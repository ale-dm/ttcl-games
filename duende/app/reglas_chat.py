"""Respuestas del chat sin Gemini: detecta qué se pregunta y contesta con los datos y las recomendaciones del motor.

Es el respaldo cuando no hay GOOGLE_API_KEY, se agota la cuota o Gemini falla. Así el Duende siempre responde.
"""

import re
import unicodedata
from dataclasses import dataclass
from typing import Literal

from .informes import nombre_clave
from .insights import (
    MEJORA_CONSEJO,
    MIN_PARTIDAS_DEMO,
    MIN_SESIONES,
    aviso_de,
    companeros_destacados,
    demos_destacadas,
    efecto,
    generar_insights,
    seguimiento_destacado,
    sesiones_destacadas,
)
from .metricas import METRICAS, NOMBRE_JUEGO, formatear, metrica, rol_de
from .modelos import (
    Idioma,
    Insight,
    Juego,
    JuegoContexto,
    JugadorContexto,
    JugadorRef,
    Periodo,
    PeticionChat,
    PeticionInsights,
    ResumenPeriodo,
)
from .textos import NOMBRES_MOMENTO, NOMBRES_ROL

Intencion = Literal[
    "hola", "seguimiento", "companeros", "sesiones", "demos", "nivel", "ranking", "comparar", "racha", "desglose",
    "fuerte", "mejorar", "stats", "ayuda",
]

# Lo que solo sale de las demos (P12): las rondas, CT y T, la economía, dónde muere.
PALABRAS_DEMOS = [
    "kast", "rating", "trade", "apertura", "opening", "primer duelo", "first duel", "ronda", "round", "economia",
    "economy", "eco", "forzada", "force buy", "pistola", "pistol", "donde muero", "donde me matan", "donde mueres",
    "where do i die", "where i die", "mapa de calor", "heatmap", "heat map", "demo", "flash",
]
LADOS_DEMOS = re.compile(r"\b(de|como|as|on|lado) (ct|t)\b|\b(ct|t) side\b|\bct\b")


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
    # "¿Ha funcionado lo que me dijiste?": antes que todo lo demás (lleva "mejor", "quién"...).
    if _contiene(
        t,
        ["funciona", "sirvi", "me dijiste", "me has dicho", "tus consejos", "consejos de antes", "did it work",
         "has it worked", "is it working", "your advice", "you told me", "your tips"],
    ):
        return "seguimiento"
    # Antes que "quién" (ranking y comparar): "¿con quién juego mejor?" va de compañeros.
    if _contiene(t, ["con quien", "companer", "duo", "trio", "sinergi", "synerg", "teammate", "partner"]) or re.search(
        r"\bwho\b.*\bwith\b", t
    ):
        return "companeros"
    # Antes que "mejor" (mejorar): "¿cuándo juego mejor?" va de sesiones y horas.
    if _contiene(
        t,
        ["tilt", "sesion", "session", "partidas seguidas", "matches in a row", "games in a row", "horario",
         "a que hora", "what time", "time of day", "cuando juego", "cuando juga", "when do i play",
         "when should i play", "despues de perder", "tras perder", "after losing", "after a loss"],
    ):
        return "sesiones"
    # Antes que "mapa" y "mejor": "¿en qué mapa muero sin trade?" o "¿cómo voy de T?" van de las demos.
    if _contiene(t, PALABRAS_DEMOS) or LADOS_DEMOS.search(t):
        return "demos"
    # Antes que "quién" y "mejor": "¿cómo voy para mi nivel?" o "¿quién tiene más ELO?" van del nivel de FACEIT. Pero
    # "¿qué mejoro para subir de nivel?" es de mejorar.
    if _contiene(t, ["nivel", "elo", "faceit", "percentil", "level", "percentile"]) and not _contiene(
        t, ["mejorar", "mejoro", "subir", "improve", "level up"]
    ):
        return "nivel"
    if _contiene(t, ["compar", "vs", "versus", "contra", "frente a", "head to head"]) or (
        num_foco == 2 and _contiene(t, ["quien", "who"])
    ):
        return "comparar"
    if _contiene(t, ["quien", "who", "ranking", "clasificacion", "del equipo", "of the team", "best", "worst", "top"]):
        return "ranking"
    if _contiene(t, ["racha", "forma", "ultim", "streak", "form", "recent", "lately"]):
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
        sesiones=g.sesiones,
        seguimiento=g.seguimiento,
        nivel=g.nivel,
        demos=g.demos,
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


