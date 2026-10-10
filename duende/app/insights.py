"""Motor de recomendaciones: a partir del resumen de un jugador, decide en qué tiene que mejorar y qué hace bien.

Es determinista y no necesita Gemini: compara cada métrica con la media del resto del equipo y con una referencia
(lo normal en su nivel de FACEIT si la API lo sabe; si no, la fija de metricas.py), y añade reglas con más contexto
(kills que no dan victorias, rachas, tendencia reciente, mapas o dioses flojos, compañeros, tilt en las sesiones
largas, la mejor hora del día y si los consejos de antes han funcionado).
Si se conoce el rol del jugador, cada métrica pesa lo que pide ese rol (metricas.AJUSTES_ROL).
"""

from dataclasses import dataclass

from .metricas import METRICAS, NOMBRE_JUEGO, Metrica, factor_rol, formatear, rol_de
from .metricas import metrica as metrica_de
from .modelos import (
    Barra,
    FilaMomento,
    FilaSinergia,
    Idioma,
    Insight,
    Nivel,
    PeticionInsights,
    Resumen,
    SeguimientoConsejo,
)
from .textos import DEBILIDADES, ESPECIALES, FORTALEZAS, FRASES, NOMBRES_MOMENTO, NOMBRES_ROL, consejo_para

MUESTRA_MINIMA = 5
MAX_DEBILIDADES = 4
MAX_FORTALEZAS = 2
LONGITUD_RACHA = 5
MIN_PARTIDAS_DESGLOSE = 3
# Puntos de winrate entre jugar con un compañero y sin él para decir que con él se gana más (o menos).
DIFERENCIA_SINERGIA = 15
# Sesiones (P3): hacen falta sesiones y partidas suficientes a cada lado para no confundir casualidad con tilt.
MIN_SESIONES = 15
MUESTRA_SESION = 10
# Puntos de winrate que se pierden desde la 3ª seguida (o tras perder) para avisar de tilt; desde TILT_GRAVE, "alto".
DIFERENCIA_TILT = 15
TILT_GRAVE = 25
# La mejor hora del día se elige entre cuatro: se pide más diferencia para no premiar la casualidad.
DIFERENCIA_HORARIO = 20
# Memoria de consejos (P6): días que hay que dejar pasar antes de juzgar un consejo, y cuánto tiene que mejorar la
# métrica (como en el resto de reglas: 5 puntos en porcentajes, un 10 % en lo demás) para decir que ha funcionado.
DIAS_SEGUIMIENTO = 7
MEJORA_CONSEJO = 0.1

ORDEN_NIVEL: dict[Nivel, int] = {"info": 0, "alto": 1, "medio": 2, "bien": 3}


@dataclass(frozen=True)
class Referencia:
    """Con qué se compara una métrica además de con el equipo: lo normal en su nivel de FACEIT (P8) o, si no se sabe,
    la referencia fija de metricas.py (entonces `nivel` es None)."""

    valor: float
    nivel: int | None = None
    # % de las partidas de su nivel en las que se hace peor que él (ya girado en las que es mejor bajo).
    mejor_que: float | None = None


def referencia_de(p: PeticionInsights, metrica: Metrica) -> Referencia | None:
    """Lo normal en su nivel si la API lo manda (solo llega con muestra suficiente); si no, la fija, si la hay."""
    if p.nivel:
        m = next((x for x in p.nivel.metricas if x.metrica == metrica.clave), None)
        if m:
            mejor_que = None
            if m.percentil is not None:
                mejor_que = m.percentil if metrica.mejor == "alto" else 100 - m.percentil
            return Referencia(m.referencia, p.nivel.nivel, mejor_que)
    return Referencia(metrica.referencia) if metrica.referencia is not None else None


@dataclass
class Candidato:
    insight: Insight
    # Lo lejos que está de lo normal (en valor absoluto). Ordena dentro de cada nivel.
    peso: float


