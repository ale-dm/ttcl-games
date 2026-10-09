"""Respuestas del chat sin Gemini: detecta qué se pregunta y contesta con los datos y las recomendaciones del motor.

Es el respaldo cuando no hay GOOGLE_API_KEY, se agota la cuota o Gemini falla. Así el Duende siempre responde.
"""

import re
import unicodedata
from dataclasses import dataclass
from typing import Literal

from .insights import companeros_destacados, generar_insights
from .metricas import METRICAS, NOMBRE_JUEGO, formatear, metrica, rol_de
from .modelos import (
    Idioma,
    Insight,
    Juego,
    JuegoContexto,
    JugadorContexto,
    JugadorRef,
    PeticionChat,
    PeticionInsights,
)
from .textos import NOMBRES_ROL

Intencion = Literal[
    "hola", "companeros", "ranking", "comparar", "racha", "desglose", "fuerte", "mejorar", "stats", "ayuda"
]


def normalizar(texto: str) -> str:
    sin_tildes = unicodedata.normalize("NFKD", texto.lower())
    return "".join(c for c in sin_tildes if not unicodedata.combining(c))


def _contiene(texto: str, palabras: list[str]) -> bool:
    return any(re.search(rf"(?<![a-z0-9]){re.escape(p)}", texto) for p in palabras)


# Palabras que apuntan a cada métrica (ya normalizadas: minúsculas y sin tildes).
PALABRAS_METRICA: dict[str, list[str]] = {
    "winrate": ["winrate", "win rate", "victorias", "ganadas", "wins"],
    "kd": ["kd", "k/d"],
    "kda": ["kda"],
    "adr": ["adr"],
    "hs_pct": ["hs", "headshot", "cabeza", "head"],
    "entry_pct": ["entry", "entrada", "abrir"],
    "clutch_pct": ["clutch"],
    "dano_min": ["dano", "damage", "dps"],
    "oro_min": ["oro", "gold", "farm"],
    "mitigado": ["mitig", "tanque", "tank"],
    "kills_media": ["kills", "bajas", "asesinatos"],
    "muertes_media": ["muertes", "mueres", "deaths", "die"],
    "asistencias_media": ["asistencias", "assists"],
}


def detectar_intencion(pregunta: str, num_foco: int) -> Intencion:
    t = normalizar(pregunta)
    palabras = re.findall(r"[a-z0-9/]+", t)
    if len(palabras) <= 3 and _contiene(t, ["hola", "buenas", "hey", "hello", "hi", "ey"]):
        return "hola"
    # Antes que "quién" (ranking y comparar): "¿con quién juego mejor?" va de compañeros.
    if _contiene(t, ["con quien", "companer", "duo", "trio", "sinergi", "synerg", "teammate", "partner"]) or re.search(
        r"\bwho\b.*\bwith\b", t
    ):
        return "companeros"
    if _contiene(t, ["compar", "vs", "versus", "contra", "frente a", "head to head"]) or (
        num_foco == 2 and _contiene(t, ["quien", "who"])
    ):
        return "comparar"
    if _contiene(t, ["quien", "who", "ranking", "clasificacion", "del equipo", "of the team", "best", "worst", "top"]):
        return "ranking"
    if _contiene(t, ["racha", "forma", "ultim", "streak", "form", "recent", "lately", "tilt"]):
        return "racha"
    if _contiene(t, ["mapa", "map", "dios", "god", "personaje", "pick"]):
        return "desglose"
    if _contiene(t, ["bien", "fuerte", "destaco", "strength", "strong", "good at", "lo mejor"]):
        return "fuerte"
    if _contiene(
        t,
        ["mejor", "consejo", "tip", "improve", "better", "work on", "debil", "weak", "fallo", "practic", "entren",
         "train", "recomend", "fix", "ayuda", "help"],
    ):
        return "mejorar"
    if any(_contiene(t, ps) for ps in PALABRAS_METRICA.values()):
        return "stats"
    return "ayuda"