def _sesiones(f: Foco, lang: Idioma) -> str:
    """Cómo le va según el orden en la sesión, lo que pasó en la anterior y la hora; y si se tiltea o tiene su hora."""
    s = f.juego.sesiones
    juego = NOMBRE_JUEGO[f.juego.juego]
    if not s or not s.sesiones:
        return _t(lang, f"Aún no hay partidas de {juego} para saberlo.", f"No {juego} matches to tell yet.")
    media = f"{s.partidas_por_sesion or 0:.1f}"
    if lang == "es":
        media = media.replace(".", ",")
    texto = _t(
        lang,
        f"Cómo te va según cuándo juegas a {juego}, {f.jugador.nombre} ({s.sesiones} sesiones, {media} partidas de media):",
        f"How you do depending on when you play {juego}, {f.jugador.nombre} ({s.sesiones} sessions, {media} matches on average):",
    )
    lineas = [
        f"- **{NOMBRES_MOMENTO[m.clave][lang]}**: {formatear(m.winrate, 'pct', lang)} "
        f"({m.victorias} {_t(lang, 'de', 'of')} {m.partidas})"
        for m in [*s.por_orden, *s.tras_resultado, *s.por_franja]
        if m.winrate is not None and m.clave in NOMBRES_MOMENTO
    ]
    texto += "\n\n" + "\n".join(lineas)
    # Sin el tope del panel: aquí se pregunta justo por esto.
    destacados = sesiones_destacadas(_peticion(f.jugador, f.juego, lang))
    if s.sesiones < MIN_SESIONES:
        texto += "\n\n" + _t(
            lang,
            f"Con {s.sesiones} sesiones aún no saco conclusiones: a partir de {MIN_SESIONES} te digo si te tilteas o "
            "cuál es tu hora.",
            f"With {s.sesiones} sessions I can't draw conclusions yet: from {MIN_SESIONES} on I'll tell you whether you "
            "tilt or what your best time is.",
        )
    elif not destacados:
        texto += "\n\n" + _t(
            lang,
            "Ni tilt ni hora mágica: rindes parecido juegues cuando juegues.",
            "No tilt and no magic hour: you play about the same whenever you play.",
        )
    for i in destacados:
        texto += f"\n\n**{i.titulo}.** {i.texto} {i.consejo or ''}".rstrip()
    return texto


def _hace(dias: int, lang: Idioma) -> str:
    if dias == 0:
        return _t(lang, "hoy", "today")
    if dias == 1:
        return _t(lang, "ayer", "yesterday")
    return _t(lang, f"hace {dias} días", f"{dias} days ago")


def _seguimiento(f: Foco, lang: Idioma) -> str:
    """Los consejos que se le han dado y cómo ha ido cada uno desde entonces."""
    juego = f.juego.juego
    seguimiento = [s for s in f.juego.seguimiento if metrica(juego, s.metrica)]
    if not seguimiento:
        return _t(
            lang,
            f"Aún no tengo consejos tuyos de {NOMBRE_JUEGO[juego]} que revisar. Cuando te avise de algo, en una o dos "
            "semanas te digo si ha funcionado.",
            f"I don't have any {NOMBRE_JUEGO[juego]} tips of yours to review yet. Once I warn you about something, "
            "give it a week or two and I'll tell you whether it worked.",
        )
    lineas = []
    for s in seguimiento:
        m = metrica(juego, s.metrica)
        assert m is not None
        cuando = _hace(s.dias, lang)
        antes = formatear(s.valor, m.formato, lang)
        cabeza = f"- **{aviso_de(s, m, lang)}** ({cuando}): {m.nombre(lang)} {antes}"
        if s.valor_desde is None:
            lineas.append(cabeza + _t(lang, "; aún no has jugado desde entonces.", "; you haven't played since."))
            continue
        ahora = formatear(s.valor_desde, m.formato, lang)
        d = efecto(s, juego)
        if d is None:
            veredicto = _t(lang, "Aún es pronto para saberlo.", "Too early to tell.")
        elif d >= MEJORA_CONSEJO:
            veredicto = _t(lang, "Funciona.", "It's working.")
        elif d <= 0:
            veredicto = _t(lang, "Sigue sin mejorar.", "Still not improving.")
        else:
            veredicto = _t(lang, "Algo mejor, pero poco.", "A bit better, but not much.")
        lineas.append(
            cabeza
            + _t(
                lang,
                f" → {ahora} en {s.partidas_desde} partidas desde entonces. {veredicto}",
                f" → {ahora} over {s.partidas_desde} matches since. {veredicto}",
            )
        )
    texto = _t(
        lang,
        f"Lo que te he ido diciendo en {NOMBRE_JUEGO[juego]}, {f.jugador.nombre}:",
        f"What I've been telling you in {NOMBRE_JUEGO[juego]}, {f.jugador.nombre}:",
    )
    texto += "\n\n" + "\n".join(lineas)
    # Sin el tope del panel: aquí se pregunta justo por esto.
    for i in seguimiento_destacado(_peticion(f.jugador, f.juego, lang)):
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