def _desviacion(metrica: Metrica, tu: float, otro: float | None) -> float | None:
    """Cuánto mejor (+) o peor (-) es `tu` que `otro`. En porcentajes, puntos / 50; en el resto, relativo."""
    if otro is None:
        return None
    if metrica.formato == "pct":
        d = (tu - otro) / 50
    elif otro == 0:
        return None
    else:
        d = (tu - otro) / abs(otro)
    return -d if metrica.mejor == "bajo" else d


def _nivel_debilidad(d: float) -> Nivel | None:
    if d <= -0.2:
        return "alto"
    if d <= -0.1:
        return "medio"
    return None


def _barras(metrica: Metrica, tu: float, equipo: float | None, ref: Referencia | None, lang: Idioma) -> list[Barra]:
    f = FRASES[lang]
    barras = [Barra(etiqueta=f["tu"], valor=round(tu, 2), tuyo=True)]
    if equipo is not None:
        barras.append(Barra(etiqueta=f["equipo"], valor=round(equipo, 2)))
    if ref is not None:
        etiqueta = f["referencia"] if ref.nivel is None else f["nivel"].format(nivel=ref.nivel)
        barras.append(Barra(etiqueta=etiqueta, valor=round(ref.valor, 2)))
    return barras


def _pct_entero(valor: float, lang: Idioma) -> str:
    return f"{round(valor)} %" if lang == "es" else f"{round(valor)}%"


def _comparativa(metrica: Metrica, tu: float, equipo: float | None, ref: Referencia | None, lang: Idioma) -> str:
    f = FRASES[lang]
    v = {
        "tu": formatear(tu, metrica.formato, lang),
        "equipo": formatear(equipo, metrica.formato, lang),
        "ref": formatear(ref.valor if ref else None, metrica.formato, lang),
        "nivel": ref.nivel if ref else None,
    }
    if ref is not None and ref.nivel is not None:
        texto = f["cmp_equipo_nivel" if equipo is not None else "cmp_nivel"].format(**v)
        if ref.mejor_que is not None:
            texto += " " + f["mejor_que"].format(pct=_pct_entero(ref.mejor_que, lang))
        return texto
    if equipo is not None and ref is not None:
        return f["cmp_equipo_ref"].format(**v)
    if equipo is not None:
        return f["cmp_equipo"].format(**v)
    if ref is not None:
        return f["cmp_ref"].format(**v)
    return f["cmp_solo"].format(**v)


def _nota_rol(rol: str | None, factor: float, debilidad: bool, lang: Idioma) -> str:
    """Coletilla que explica que el rol ha contado: es lo suyo, o se le ha perdonado algo y aun así no llega."""
    if rol is None or factor == 1:
        return ""
    if factor > 1:
        clave = "rol_pesa_debil" if debilidad else "rol_pesa_fuerte"
    elif debilidad:
        clave = "rol_tolera"
    else:
        return ""
    return " " + FRASES[lang][clave].format(rol=NOMBRES_ROL[rol][lang])


