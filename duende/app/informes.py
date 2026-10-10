"""Informe de cada partida (P10) y resumen de la semana (P11): la API dice qué tiene de especial cada partida (rachas,
récords del mes, estrenos...) y quién ha sido el mejor y el peor de la semana; aquí se cuenta en una o dos frases, con
la personalidad de siempre.

Siempre por reglas, sin Gemini: un comentario por partida en cada página del historial gastaría la cuota, y para una
frase basta. El Duende no inventa números: todos vienen en los hechos.
"""

import hashlib

from .metricas import NOMBRE_JUEGO, formatear
from .modelos import FilaSemana, Formato, Hecho, Idioma, ItemInforme, SemanaJuego
from .textos import CONECTORES, INFORMES, METRICAS_INFORME, ORDINALES, SEMANA

# El orden en que se cuentan: primero las rachas, luego los números, al final los estrenos y la media.
PRIORIDAD = [
    "racha_derrotas",
    "racha_victorias",
    "fin_racha_derrotas",
    "fin_racha_victorias",
    "racha_derrotas_clave",
    "racha_victorias_clave",
    "mejor_mes",
    "peor_mes",
    "estreno_clave",
    "sobre_media",
    "bajo_media",
]
RACHAS = {"racha_derrotas", "racha_victorias", "fin_racha_derrotas", "fin_racha_victorias", "racha_derrotas_clave",
          "racha_victorias_clave", "estreno_clave"}
BUENOS = {"racha_victorias", "fin_racha_derrotas", "racha_victorias_clave", "mejor_mes", "sobre_media"}
MALOS = {"racha_derrotas", "fin_racha_victorias", "racha_derrotas_clave", "peor_mes", "bajo_media"}
# Las partidas sueltas tienen kills y daño enteros.
FORMATO: dict[str, Formato] = {"kills_media": "int", "adr": "int", "hs_pct": "pct", "kd": "dec", "kda": "dec",
                               "dano": "int"}


def nombre_clave(clave: str | None) -> str:
    """"de_nuke" → "Nuke"; los dioses, tal cual."""
    if not clave:
        return ""
    limpio = clave[3:] if clave.lower().startswith("de_") else clave
    return limpio[:1].upper() + limpio[1:]


def _ordinal(n: int | None, lang: Idioma) -> str:
    if n is None:
        return ""
    return ORDINALES[lang].get(n) or (f"{n}.ª" if lang == "es" else f"{n}th")


def _variante(id_: str, opciones: list[str]) -> str:
    """Siempre la misma para la misma partida, para que el historial no cambie al recargar."""
    return opciones[int(hashlib.sha256(id_.encode()).hexdigest(), 16) % len(opciones)]


def _frase(h: Hecho, lang: Idioma, id_: str) -> str:
    formato = FORMATO.get(h.metrica or "", "dec")
    nombres = METRICAS_INFORME.get(h.metrica or "", {}).get(lang, {})
    valores = {
        "ordinal": _ordinal(h.n, lang),
        "n": h.n,
        "clave": nombre_clave(h.clave),
        "valor": formatear(h.valor, formato, lang),
        "referencia": formatear(h.referencia, formato, lang),
        "Que_mejor": _mayuscula(nombres.get("mejor", "")),
        "Que_peor": _mayuscula(nombres.get("peor", "")),
        "Metrica": _mayuscula(nombres.get("nombre", h.metrica or "")),
    }
    return _mayuscula(_variante(id_ + h.tipo, INFORMES[h.tipo][lang]).format(**valores))


def _mayuscula(texto: str) -> str:
    return texto[:1].upper() + texto[1:]


def _elegidos(hechos: list[Hecho]) -> list[Hecho]:
    """Como mucho dos: el que va primero y, si lo hay, uno del otro tipo (una racha y un número, o al revés)."""
    conocidos = sorted((h for h in hechos if h.tipo in INFORMES), key=lambda h: PRIORIDAD.index(h.tipo))
    if not conocidos:
        return []
    # Entre varios récords del mes, el que más se pasa.
    primero = conocidos[0]
    if primero.tipo in ("mejor_mes", "peor_mes"):
        primero = max((h for h in conocidos if h.tipo == primero.tipo), key=_margen)
    otros = [h for h in conocidos if (h.tipo in RACHAS) != (primero.tipo in RACHAS)]
    if otros and otros[0].tipo in ("mejor_mes", "peor_mes"):
        otros[0] = max((h for h in otros if h.tipo == otros[0].tipo), key=_margen)
    return [primero, *otros[:1]]


def _margen(h: Hecho) -> float:
    if not h.valor or not h.referencia:
        return 0.0
    return abs(h.valor - h.referencia) / abs(h.referencia)


def _conector(primero: Hecho, segundo: Hecho, lang: Idioma) -> str:
    c = CONECTORES[lang]
    if (primero.tipo in MALOS and segundo.tipo in MALOS) or (primero.tipo in BUENOS and segundo.tipo in BUENOS):
        return c["y_encima"]
    if primero.tipo in MALOS and segundo.tipo in BUENOS:
        return c["al_menos"]
    if primero.tipo in BUENOS and segundo.tipo in MALOS:
        return c["eso_si"]
    return c["ademas"]


def texto_informe(item: ItemInforme, lang: Idioma) -> str | None:
    """Una o dos frases sobre la partida, o None si no tiene nada especial."""
    elegidos = _elegidos(item.hechos)
    if not elegidos:
        return None
    texto = _frase(elegidos[0], lang, item.id)
    if len(elegidos) > 1:
        segundo = elegidos[1]
        frase = _frase(segundo, lang, item.id)
        # Tras el conector va en minúscula, salvo si empieza por un nombre (Anubis) o unas siglas (KDA, ADR).
        nombres = [nombre_clave(segundo.clave), *METRICAS_INFORME.get(segundo.metrica or "", {}).get(lang, {}).values()]
        if not any(n[:1].isupper() and frase.startswith(n) for n in nombres if n):
            frase = frase[:1].lower() + frase[1:]
        texto += " " + _conector(elegidos[0], segundo, lang) + frase
    return texto


def _fila(clave: str, f: FilaSemana, lang: Idioma) -> str:
    return SEMANA[lang][clave].format(
        nombre=f.nombre, victorias=f.victorias, partidas=f.partidas, winrate=formatear(f.winrate, "pct", lang)
    )


def texto_semana(juegos: list[SemanaJuego], lang: Idioma) -> str:
    """Un párrafo por juego: partidas del equipo, el mejor y el peor, y algo que decirles."""
    s = SEMANA[lang]
    parrafos = []
    for j in juegos:
        palabra = s["partida"] if j.partidas == 1 else s["partidas"]
        frases = [s["juego"].format(juego=NOMBRE_JUEGO[j.juego], partidas=j.partidas, palabra_partidas=palabra)]
        if not j.mejor:
            frases.append(s["nadie"])
            parrafos.append(" ".join(frases))
            continue
        # Si solo llega uno, no es "el mejor" de nadie: se dice tal cual.
        peor = j.peor or j.mejor
        if j.peor:
            frases += [_fila("mejor", j.mejor, lang), _fila("peor", j.peor, lang)]
        else:
            frases.append(_fila("solo", j.mejor, lang))
        if j.mejor.winrate is not None and j.mejor.winrate >= 70:
            frases.append(s["pleno"].format(nombre=j.mejor.nombre))
        elif peor.winrate is not None and peor.winrate < 40:
            frases.append(s["hundido"].format(nombre=peor.nombre))
        parrafos.append(" ".join(frases))
    return "\n".join(parrafos)