def _nivel(f: Foco, lang: Idioma) -> str:
    """Cómo va frente a los jugadores de su nivel de FACEIT: cada métrica, lo normal en ese nivel y su percentil."""
    nombre, g = f.jugador.nombre, f.juego
    n = g.nivel
    if n is None:
        if g.juego != "cs2":
            return _t(
                lang,
                f"El nivel solo lo sé de FACEIT (CS2). En {NOMBRE_JUEGO[g.juego]} comparo con el equipo y con "
                "referencias fijas.",
                f"I only know levels from FACEIT (CS2). In {NOMBRE_JUEGO[g.juego]} I compare with the team and with "
                "fixed benchmarks.",
            )
        return _t(
            lang,
            f"Aún no sé el nivel de FACEIT de {nombre}: lo leo al sincronizar con FACEIT.",
            f"I don't know {nombre}'s FACEIT level yet: I read it when syncing with FACEIT.",
        )
    elo = f" ({formatear(n.elo, 'int', lang)} ELO)" if n.elo else ""
    cabecera = f"**{nombre} · " + _t(lang, f"nivel {n.nivel} de FACEIT", f"FACEIT level {n.nivel}") + f"{elo}**"
    lineas: list[str] = []
    con_percentil: list[tuple[str, float]] = []
    for m in METRICAS[g.juego]:
        mn = next((x for x in n.metricas if x.metrica == m.clave), None)
        tu = m.valor(g.resumen)
        if mn is None or tu is None:
            continue
        linea = (
            f"- {m.nombre(lang)}: **{formatear(tu, m.formato, lang)}** · "
            + _t(lang, f"nivel {n.nivel}: ", f"level {n.nivel}: ")
            + formatear(mn.referencia, m.formato, lang)
        )
        if mn.percentil is not None:
            mejor = mn.percentil if m.mejor == "alto" else 100 - mn.percentil
            con_percentil.append((m.nombre(lang), mejor))
            linea += _t(
                lang, f" · mejor que el {round(mejor)} % de las partidas", f" · better than {round(mejor)}% of matches"
            )
        lineas.append(linea)
    if not lineas:
        return (
            cabecera
            + "\n\n"
            + _t(
                lang,
                "Aún no tengo partidas suficientes de jugadores de ese nivel para comparar.",
                "I don't have enough matches from players at that level to compare yet.",
            )
        )
    texto = (
        cabecera
        + ". "
        + _t(
            lang,
            f"Comparado con {n.partidas} partidas de jugadores de ese nivel:",
            f"Compared with {n.partidas} matches from players at that level:",
        )
        + "\n\n"
        + "\n".join(lineas)
    )
    if len(con_percentil) >= 2:
        mejor_m = max(con_percentil, key=lambda x: x[1])[0]
        peor_m = min(con_percentil, key=lambda x: x[1])[0]
        texto += "\n\n" + _t(
            lang,
            f"Donde más destaca para su nivel: {mejor_m}. Donde más le queda: {peor_m}.",
            f"Best for that level: {mejor_m}. Furthest behind: {peor_m}.",
        )
    return texto