def detectar_juego(pregunta: str, por_defecto: Juego | None) -> Juego | None:
    t = normalizar(pregunta)
    if _contiene(t, ["cs2", "cs", "counter", "csgo"]):
        return "cs2"
    if _contiene(t, ["smite"]):
        return "smite2"
    return por_defecto


def buscar_jugador(pregunta: str, equipo: list[JugadorContexto]) -> JugadorContexto | None:
    """Un jugador del equipo mencionado por nombre o slug en la pregunta."""
    t = normalizar(pregunta)
    for j in sorted(equipo, key=lambda j: -len(j.nombre)):
        if re.search(rf"(?<![a-z0-9]){re.escape(normalizar(j.nombre))}(?![a-z0-9])", t) or re.search(
            rf"(?<![a-z0-9]){re.escape(normalizar(j.slug))}(?![a-z0-9])", t
        ):
            return j
    return None


# ─── Utilidades de contexto ──────────────────────────────────────────────────


@dataclass
class Foco:
    jugador: JugadorContexto
    juego: JuegoContexto


def _juego_de(j: JugadorContexto, juego: Juego | None) -> JuegoContexto | None:
    if juego:
        return next((g for g in j.juegos if g.juego == juego), None)
    con_partidas = [g for g in j.juegos if g.resumen.partidas > 0]
    return max(con_partidas, key=lambda g: g.resumen.partidas, default=None)


def _peticion(j: JugadorContexto, g: JuegoContexto, lang: Idioma) -> PeticionInsights:
    return PeticionInsights(
        lang=lang,
        jugador=JugadorRef(slug=j.slug, nombre=j.nombre),
        juego=g.juego,
        rol=g.rol,
        resumen=g.resumen,
        reciente=g.reciente,
        equipo=g.equipo,
        desglose=g.desglose,
        sinergias=g.sinergias,
    )


def insights_de(j: JugadorContexto, g: JuegoContexto, lang: Idioma) -> list[Insight]:
    return generar_insights(_peticion(j, g, lang))


def _t(lang: Idioma, es: str, en: str) -> str:
    return es if lang == "es" else en


def _fmt(clave: str, juego: Juego, valor: float | None, lang: Idioma) -> str:
    m = metrica(juego, clave)
    return formatear(valor, m.formato if m else "dec", lang)


# ─── Respuestas por intención ────────────────────────────────────────────────


def _mejorar(f: Foco, lang: Idioma) -> str:
    nombre, juego = f.jugador.nombre, NOMBRE_JUEGO[f.juego.juego]
    if rol := rol_de(f.juego.juego, f.juego.rol):
        nombre_rol = NOMBRES_ROL[rol][lang]
        juego += _t(lang, f" (rol de {nombre_rol})", f" ({nombre_rol} role)")
    ins = insights_de(f.jugador, f.juego, lang)
    debiles = [i for i in ins if i.nivel in ("alto", "medio")][:3]
    aviso = next((i for i in ins if i.nivel == "info"), None)
    lineas: list[str] = []
    if aviso:
        lineas.append(f"{aviso.texto} {aviso.consejo or ''}".strip())
    if not debiles:
        fuertes = [i.titulo for i in ins if i.nivel == "bien"]
        lineas.append(
            _t(
                lang,
                f"En {juego} poco que rascar, {nombre}: nada está claramente por debajo del equipo ni de la referencia.",
                f"Not much to fix in {juego}, {nombre}: nothing is clearly below the team or the benchmark.",
            )
        )
        if fuertes:
            lineas.append(_t(lang, "Lo tuyo: ", "Your strengths: ") + ", ".join(fuertes) + ".")
        return "\n\n".join(lineas)
    lineas.append(_t(lang, f"Esto es lo que yo trabajaría en {juego}, {nombre}:", f"Here's what I'd work on in {juego}, {nombre}:"))
    lineas.append("\n".join(f"- **{i.titulo}.** {i.consejo or i.texto}" for i in debiles))
    return "\n\n".join(lineas)