def _por_metrica(p: PeticionInsights, ocupadas: set[str]) -> list[Candidato]:
    """Debilidades y fortalezas métrica a métrica, frente al equipo y la referencia (la de su nivel, si se sabe),
    según lo que pide su rol."""
    lang, juego = p.lang, p.juego
    rol = rol_de(juego, p.rol)
    candidatos: list[Candidato] = []
    for metrica in METRICAS[juego]:
        if metrica.clave in ocupadas:
            continue
        tu = metrica.valor(p.resumen)
        if tu is None:
            continue
        equipo = metrica.valor(p.equipo)
        ref = referencia_de(p, metrica)
        d_equipo = _desviacion(metrica, tu, equipo)
        d_ref = _desviacion(metrica, tu, ref.valor if ref else None)
        disponibles = [d for d in (d_equipo, d_ref) if d is not None]
        if not disponibles:
            continue

        # Con el rol, lo que no le toca no cuenta (o cuenta menos) y lo suyo, más.
        factor = factor_rol(juego, rol, metrica.clave)
        peor = min(disponibles) * factor
        nivel = _nivel_debilidad(peor)
        if nivel == "alto" and ref and ref.nivel and d_ref is not None and not _nivel_debilidad(d_ref * factor):
            # Frente a su nivel no habría aviso (va en lo normal de su nivel): lo que le separe del equipo es para
            # vigilar, no para mejorar ya.
            nivel = "medio"
        if nivel:
            textos = DEBILIDADES.get(metrica.clave, {}).get(lang)
            if textos:
                titulo = str(textos["titulo"])
                frase = f"{textos['frase']} "
            else:
                titulo = metrica.nombre(lang)
                frase = ""
            candidatos.append(
                Candidato(
                    Insight(
                        id=f"debil_{metrica.clave}",
                        nivel=nivel,
                        metrica=metrica.clave,
                        titulo=titulo,
                        texto=frase
                        + _comparativa(metrica, tu, equipo, ref, lang)
                        + _nota_rol(rol, factor, True, lang),
                        consejo=consejo_para(textos, juego) if textos else None,
                        barras=_barras(metrica, tu, equipo, ref, lang),
                        formato=metrica.formato,
                    ),
                    abs(peor),
                )
            )
            continue

        # Fortaleza: claramente por encima del equipo y sin estar por debajo de la referencia.
        # Sin equipo con quien comparar, vale con superar bien la referencia. Si es lo suyo, se reconoce antes.
        refuerzo = max(factor, 1.0)
        fuerte = (d_equipo is not None and d_equipo * refuerzo >= 0.1 and (d_ref is None or d_ref >= 0)) or (
            d_equipo is None and d_ref is not None and d_ref * refuerzo >= 0.15
        )
        textos_f = FORTALEZAS.get(metrica.clave, {}).get(lang)
        if fuerte and textos_f:
            candidatos.append(
                Candidato(
                    Insight(
                        id=f"fuerte_{metrica.clave}",
                        nivel="bien",
                        metrica=metrica.clave,
                        titulo=textos_f["titulo"],
                        texto=_comparativa(metrica, tu, equipo, ref, lang) + _nota_rol(rol, factor, False, lang),
                        consejo=textos_f["consejo"],
                        barras=_barras(metrica, tu, equipo, ref, lang),
                        formato=metrica.formato,
                    ),
                    max(disponibles) * refuerzo,
                )
            )
    return candidatos


def _especial(regla: str, lang: Idioma, juego: str, nivel: Nivel, /, **valores: object) -> Insight:
    """Insight de una regla especial. Los parámetros van por posición: `juego` o `clave` pueden ser valores del texto."""
    textos = ESPECIALES[regla][lang]
    consejo = consejo_para(textos, juego)  # type: ignore[arg-type]
    return Insight(
        id=regla,
        nivel=nivel,
        titulo=str(textos["titulo"]).format(**valores),
        texto=str(textos["frase"]).format(**valores),
        consejo=consejo.format(**valores) if consejo else None,
    )


def _kd_sin_victorias(p: PeticionInsights) -> Candidato | None:
    r = p.resumen
    if r.kd is None or r.winrate is None:
        return None
    kd_equipo = p.equipo.kd if p.equipo else None
    umbral_kd = max(1.05, kd_equipo or 0)
    if r.kd < umbral_kd or r.winrate > 45:
        return None
    lang = p.lang
    insight = _especial(
        "kd_sin_victorias",
        lang,
        p.juego,
        "alto",
        kd=formatear(r.kd, "dec", lang),
        winrate=formatear(r.winrate, "pct", lang),
    )
    insight.metrica = "winrate"
    insight.formato = "pct"
    metrica_wr = next(m for m in METRICAS[p.juego] if m.clave == "winrate")
    insight.barras = _barras(
        metrica_wr, r.winrate, p.equipo.winrate if p.equipo else None, referencia_de(p, metrica_wr), lang
    )
    # Es la recomendación con más miga: va la primera entre las graves.
    return Candidato(insight, (50 - r.winrate) / 50 + 1)