COMPRAS: dict[str, dict[Idioma, str]] = {
    "completa": {"es": "completa", "en": "full buy"},
    "forzada": {"es": "forzada", "en": "force buy"},
    "eco": {"es": "eco", "en": "eco"},
    "pistola": {"es": "pistola", "en": "pistol"},
}


def _demos(f: Foco, lang: Idioma) -> str:
    """Lo que dicen las rondas de sus demos (P12): rating, KAST, aperturas, trades, CT y T, economía y dónde muere."""
    g, nombre = f.juego, f.jugador.nombre
    if g.juego != "cs2":
        return _t(lang, "Las demos solo las analizo en CS2.", "I only analyse demos for CS2.")
    d = g.demos
    if not d or not d.metricas.partidas:
        return _t(
            lang,
            f"Aún no tengo ninguna demo analizada de {nombre}. Se analizan solas si se deja la demo (la de la sala "
            "de la partida en FACEIT) en la carpeta de demos.",
            f"I don't have any analysed demos from {nombre} yet. They're analysed automatically when the demo "
            "(from the FACEIT match room) is dropped in the demos folder.",
        )
    m, e = d.metricas, d.equipo

    def con_equipo(clave: str, formato: str) -> str:
        valor = formatear(getattr(m, clave), formato, lang)  # type: ignore[arg-type]
        extra = f" ({_t(lang, 'equipo', 'team')} {formatear(getattr(e, clave), formato, lang)})" if e else ""  # type: ignore[arg-type]
        return f"**{valor}**{extra}"

    de = _t(lang, "de", "of")
    lineas = [
        f"- Rating: {con_equipo('rating', 'dec')}",
        f"- KAST: {con_equipo('kast', 'pct')}",
    ]
    if m.aperturas:
        lineas.append(
            _t(lang, "- Duelos de apertura: ganas ", "- Opening duels: you win ")
            + f"**{m.aperturas_ganadas} {de} {m.aperturas}** ({formatear(m.apertura_pct, 'pct', lang)})"
        )
    lineas.append(
        _t(lang, "- Trades: ", "- Trades: ")
        + f"**{formatear(m.trades_partida, 'dec', lang)}** "
        + _t(lang, "por partida; te tradean el ", "per match; you get traded on ")
        + f"**{formatear(m.tradeadas_pct, 'pct', lang)}**"
        + _t(lang, " de tus muertes", " of your deaths")
    )
    lineas.append(
        _t(lang, "- Asistencias de flash: ", "- Flash assists: ")
        + f"**{formatear(m.flash_partida, 'dec', lang)}** "
        + _t(lang, "por partida", "per match")
    )
    lados = [
        f"{_t(lang, 'de', 'on')} {lado.lado}: rating {formatear(lado.rating, 'dec', lang)}, "
        + _t(lang, f"ganas el {formatear(lado.winrate, 'pct', lang)} de las rondas", f"you win {formatear(lado.winrate, 'pct', lang)} of rounds")
        for lado in d.lados
    ]
    if lados:
        lineas.append("- " + " · ".join(x[:1].upper() + x[1:] for x in lados))
    if d.economia:
        compras = sorted(d.economia, key=lambda c: list(COMPRAS).index(c.compra) if c.compra in COMPRAS else 9)
        lineas.append(
            _t(lang, "- Rondas ganadas según la compra: ", "- Rounds won by buy: ")
            + " · ".join(
                f"{COMPRAS.get(c.compra, {}).get(lang, c.compra)} {formatear(c.winrate, 'pct', lang)}" for c in compras
            )
        )
    texto = (
        f"**{nombre} · "
        + _t(lang, f"{m.partidas} partidas con demo ({m.rondas} rondas)", f"{m.partidas} matches with a demo ({m.rondas} rounds)")
        + "**\n\n"
        + "\n".join(lineas)
    )
    # Dónde muere más sin trade, de todos los mapas.
    zonas = [(mp, z) for mp in d.mapas if mp.muertes for z in mp.zonas if z.sin_trade]
    if zonas:
        mp, z = max(zonas, key=lambda x: (x[1].sin_trade / x[0].muertes, x[1].sin_trade))
        texto += "\n\n" + _t(
            lang,
            f"Donde más mueres sin que te tradeen: **{z.zona}** en {nombre_clave(mp.mapa)} ({z.sin_trade} de tus "
            f"{mp.muertes} muertes en ese mapa).",
            f"Where you die untraded the most: **{z.zona}** on {nombre_clave(mp.mapa)} ({z.sin_trade} of your "
            f"{mp.muertes} deaths on that map).",
        )
    if m.partidas < MIN_PARTIDAS_DEMO:
        texto += "\n\n" + _t(
            lang,
            f"Con {m.partidas} partidas analizadas aún no saco conclusiones: a partir de {MIN_PARTIDAS_DEMO}, sí.",
            f"With {m.partidas} analysed matches I can't draw conclusions yet: from {MIN_PARTIDAS_DEMO} on, I will.",
        )
        return texto
    # Sin el tope del panel: aquí se pregunta justo por esto.
    for i in [x for x in demos_destacadas(_peticion(f.jugador, g, lang)) if x.nivel in ("alto", "medio")][:2]:
        texto += f"\n\n**{i.titulo}.** {i.texto} {i.consejo or ''}".rstrip()
    return texto