def _fuerte(f: Foco, lang: Idioma) -> str:
    juego = NOMBRE_JUEGO[f.juego.juego]
    fuertes = [i for i in insights_de(f.jugador, f.juego, lang) if i.nivel == "bien"]
    if not fuertes:
        return _t(
            lang,
            f"En {juego}, de momento nada destaca por encima del equipo. Tranquilo, que para eso estoy.",
            f"In {juego}, nothing stands out above the team yet. Relax, that's what I'm here for.",
        )
    cabecera = _t(lang, f"Lo que haces bien en {juego}:", f"What you do well in {juego}:")
    return cabecera + "\n\n" + "\n".join(f"- **{i.titulo}.** {i.texto}" for i in fuertes)


def _racha(f: Foco, lang: Idioma) -> str:
    r = f.juego.resumen
    if not r.forma:
        return _t(lang, "No hay partidas recientes con resultado.", "There are no recent matches with a result.")
    forma = r.forma if lang == "es" else r.forma.replace("V", "W").replace("D", "L")
    v, d = r.forma.count("V"), r.forma.count("D")
    texto = _t(
        lang,
        f"Últimas {len(r.forma)} en {NOMBRE_JUEGO[r.juego]}: {' '.join(forma)} → {v} victorias y {d} derrotas.",
        f"Last {len(r.forma)} in {NOMBRE_JUEGO[r.juego]}: {' '.join(forma)} → {v} wins and {d} losses.",
    )
    extra = [
        i for i in insights_de(f.jugador, f.juego, lang) if i.id in ("racha_mala", "racha_buena", "tendencia_sube", "tendencia_baja")
    ]
    for i in extra:
        texto += f"\n\n**{i.titulo}.** {i.texto} {i.consejo or ''}".rstrip()
    return texto


def _desglose(f: Foco, lang: Idioma) -> str:
    g = f.juego
    que = _t(lang, "mapa", "map") if g.juego == "cs2" else _t(lang, "dios", "god")
    validos = [d for d in g.desglose if d.partidas >= 2 and d.winrate is not None]
    if not validos:
        return _t(lang, f"Aún no hay partidas suficientes por {que}.", f"Not enough matches per {que} yet.")
    mejor = max(validos, key=lambda d: (d.winrate, d.partidas))
    peor = min(validos, key=lambda d: (d.winrate, -d.partidas))

    def linea(d) -> str:
        return f"**{d.clave}** ({formatear(d.winrate, 'pct', lang)}, {d.victorias}/{d.partidas})"

    if mejor is peor:
        return _t(lang, f"Solo hay datos suficientes de {linea(mejor)}.", f"Only {linea(mejor)} has enough data.")
    return _t(
        lang,
        f"Donde mejor te va: {linea(mejor)}. Donde peor: {linea(peor)}.",
        f"Your best {que}: {linea(mejor)}. Your worst: {linea(peor)}.",
    ) + "\n\n" + _t(
        lang,
        "Pide el bueno siempre que puedas y entrena (o evita) el malo.",
        "Ask for the good one whenever you can and practise (or avoid) the bad one.",
    )