def _rachas(p: PeticionInsights) -> Candidato | None:
    ultimas = p.resumen.forma[:LONGITUD_RACHA]
    if len(ultimas) < LONGITUD_RACHA:
        return None
    derrotas, victorias = ultimas.count("D"), ultimas.count("V")
    n = len(ultimas)
    if derrotas >= 4:
        return Candidato(_especial("racha_mala", p.lang, p.juego, "alto", derrotas=derrotas, n=n), derrotas / n)
    if victorias >= 4:
        return Candidato(_especial("racha_buena", p.lang, p.juego, "bien", victorias=victorias, n=n), victorias / n)
    return None


def _tendencia(p: PeticionInsights) -> Candidato | None:
    reciente: Resumen | None = p.reciente
    if not reciente or reciente.partidas < MUESTRA_MINIMA or p.resumen.partidas <= reciente.partidas:
        return None
    if reciente.kd is None or p.resumen.kd is None or p.resumen.kd == 0:
        return None
    cambio = (reciente.kd - p.resumen.kd) / p.resumen.kd
    if abs(cambio) < 0.15:
        return None
    lang = p.lang
    clave, nivel = ("tendencia_sube", "bien") if cambio > 0 else ("tendencia_baja", "medio")
    insight = _especial(
        clave,
        lang,
        p.juego,
        nivel,
        n=reciente.partidas,
        reciente=formatear(reciente.kd, "dec", lang),
        **{"global": formatear(p.resumen.kd, "dec", lang)},
    )
    f = FRASES[lang]
    insight.metrica = "kd"
    insight.formato = "dec"
    insight.barras = [
        Barra(etiqueta=f["ultimas"].format(n=reciente.partidas), valor=reciente.kd, tuyo=True),
        Barra(etiqueta=f["global"], valor=p.resumen.kd),
    ]
    return Candidato(insight, abs(cambio))


def _desglose(p: PeticionInsights) -> list[Candidato]:
    validos = [d for d in p.desglose if d.partidas >= MIN_PARTIDAS_DESGLOSE and d.winrate is not None]
    if not validos:
        return []
    lang = p.lang
    candidatos: list[Candidato] = []
    peor = min(validos, key=lambda d: (d.winrate, -d.partidas))
    if peor.winrate <= 35:
        insight = _especial(
            "desglose_malo",
            lang,
            p.juego,
            "medio",
            clave=peor.clave,
            victorias=peor.victorias,
            partidas=peor.partidas,
            winrate=formatear(peor.winrate, "pct", lang),
        )
        candidatos.append(Candidato(insight, (50 - peor.winrate) / 50))
    mejor = max(validos, key=lambda d: (d.winrate, d.partidas))
    if mejor.winrate >= 65 and mejor is not peor:
        insight = _especial(
            "desglose_bueno",
            lang,
            p.juego,
            "bien",
            clave=mejor.clave,
            victorias=mejor.victorias,
            partidas=mejor.partidas,
            winrate=formatear(mejor.winrate, "pct", lang),
        )
        candidatos.append(Candidato(insight, (mejor.winrate - 50) / 50))
    return candidatos


def _companero(regla: str, nivel: Nivel, c: FilaSinergia, p: PeticionInsights) -> Candidato:
    """Insight de un compañero con el que se gana más o menos, con las barras de con él y sin él."""
    lang = p.lang
    assert c.winrate is not None and c.winrate_sin is not None and c.nombre
    insight = _especial(
        regla,
        lang,
        p.juego,
        nivel,
        nombre=c.nombre,
        partidas=c.partidas,
        con=formatear(c.winrate, "pct", lang),
        sin=formatear(c.winrate_sin, "pct", lang),
    )
    f = FRASES[lang]
    insight.formato = "pct"
    insight.barras = [
        Barra(etiqueta=f["con"].format(nombre=c.nombre), valor=c.winrate, tuyo=True),
        Barra(etiqueta=f["sin"].format(nombre=c.nombre), valor=c.winrate_sin),
    ]
    return Candidato(insight, abs(c.winrate - c.winrate_sin) / 50)