def _demos_equipo(equipo: list[JugadorContexto], lang: Idioma) -> str:
    """El rating de las demos de cada uno, el más alto primero."""
    filas = [(j.nombre, g.demos) for j in equipo for g in j.juegos if g.demos and g.demos.metricas.partidas]
    if not filas:
        return _t(
            lang,
            "Aún no tengo ninguna demo analizada del equipo (solo CS2).",
            "I don't have any analysed demos from the team yet (CS2 only).",
        )
    filas.sort(key=lambda x: x[1].metricas.rating or 0, reverse=True)
    lineas = [
        f"- {nombre}: **{formatear(d.metricas.rating, 'dec', lang)}** (KAST {formatear(d.metricas.kast, 'pct', lang)}, "
        + _t(lang, f"{d.metricas.partidas} partidas)", f"{d.metricas.partidas} matches)")
        for nombre, d in filas
    ]
    return _t(lang, "Rating de las demos del equipo:", "The team's demo ratings:") + "\n\n" + "\n".join(lineas)


def _niveles_equipo(equipo: list[JugadorContexto], lang: Idioma) -> str:
    """El nivel de FACEIT de cada uno, el de más ELO primero."""
    con_nivel = [(j.nombre, g.nivel) for j in equipo for g in j.juegos if g.nivel]
    if not con_nivel:
        return _t(
            lang,
            "Aún no sé el nivel de FACEIT de nadie: lo leo al sincronizar con FACEIT (solo CS2).",
            "I don't know anyone's FACEIT level yet: I read it when syncing with FACEIT (CS2 only).",
        )
    con_nivel.sort(key=lambda x: (x[1].nivel, x[1].elo or 0), reverse=True)
    lineas = [
        f"- {nombre}: " + _t(lang, f"nivel {n.nivel}", f"level {n.nivel}")
        + (f" ({formatear(n.elo, 'int', lang)} ELO)" if n.elo else "")
        for nombre, n in con_nivel
    ]
    return _t(lang, "Niveles de FACEIT del equipo:", "The team's FACEIT levels:") + "\n\n" + "\n".join(lineas)


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
    "con quién juegas mejor, cuándo juegas mejor (si te tilteas, a qué hora rindes más), si ha funcionado lo que te "
    "dije, cómo vas para tu nivel de FACEIT, qué dicen tus demos de CS2 (rating, KAST, trades, CT y T, dónde mueres) "
    "o comparar a dos del equipo. Y si dices «esta semana» o «este mes», miro solo esos días. Pregúntame algo de eso.",
    "en": "I can tell you what to improve, what you do well, how you've been doing lately, your worst map or god, "
    "who you play best with, when you play best (whether you tilt, what time suits you), whether my advice worked, "
    "how you're doing for your FACEIT level, what your CS2 demos say (rating, KAST, trades, CT and T, where you die) "
    "or compare two teammates. And if you say 'this week' or 'this month', I'll look at just those days. Ask me any "
    "of that.",
}

# ─── Periodos ────────────────────────────────────────────────────────────────

ETIQUETA_PERIODO: dict[str, dict[Idioma, str]] = {
    "7d": {"es": "Últimos 7 días", "en": "Last 7 days"},
    "30d": {"es": "Últimos 30 días", "en": "Last 30 days"},
}
# Esto solo se calcula con todas las partidas: con unos pocos días no hay muestra.
SOLO_CON_TODAS = ("companeros", "desglose", "sesiones", "seguimiento", "nivel", "demos")