def _companeros(f: Foco, lang: Idioma) -> str:
    """Con quién del equipo le va mejor, el mejor winrate primero, y cómo le va solo."""
    s = f.juego.sinergias
    juego = NOMBRE_JUEGO[f.juego.juego]
    filas = sorted((c for c in (s.companeros if s else []) if c.winrate is not None), key=lambda c: -c.winrate)
    solo = s.solo if s and s.solo and s.solo.winrate is not None else None
    if not filas and not solo:
        return _t(
            lang,
            f"Aún no hay partidas suficientes de {juego} con nadie del equipo para saberlo.",
            f"Not enough {juego} matches with anyone on the team to tell yet.",
        )
    lineas = []
    for c in filas:
        con = formatear(c.winrate, "pct", lang)
        sin = formatear(c.winrate_sin, "pct", lang)
        lineas.append(
            _t(
                lang,
                f"- **{c.nombre}**: ganas el {con} ({c.victorias} de {c.partidas}); sin {c.nombre}, el {sin}.",
                f"- **{c.nombre}**: you win {con} ({c.victorias} of {c.partidas}); without {c.nombre}, {sin}.",
            )
        )
    if solo:
        pct = formatear(solo.winrate, "pct", lang)
        lineas.append(
            _t(
                lang,
                f"- **Solo**: ganas el {pct} ({solo.victorias} de {solo.partidas}).",
                f"- **Solo**: you win {pct} ({solo.victorias} of {solo.partidas}).",
            )
        )
    texto = _t(
        lang, f"Con quién te va mejor en {juego}, {f.jugador.nombre}:", f"Who you do best with in {juego}, {f.jugador.nombre}:"
    )
    texto += "\n\n" + "\n".join(lineas)
    # Sin el tope del panel: aquí se pregunta justo por esto.
    for i in companeros_destacados(_peticion(f.jugador, f.juego, lang)):
        texto += f"\n\n**{i.titulo}.** {i.consejo or ''}".rstrip()
    return texto


def _mejores_duos(equipo: list[JugadorContexto], juego: Juego, lang: Idioma) -> str:
    """Los dúos del equipo con mejor winrate juntos, a partir de las sinergias de cada uno."""
    duos: dict[tuple[str, str], tuple[str, float, int, int]] = {}
    for j in equipo:
        g = _juego_de(j, juego)
        for c in g.sinergias.companeros if g and g.sinergias else []:
            if c.slug and c.nombre and c.winrate is not None:
                # Desde los dos lados salen los mismos números: basta con uno.
                clave = tuple(sorted((j.slug, c.slug)))
                duos.setdefault(clave, (f"{j.nombre} + {c.nombre}", c.winrate, c.victorias, c.partidas))
    if not duos:
        return _t(
            lang,
            f"Aún no hay ningún dúo con partidas suficientes juntos en {NOMBRE_JUEGO[juego]}.",
            f"No duo has played enough {NOMBRE_JUEGO[juego]} matches together yet.",
        )
    orden = sorted(duos.values(), key=lambda d: (-d[1], -d[3]))[:3]
    lineas = [
        f"- **{n}**: {formatear(wr, 'pct', lang)} ({v} {_t(lang, 'de', 'of')} {p})" for n, wr, v, p in orden
    ]
    return (
        _t(lang, f"Los mejores dúos en {NOMBRE_JUEGO[juego]}:", f"Best duos in {NOMBRE_JUEGO[juego]}:")
        + "\n\n"
        + "\n".join(lineas)
    )


def _stats(f: Foco, pregunta: str, lang: Idioma) -> str:
    t = normalizar(pregunta)
    juego = f.juego.juego
    claves = [c for c, ps in PALABRAS_METRICA.items() if _contiene(t, ps) and metrica(juego, c)]
    if not claves:
        claves = ["winrate", "kd"]
    lineas = []
    for clave in claves:
        m = metrica(juego, clave)
        assert m is not None
        tu = m.valor(f.juego.resumen)
        eq = m.valor(f.juego.equipo)
        partes = [f"- {m.nombre(lang)}: **{formatear(tu, m.formato, lang)}**"]
        detalles = []
        if eq is not None:
            detalles.append(_t(lang, "equipo", "team") + f" {formatear(eq, m.formato, lang)}")
        if m.referencia is not None:
            detalles.append(_t(lang, "referencia", "benchmark") + f" {formatear(m.referencia, m.formato, lang)}")
        if detalles:
            partes.append(f"({', '.join(detalles)})")
        lineas.append(" ".join(partes))
    return f"{f.jugador.nombre} · {NOMBRE_JUEGO[juego]}\n\n" + "\n".join(lineas)