def _sinergias(p: PeticionInsights) -> list[Candidato]:
    """El compañero con el que más gana y con el que menos, si la diferencia entre jugar con él y sin él es clara.

    Se compara con las partidas sin él y no con el winrate global: en un grupo pequeño casi todo se juega con los
    mismos, así que "con él" y "global" son casi lo mismo y la diferencia no se vería nunca.
    """
    if not p.sinergias:
        return []
    validos = [
        c
        for c in p.sinergias.companeros
        if c.nombre
        and c.winrate is not None
        and c.winrate_sin is not None
        and c.partidas >= MUESTRA_MINIMA
        and c.partidas_sin >= MUESTRA_MINIMA
    ]
    if not validos:
        return []
    candidatos: list[Candidato] = []
    mejor = max(validos, key=lambda c: (c.winrate - c.winrate_sin, c.partidas))
    if mejor.winrate - mejor.winrate_sin >= DIFERENCIA_SINERGIA:
        candidatos.append(_companero("companero_bueno", "bien", mejor, p))
    peor = min(validos, key=lambda c: (c.winrate - c.winrate_sin, -c.partidas))
    if peor.winrate_sin - peor.winrate >= DIFERENCIA_SINERGIA:
        candidatos.append(_companero("companero_malo", "medio", peor, p))
    return candidatos


def companeros_destacados(p: PeticionInsights) -> list[Insight]:
    """Las recomendaciones de compañeros sin el tope de fortalezas y debilidades, para cuando se pregunta por ellos."""
    return [c.insight for c in _sinergias(p)]


def _fila(filas: list[FilaMomento], clave: str) -> FilaMomento | None:
    return next((f for f in filas if f.clave == clave), None)


def _contraste(f: FilaMomento | None) -> float | None:
    """Puntos de winrate de esta fila frente al resto de partidas, si hay partidas suficientes a los dos lados."""
    if f is None or f.winrate is None or f.winrate_resto is None:
        return None
    if f.partidas < MUESTRA_SESION or f.partidas_resto < MUESTRA_SESION:
        return None
    return f.winrate - f.winrate_resto


def _momento(
    regla: str, nivel: Nivel, f: FilaMomento, p: PeticionInsights, barras: tuple[str, str], **extra: str
) -> Candidato:
    """Insight de una fila de las sesiones, con sus barras: esa fila (resaltada) y el resto de partidas."""
    lang = p.lang
    assert f.winrate is not None and f.winrate_resto is not None
    insight = _especial(
        regla,
        lang,
        p.juego,
        nivel,
        partidas=f.partidas,
        winrate=formatear(f.winrate, "pct", lang),
        resto=formatear(f.winrate_resto, "pct", lang),
        **extra,
    )
    insight.formato = "pct"
    fila, resto = barras
    insight.barras = [Barra(etiqueta=fila, valor=f.winrate, tuyo=True), Barra(etiqueta=resto, valor=f.winrate_resto)]
    return Candidato(insight, abs(f.winrate - f.winrate_resto) / 50)