def detectar_periodo(pregunta: str, por_defecto: Periodo | None) -> Periodo | None:
    """El periodo del que habla la pregunta o, si no dice ninguno, el de la página."""
    t = normalizar(pregunta)
    if _contiene(t, ["esta semana", "ultima semana", "semana pasada", "7 dias", "siete dias", "this week",
                     "last week", "past week", "7 days", "seven days"]):
        return "7d"
    if _contiene(t, ["este mes", "ultimo mes", "mes pasado", "30 dias", "treinta dias", "this month", "last month",
                     "past month", "30 days", "thirty days"]):
        return "30d"
    if _contiene(t, ["desde siempre", "de siempre", "en total", "todas las partidas", "all time", "all-time",
                     "overall"]):
        return "todo"
    return por_defecto


def _resumen_de(g: JuegoContexto, periodo: Periodo) -> ResumenPeriodo | None:
    return next((pr for pr in g.periodos if pr.periodo == periodo), None)


def _en_periodo(equipo: list[JugadorContexto], periodo: Periodo) -> list[JugadorContexto]:
    """El equipo con los números de esos días: cada juego con su resumen y la media del equipo en esos días. Lo que
    solo hay con todas las partidas (desglose, compañeros, sesiones) no va, y los juegos sin partidas, tampoco."""
    return [
        j.model_copy(
            update={
                "juegos": [
                    JuegoContexto(juego=g.juego, rol=g.rol, resumen=pr.resumen, equipo=pr.equipo)
                    for g in j.juegos
                    if (pr := _resumen_de(g, periodo))
                ]
            }
        )
        for j in equipo
    ]


def _como_voy(j: JugadorContexto, periodo: Periodo, juego: Juego | None, lang: Idioma) -> str:
    """Los números de esos días frente a los de siempre, en el juego pedido o en el que más ha jugado estos días."""
    con_datos = [
        (g, pr) for g in j.juegos if (pr := _resumen_de(g, periodo)) and (juego is None or g.juego == juego)
    ]
    if not con_datos:
        if juego:
            return _t(
                lang,
                f"{j.nombre} no ha jugado a {NOMBRE_JUEGO[juego]} en estos días.",
                f"{j.nombre} hasn't played {NOMBRE_JUEGO[juego]} in that time.",
            )
        return _t(lang, f"{j.nombre} no ha jugado en estos días.", f"{j.nombre} hasn't played in that time.")
    g, pr = max(con_datos, key=lambda x: x[1].resumen.partidas)
    r, siempre = pr.resumen, g.resumen

    def cuenta(n: int, es: str, en: str) -> str:
        """Número con su palabra en singular o plural: cada texto va como "singular|plural"."""
        uno, varios = _t(lang, es, en).split("|")
        return f"{n} {uno if n == 1 else varios}"

    texto = _t(lang, f"{j.nombre} en {NOMBRE_JUEGO[g.juego]}: ", f"{j.nombre} in {NOMBRE_JUEGO[g.juego]}: ") + (
        f"{cuenta(r.partidas, 'partida|partidas', 'match|matches')} ("
        f"{cuenta(r.victorias, 'victoria|victorias', 'win|wins')} {_t(lang, 'y', 'and')} "
        f"{cuenta(r.derrotas, 'derrota|derrotas', 'loss|losses')})."
    )
    lineas = []
    for clave in _metricas_clave(g.juego):
        m = metrica(g.juego, clave)
        assert m is not None
        if (valor := m.valor(r)) is None:
            continue
        lineas.append(
            f"- {m.nombre(lang)}: **{formatear(valor, m.formato, lang)}** "
            f"({_t(lang, 'con todas', 'all matches')}: {formatear(m.valor(siempre), m.formato, lang)})"
        )
    texto += "\n\n" + "\n".join(lineas)
    if r.winrate is not None and siempre.winrate is not None:
        diferencia = r.winrate - siempre.winrate
        if diferencia >= 5:
            texto += "\n\n" + _t(lang, "Mejor que de costumbre. Sigue así.", "Better than usual. Keep it up.")
        elif diferencia <= -5:
            texto += "\n\n" + _t(
                lang, "Peor que de costumbre: algo ha cambiado estos días.", "Worse than usual: something's changed lately."
            )
        else:
            texto += "\n\n" + _t(lang, "En tu línea de siempre.", "Right at your usual level.")
    return texto