def _metricas_clave(juego: Juego) -> list[str]:
    return ["winrate", "kd", "adr", "hs_pct"] if juego == "cs2" else ["winrate", "kda", "dano_min", "oro_min"]


def _comparar(a: Foco, b: Foco, lang: Idioma) -> str:
    juego = a.juego.juego
    lineas = []
    puntos = {a.jugador.nombre: 0, b.jugador.nombre: 0}
    for clave in _metricas_clave(juego):
        m = metrica(juego, clave)
        assert m is not None
        va, vb = m.valor(a.juego.resumen), m.valor(b.juego.resumen)
        if va is None or vb is None:
            continue
        if va == vb:
            ganador = _t(lang, "empate", "tie")
        else:
            gana_a = va > vb if m.mejor == "alto" else va < vb
            ganador = a.jugador.nombre if gana_a else b.jugador.nombre
            puntos[ganador] += 1
        lineas.append(
            f"- {m.nombre(lang)}: {formatear(va, m.formato, lang)} vs {formatear(vb, m.formato, lang)} → **{ganador}**"
        )
    if not lineas:
        return _t(lang, "No hay datos suficientes para compararlos.", "Not enough data to compare them.")
    (lider, pl), (otro, po) = sorted(puntos.items(), key=lambda kv: -kv[1])
    if pl == po:
        veredicto = _t(lang, "Empate técnico. Tendréis que decidirlo en el servidor.", "Dead even. Settle it on the server.")
    else:
        veredicto = _t(
            lang,
            f"{lider} gana en {pl} de {pl + po}. {otro}, toca ponerse las pilas.",
            f"{lider} wins {pl} of {pl + po}. {otro}, time to step it up.",
        )
    return f"{a.jugador.nombre} vs {b.jugador.nombre} · {NOMBRE_JUEGO[juego]}\n\n" + "\n".join(lineas) + "\n\n" + veredicto


def _contra_equipo(f: Foco, lang: Idioma) -> str:
    juego = f.juego.juego
    if not f.juego.equipo:
        return _t(lang, "No hay nadie más del equipo en este juego para comparar.", "Nobody else on the team plays this game to compare.")
    encima, debajo = [], []
    for clave in _metricas_clave(juego):
        m = metrica(juego, clave)
        assert m is not None
        tu, eq = m.valor(f.juego.resumen), m.valor(f.juego.equipo)
        if tu is None or eq is None or tu == eq:
            continue
        mejor = tu > eq if m.mejor == "alto" else tu < eq
        (encima if mejor else debajo).append(f"{m.nombre(lang)} ({formatear(tu, m.formato, lang)} vs {formatear(eq, m.formato, lang)})")
    partes = []
    if encima:
        partes.append(_t(lang, "Por encima del equipo: ", "Above the team: ") + ", ".join(encima) + ".")
    if debajo:
        partes.append(_t(lang, "Por debajo: ", "Below: ") + ", ".join(debajo) + ".")
    return "\n\n".join(partes) or _t(lang, "Vas clavado a la media del equipo.", "You're right on the team average.")