def _sesiones(p: PeticionInsights) -> list[Candidato]:
    """Tilt (gana mucho menos desde la 3ª partida seguida o tras perder) y su mejor hora del día.

    Solo con MIN_SESIONES sesiones o más: con menos, cualquier diferencia puede ser casualidad. Si salta el tilt por
    sesiones largas, el de después de perder sobra (suelen ser la misma cosa: las derrotas se amontonan al final).
    """
    s = p.sesiones
    if not s or s.sesiones < MIN_SESIONES:
        return []
    lang = p.lang
    f = FRASES[lang]
    candidatos: list[Candidato] = []

    tercera = _fila(s.por_orden, "3+")
    caida = _contraste(tercera)
    derrota = _fila(s.tras_resultado, "derrota")
    caida_derrota = _contraste(derrota)
    if tercera and caida is not None and -caida >= DIFERENCIA_TILT:
        nivel: Nivel = "alto" if -caida >= TILT_GRAVE else "medio"
        candidatos.append(_momento("tilt_sesion", nivel, tercera, p, (NOMBRES_MOMENTO["3+"][lang], f["primeras"])))
    elif derrota and caida_derrota is not None and -caida_derrota >= DIFERENCIA_TILT:
        nivel = "alto" if -caida_derrota >= TILT_GRAVE else "medio"
        candidatos.append(_momento("tilt_derrota", nivel, derrota, p, (NOMBRES_MOMENTO["derrota"][lang], f["resto"])))

    franjas = [(fr, d) for fr in s.por_franja if (d := _contraste(fr)) is not None]
    if franjas:
        mejor, ventaja = max(franjas, key=lambda x: (x[1], x[0].partidas))
        if ventaja >= DIFERENCIA_HORARIO:
            nombre = NOMBRES_MOMENTO[mejor.clave][lang]
            candidatos.append(
                _momento(
                    "mejor_horario",
                    "bien",
                    mejor,
                    p,
                    (nombre, f["resto_dia"]),
                    franja=nombre[0].lower() + nombre[1:],
                    Franja=nombre,
                )
            )
    return candidatos


def sesiones_destacadas(p: PeticionInsights) -> list[Insight]:
    """Tilt y mejor hora sin el tope del panel, para cuando se pregunta por ellos."""
    return [c.insight for c in _sesiones(p)]


# ─── Memoria de consejos (P6) ────────────────────────────────────────────────


def en_frase(nombre: str) -> str:
    """El nombre de una métrica dentro de una frase: "winrate", "muertes / partida"; las siglas, igual ("ADR", "K/D")."""
    if not nombre[:1].isalpha() or nombre[:2].isupper():
        return nombre
    return nombre[0].lower() + nombre[1:]


def aviso_de(s: SeguimientoConsejo, m: Metrica, lang: Idioma) -> str:
    """El título del aviso que se le dio ("Poco daño por ronda"), o el nombre de la métrica si no se puede rehacer."""
    if s.insight.startswith("debil_") and (textos := DEBILIDADES.get(s.metrica, {}).get(lang)):
        return str(textos["titulo"])
    titulo = str(ESPECIALES.get(s.insight, {}).get(lang, {}).get("titulo", ""))
    return titulo if titulo and "{" not in titulo else m.nombre(lang)


def efecto(s: SeguimientoConsejo, juego: str) -> float | None:
    """Cuánto ha mejorado (+) o empeorado (-) la métrica desde el consejo, como en el resto de reglas. None si aún es
    pronto: menos de DIAS_SEGUIMIENTO días o menos de MUESTRA_MINIMA partidas desde entonces."""
    m = metrica_de(juego, s.metrica)
    if not m or s.valor_desde is None or s.dias < DIAS_SEGUIMIENTO or s.partidas_desde < MUESTRA_MINIMA:
        return None
    return _desviacion(m, s.valor_desde, s.valor)