def responder(p: PeticionChat) -> str:
    """Respuesta por reglas a la última pregunta del usuario.

    Si la pregunta habla de unos días ("esta semana", "este mes") o en la página hay un periodo elegido, los números
    generales son los de esos días; "¿cómo voy?" los compara con los de siempre.
    """
    lang = p.lang
    pregunta = _ultima_pregunta(p)
    periodo = detectar_periodo(pregunta, p.periodo)
    if periodo not in ETIQUETA_PERIODO:
        return _responder(p, pregunta)
    assert periodo is not None
    foco, intencion = _foco_e_intencion(p, pregunta)
    if intencion == "hola":
        return _responder(p, pregunta)
    if intencion in SOLO_CON_TODAS:
        aviso = _t(
            lang,
            "Esto lo miro con todas las partidas: por días solo separo los números generales.",
            "I look at this with all matches: I only split the overall numbers by days.",
        )
        return aviso + "\n\n" + _responder(p, pregunta)

    etiqueta = f"**{ETIQUETA_PERIODO[periodo][lang]}.** "
    recortado = p.model_copy(update={"equipo": _en_periodo(p.equipo, periodo)})
    juego = detectar_juego(pregunta, p.juego)
    if intencion in ("ayuda", "racha"):
        if foco:
            return etiqueta + _como_voy(foco[0], periodo, juego, lang)
        # Sin nadie en concreto: cómo está el equipo en esos días.
        juego_r = juego or next((g.juego for j in recortado.equipo for g in j.juegos), None)
        if not juego_r:
            return etiqueta + _t(lang, "Nadie del equipo ha jugado en estos días.", "Nobody on the team has played in that time.")
        return etiqueta + _ranking(recortado.equipo, juego_r, pregunta, lang)
    return etiqueta + _responder(recortado, pregunta)


def _ultima_pregunta(p: PeticionChat) -> str:
    return next((m.texto for m in reversed(p.mensajes) if m.rol == "usuario"), "")


def intencion(p: PeticionChat) -> Intencion:
    """De qué va la última pregunta según las reglas, conteste quien conteste (va con las valoraciones de la web)."""
    return _foco_e_intencion(p, _ultima_pregunta(p))[1]


def _foco_e_intencion(p: PeticionChat, pregunta: str) -> tuple[list[JugadorContexto], Intencion]:
    """De quién va la pregunta (la página o quien se nombre) y qué se pregunta."""
    por_slug = {j.slug: j for j in p.equipo}
    foco = [por_slug[s] for s in p.foco if s in por_slug]
    mencionado = buscar_jugador(pregunta, p.equipo)
    if not foco and mencionado:
        foco = [mencionado]
    intencion = detectar_intencion(pregunta, len(foco))
    # "¿En qué tiene que mejorar Bea?" desde el perfil de Ana: la pregunta manda sobre la página.
    if mencionado and intencion != "comparar" and foco[0] is not mencionado:
        foco = [mencionado]
    return foco, intencion