def _ranking(equipo: list[JugadorContexto], juego: Juego, pregunta: str, lang: Idioma) -> str:
    filas = [(j, g) for j in equipo if (g := _juego_de(j, juego)) and g.resumen.partidas > 0]
    if not filas:
        return _t(lang, f"Nadie del equipo tiene partidas de {NOMBRE_JUEGO[juego]}.", f"Nobody on the team has {NOMBRE_JUEGO[juego]} matches.")
    lineas = []
    puestos: dict[str, list[int]] = {j.slug: [] for j, _ in filas}
    for clave in _metricas_clave(juego)[:3]:
        m = metrica(juego, clave)
        assert m is not None
        con_valor = [(j, m.valor(g.resumen)) for j, g in filas if m.valor(g.resumen) is not None]
        if not con_valor:
            continue
        orden = sorted(con_valor, key=lambda x: -x[1] if m.mejor == "alto" else x[1])
        for i, (j, _) in enumerate(orden):
            puestos[j.slug].append(i)
        mejor, peor = orden[0], orden[-1]
        linea = f"- {m.nombre(lang)}: **{mejor[0].nombre}** ({formatear(mejor[1], m.formato, lang)})"
        if len(orden) > 1:
            linea += _t(lang, f", el último {peor[0].nombre}", f", last is {peor[0].nombre}") + f" ({formatear(peor[1], m.formato, lang)})"
        lineas.append(linea)
    texto = _t(lang, f"Así está el equipo en {NOMBRE_JUEGO[juego]}:", f"Team standings in {NOMBRE_JUEGO[juego]}:") + "\n\n" + "\n".join(lineas)
    if len(filas) > 1 and _contiene(normalizar(pregunta), ["peor", "mejorar", "worst", "improve", "malo"]):
        media = {slug: sum(p) / len(p) for slug, p in puestos.items() if p}
        peor_slug = max(media, key=media.get)
        peor_j = next(j for j, _ in filas if j.slug == peor_slug)
        texto += "\n\n" + _t(
            lang,
            f"Quien más tiene que mejorar, según los números: **{peor_j.nombre}**.",
            f"Who needs to improve the most, by the numbers: **{peor_j.nombre}**.",
        )
    return texto


def _equipo_mejorar(equipo: list[JugadorContexto], juego: Juego | None, lang: Idioma) -> str:
    lineas = []
    for j in equipo:
        g = _juego_de(j, juego)
        if not g:
            continue
        debil = next((i for i in insights_de(j, g, lang) if i.nivel in ("alto", "medio")), None)
        if debil:
            lineas.append(f"- **{j.nombre}** ({NOMBRE_JUEGO[g.juego]}): {debil.titulo}")
    if not lineas:
        return _t(lang, "No veo nada grave en nadie. Sospechoso.", "I don't see anything serious on anyone. Suspicious.")
    return _t(lang, "Lo primero que trabajaría cada uno:", "The first thing each of you should work on:") + "\n\n" + "\n".join(lineas)


AYUDA = {
    "es": "Puedo decirte en qué mejorar, qué haces bien, cómo vas últimamente, qué mapa o dios se te da peor, "
    "con quién juegas mejor o comparar a dos del equipo. Pregúntame algo de eso.",
    "en": "I can tell you what to improve, what you do well, how you've been doing lately, your worst map or god, "
    "who you play best with, or compare two teammates. Ask me any of that.",
}