def _consejo_seguido(regla: str, nivel: Nivel, s: SeguimientoConsejo, d: float, p: PeticionInsights) -> Candidato:
    lang = p.lang
    m = metrica_de(p.juego, s.metrica)
    assert m is not None and s.valor_desde is not None
    antes, ahora = formatear(s.valor, m.formato, lang), formatear(s.valor_desde, m.formato, lang)
    insight = _especial(
        regla,
        lang,
        p.juego,
        nivel,
        metrica=m.nombre(lang),
        metrica_frase=en_frase(m.nombre(lang)),
        aviso=aviso_de(s, m, lang),
        dias=s.dias,
        partidas=s.partidas_desde,
        antes=antes,
        ahora=ahora,
    )
    if regla == "consejo_no_funciona" and s.insight.startswith("debil_"):
        # El consejo de entonces, otra vez: aún no ha servido.
        if original := consejo_para(DEBILIDADES.get(s.metrica, {}).get(lang, {}), p.juego):
            insight.consejo = FRASES[lang]["insiste"].format(consejo=original)
    f = FRASES[lang]
    insight.metrica = s.metrica
    insight.formato = m.formato
    insight.barras = [
        Barra(etiqueta=f["desde_entonces"], valor=round(s.valor_desde, 2), tuyo=True),
        Barra(etiqueta=f["entonces"], valor=round(s.valor, 2)),
    ]
    # La memoria del Duende va por delante: es lo más personal que puede decir.
    return Candidato(insight, 1 + abs(d))


def _seguimiento(p: PeticionInsights) -> list[Candidato]:
    """Si los consejos de antes han funcionado: el que más ha mejorado y el que no ha mejorado nada (o ha empeorado)."""
    con_efecto = [(s, d) for s in p.seguimiento if (d := efecto(s, p.juego)) is not None]
    if not con_efecto:
        return []
    candidatos: list[Candidato] = []
    mejor, d_mejor = max(con_efecto, key=lambda x: x[1])
    if d_mejor >= MEJORA_CONSEJO:
        candidatos.append(_consejo_seguido("consejo_funciona", "bien", mejor, d_mejor, p))
    peor, d_peor = min(con_efecto, key=lambda x: x[1])
    if d_peor <= 0:
        nivel: Nivel = "alto" if d_peor <= -MEJORA_CONSEJO else "medio"
        candidatos.append(_consejo_seguido("consejo_no_funciona", nivel, peor, d_peor, p))
    return candidatos


def seguimiento_destacado(p: PeticionInsights) -> list[Insight]:
    """Si han funcionado los consejos, sin el tope del panel, para cuando se pregunta por ello."""
    return [c.insight for c in _seguimiento(p)]


def generar_insights(p: PeticionInsights) -> list[Insight]:
    """Recomendaciones ordenadas: aviso de muestra, debilidades graves, medias y, al final, lo que hace bien."""
    if p.resumen.partidas == 0:
        return []

    muestra: list[Insight] = []
    if p.resumen.partidas < MUESTRA_MINIMA:
        muestra.append(
            _especial(
                "muestra", p.lang, p.juego, "info", partidas=p.resumen.partidas, juego=NOMBRE_JUEGO[p.juego]
            )
        )

    especiales = (
        [c for c in (_kd_sin_victorias(p), _rachas(p), _tendencia(p)) if c]
        + _desglose(p)
        + _sinergias(p)
        + _sesiones(p)
        + _seguimiento(p)
    )
    # Si una regla especial ya habla de una métrica (el winrate, o un consejo que no ha funcionado), la genérica sobra.
    ocupadas = {c.insight.metrica for c in especiales if c.insight.id in ("kd_sin_victorias", "consejo_no_funciona")}
    candidatos = especiales + _por_metrica(p, {m for m in ocupadas if m})

    debiles = sorted(
        (c for c in candidatos if c.insight.nivel in ("alto", "medio")),
        key=lambda c: (ORDEN_NIVEL[c.insight.nivel], -c.peso),
    )[:MAX_DEBILIDADES]
    fuertes = sorted((c for c in candidatos if c.insight.nivel == "bien"), key=lambda c: -c.peso)[:MAX_FORTALEZAS]

    if muestra:
        # Con pocas partidas no se exagera: el aviso y, como mucho, lo más claro de cada lado.
        return muestra + [c.insight for c in debiles[:1] + fuertes[:1]]
    return [c.insight for c in debiles + fuertes]