def _responder(p: PeticionChat, pregunta: str) -> str:
    """Respuesta con los datos que vengan en la petición (todas las partidas o las de un periodo)."""
    lang = p.lang
    foco, intencion = _foco_e_intencion(p, pregunta)
    juego = detectar_juego(pregunta, p.juego)

    if intencion == "hola":
        return _t(lang, "¡Buenas! Soy el Duende. ", "Hey! I'm the Duende. ") + AYUDA[lang]

    if intencion == "companeros" and not foco:
        juego_d = juego or next((g.juego for j in p.equipo for g in j.juegos), None)
        if not juego_d:
            return _t(lang, "Aún no hay partidas guardadas.", "There are no saved matches yet.")
        return _mejores_duos(p.equipo, juego_d, lang)

    # "¿Quién tiene mejor rating?" o sin nadie en concreto: las demos de todos.
    if intencion == "demos" and (not foco or _contiene(normalizar(pregunta), ["quien", "who", "cada uno", "each of"])):
        return _demos_equipo(p.equipo, lang)

    # "¿Quién tiene más nivel?": el de todos.
    if intencion == "nivel" and (not foco or _contiene(normalizar(pregunta), ["quien", "who", "cada uno", "each of"])):
        return _niveles_equipo(p.equipo, lang)

    if intencion == "ranking" or (not foco and intencion in ("comparar", "fuerte", "stats")):
        juego_r = juego or next((g.juego for j in p.equipo for g in j.juegos), None)
        if not juego_r:
            return _t(lang, "Aún no hay partidas guardadas.", "There are no saved matches yet.")
        return _ranking(p.equipo, juego_r, pregunta, lang)

    if not foco:
        if intencion == "mejorar":
            return _equipo_mejorar(p.equipo, juego, lang)
        if intencion in ("racha", "desglose", "sesiones", "seguimiento"):
            return _t(
                lang,
                "Dime de quién: abre su perfil o pon su nombre en la pregunta.",
                "Tell me who: open their profile or put their name in the question.",
            )
        return AYUDA[lang]

    principal = foco[0]
    if intencion == "nivel" and not juego:
        # El nivel es de FACEIT: si la página no dice juego, el que lo tenga.
        juego = next((g.juego for g in principal.juegos if g.nivel), None)
    if intencion == "demos" and not juego:
        # Las demos son de CS2.
        juego = "cs2" if any(g.juego == "cs2" for g in principal.juegos) else None
    g = _juego_de(principal, juego)
    if not g:
        if not juego:
            return _t(lang, f"{principal.nombre} no tiene partidas.", f"{principal.nombre} has no matches.")
        nombre_juego = NOMBRE_JUEGO[juego]
        return _t(lang, f"{principal.nombre} no tiene partidas de {nombre_juego}.", f"{principal.nombre} has no {nombre_juego} matches.")
    f = Foco(principal, g)

    if intencion == "comparar":
        if len(foco) == 2 and (g2 := _juego_de(foco[1], g.juego)):
            return _comparar(f, Foco(foco[1], g2), lang)
        return _contra_equipo(f, lang)
    if intencion == "companeros":
        return _companeros(f, lang)
    if intencion == "sesiones":
        return _sesiones(f, lang)
    if intencion == "seguimiento":
        return _seguimiento(f, lang)
    if intencion == "nivel":
        return _nivel(f, lang)
    if intencion == "demos":
        return _demos(f, lang)
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
        g = _juego_de(foco[0], juego)
        # Si ya le ha dado consejos, preguntar si han funcionado va arriba.
        seguimiento = [_t(lang, "¿Ha funcionado lo que me dijiste?", "Did your advice work?")] if g and g.seguimiento else []
        nivel = [_t(lang, "¿Cómo voy para mi nivel?", "How am I doing for my level?")] if g and g.nivel else []
        demos = (
            [_t(lang, "¿Dónde muero más?", "Where do I die most?")]
            if g and g.demos and g.demos.metricas.partidas
            else []
        )
        return [
            _t(lang, "¿En qué tengo que mejorar?", "What should I improve?"),
            *seguimiento,
            _t(lang, "¿Qué hago bien?", "What am I good at?"),
            _t(lang, "¿Cómo voy últimamente?", "How have I been doing lately?"),
            _t(lang, "¿Con quién juego mejor?", "Who do I play best with?"),
            _t(lang, "¿Cuándo juego mejor?", "When do I play best?"),
            *demos,
            *nivel,
            _t(lang, f"¿Qué {que} se me da peor?", f"What's my worst {que}?"),
        ]
    juegos = sorted({g.juego for j in p.equipo for g in j.juegos})
    preguntas = [_t(lang, f"¿Quién es el mejor en {NOMBRE_JUEGO[jg]}?", f"Who's the best at {NOMBRE_JUEGO[jg]}?") for jg in juegos]
    preguntas.append(_t(lang, "¿En qué tiene que mejorar cada uno?", "What should each of us improve?"))
    preguntas.append(_t(lang, "¿Cuál es nuestro mejor dúo?", "What's our best duo?"))
    return preguntas[:4]


__all__ = [
    "responder", "sugerencias", "intencion", "detectar_intencion", "detectar_juego", "detectar_periodo", "insights_de",
    "METRICAS",
]