def responder(p: PeticionChat) -> str:
    """Respuesta por reglas a la última pregunta del usuario."""
    lang = p.lang
    pregunta = next((m.texto for m in reversed(p.mensajes) if m.rol == "usuario"), "")
    por_slug = {j.slug: j for j in p.equipo}
    foco = [por_slug[s] for s in p.foco if s in por_slug]
    mencionado = buscar_jugador(pregunta, p.equipo)
    if not foco and mencionado:
        foco = [mencionado]

    juego = detectar_juego(pregunta, p.juego)
    intencion = detectar_intencion(pregunta, len(foco))
    # "¿En qué tiene que mejorar Bea?" desde el perfil de Ana: la pregunta manda sobre la página.
    if mencionado and intencion != "comparar" and foco[0] is not mencionado:
        foco = [mencionado]

    if intencion == "hola":
        return _t(lang, "¡Buenas! Soy el Duende. ", "Hey! I'm the Duende. ") + AYUDA[lang]

    if intencion == "companeros" and not foco:
        juego_d = juego or next((g.juego for j in p.equipo for g in j.juegos), None)
        if not juego_d:
            return _t(lang, "Aún no hay partidas guardadas.", "There are no saved matches yet.")
        return _mejores_duos(p.equipo, juego_d, lang)

    if intencion == "ranking" or (not foco and intencion in ("comparar", "fuerte", "stats")):
        juego_r = juego or next((g.juego for j in p.equipo for g in j.juegos), None)
        if not juego_r:
            return _t(lang, "Aún no hay partidas guardadas.", "There are no saved matches yet.")
        return _ranking(p.equipo, juego_r, pregunta, lang)

    if not foco:
        if intencion == "mejorar":
            return _equipo_mejorar(p.equipo, juego, lang)
        if intencion in ("racha", "desglose"):
            return _t(
                lang,
                "Dime de quién: abre su perfil o pon su nombre en la pregunta.",
                "Tell me who: open their profile or put their name in the question.",
            )
        return AYUDA[lang]

    principal = foco[0]
    g = _juego_de(principal, juego)
    if not g:
        nombre_juego = NOMBRE_JUEGO[juego] if juego else ""
        return _t(lang, f"{principal.nombre} no tiene partidas de {nombre_juego}.", f"{principal.nombre} has no {nombre_juego} matches.")
    f = Foco(principal, g)

    if intencion == "comparar":
        if len(foco) == 2 and (g2 := _juego_de(foco[1], g.juego)):
            return _comparar(f, Foco(foco[1], g2), lang)
        return _contra_equipo(f, lang)
    if intencion == "companeros":
        return _companeros(f, lang)
    if intencion == "fuerte":
        return _fuerte(f, lang)
    if intencion == "racha":
        return _racha(f, lang)
    if intencion == "desglose":
        return _desglose(f, lang)
    if intencion == "stats":
        return _stats(f, pregunta, lang)
    if intencion == "mejorar":
        return _mejorar(f, lang)
    return AYUDA[lang]


def sugerencias(p: PeticionChat) -> list[str]:
    """Preguntas rápidas para la interfaz, según de quién vaya la conversación."""
    lang = p.lang
    por_slug = {j.slug: j for j in p.equipo}
    foco = [por_slug[s] for s in p.foco if s in por_slug]
    juego = p.juego or (foco and (g := _juego_de(foco[0], None)) and g.juego) or None
    if len(foco) == 2:
        return [
            _t(lang, "¿Quién es mejor?", "Who's better?"),
            _t(lang, f"¿En qué tiene que mejorar {foco[1].nombre}?", f"What should {foco[1].nombre} improve?"),
            _t(lang, f"¿En qué tiene que mejorar {foco[0].nombre}?", f"What should {foco[0].nombre} improve?"),
        ]
    if len(foco) == 1:
        que = _t(lang, "mapa", "map") if juego == "cs2" else _t(lang, "dios", "god")
        return [
            _t(lang, "¿En qué tengo que mejorar?", "What should I improve?"),
            _t(lang, "¿Qué hago bien?", "What am I good at?"),
            _t(lang, "¿Cómo voy últimamente?", "How have I been doing lately?"),
            _t(lang, "¿Con quién juego mejor?", "Who do I play best with?"),
            _t(lang, f"¿Qué {que} se me da peor?", f"What's my worst {que}?"),
        ]
    juegos = sorted({g.juego for j in p.equipo for g in j.juegos})
    preguntas = [_t(lang, f"¿Quién es el mejor en {NOMBRE_JUEGO[jg]}?", f"Who's the best at {NOMBRE_JUEGO[jg]}?") for jg in juegos]
    preguntas.append(_t(lang, "¿En qué tiene que mejorar cada uno?", "What should each of us improve?"))
    preguntas.append(_t(lang, "¿Cuál es nuestro mejor dúo?", "What's our best duo?"))
    return preguntas[:4]


__all__ = ["responder", "sugerencias", "detectar_intencion", "detectar_juego", "insights_de", "METRICAS"]
