"""Lectura de capturas de csstats.gg con Gemini (P13): de la imagen del perfil de un jugador saca los números que pide la
API (las claves de Capturas.CAMPOS en Java). No guarda nada ni se fía de nada: la web enseña lo leído para que alguien
lo revise antes de guardarlo, y la API vuelve a limpiar los números.

Sin GOOGLE_API_KEY (o sin cuota, o si Gemini falla) devuelve todo vacío con el motivo, y en la web se rellena a mano.
Cada lectura cuenta como una respuesta para el límite diario del chat.
"""

import base64
import binascii
import logging
import re

from . import gemini
from .chat import limite
from .config import get_config
from .modelos import MapaLeido, PeticionLectura, RespuestaLectura

log = logging.getLogger("duende.lectura")

TIPOS = {"image/png", "image/jpeg", "image/webp"}
MAX_MAPAS = 12
CLAVE_VALIDA = re.compile(r"^[a-z0-9_]{1,40}$")

# Dónde está cada número en la página de un perfil de csstats.gg (en inglés: es como sale en la web). Lo que no esté
# aquí también se pide, con su clave como descripción.
DESCRIPCIONES: dict[str, str] = {
    "rating": "HLTV RATING (big number in a circle), e.g. 0.74",
    "kd": "K/D (big number in a circle), e.g. 0.6",
    "winrate": "WIN RATE percentage, e.g. 43",
    "hs_pct": "HS% percentage (headshot percentage card), e.g. 36",
    "adr": "ADR (average damage per round), e.g. 57",
    "kast": "KAST percentage, e.g. 66",
    "partidas": "PLAYED (matches played)",
    "victorias": "WON",
    "derrotas": "LOST",
    "empates": "TIED",
    "kills": "KILLS total",
    "muertes": "DEATHS total",
    "asistencias": "ASSISTS total",
    "headshots": "HEADSHOTS total",
    "dano": "DAMAGE total",
    "rondas": "ROUNDS total",
    "clutch_pct": "CLUTCH SUCCESS 1vX percentage",
    "clutch_1v1_ganados": "1v1 clutch wins (the W number under 1v1, e.g. W:52)",
    "clutch_1v1_perdidos": "1v1 clutch losses (the L number under 1v1, e.g. L:53)",
    "clutch_1v2_ganados": "1v2 clutch wins (W under 1v2)",
    "clutch_1v2_perdidos": "1v2 clutch losses (L under 1v2)",
    "clutch_1v3_ganados": "1v3 clutch wins (W under 1v3)",
    "clutch_1v3_perdidos": "1v3 clutch losses (L under 1v3)",
    "clutch_1v4_ganados": "1v4 clutch wins (W under 1v4)",
    "clutch_1v4_perdidos": "1v4 clutch losses (L under 1v4)",
    "clutch_1v5_ganados": "1v5 clutch wins (W under 1v5)",
    "clutch_1v5_perdidos": "1v5 clutch losses (L under 1v5)",
    "entry_ronda": "ENTRY SUCCESS 'per Round' percentage, e.g. 6",
    "entry_exito": "Entry Success, Combined column percentage",
    "entry_exito_t": "Entry Success, T Entries column percentage",
    "entry_exito_ct": "Entry Success, CT Entries column percentage",
    "entry_intentos": "Entry Attempts, Combined column percentage",
    "entry_intentos_t": "Entry Attempts, T Entries column percentage",
    "entry_intentos_ct": "Entry Attempts, CT Entries column percentage",
    "premier_actual": "PREMIER rating CURRENT of the latest season row (e.g. the S4 row); empty if '---'",
    "premier_mejor": "PREMIER rating BEST of the latest season row",
    "premier_victorias": "PREMIER WINS of the latest season row",
    "competitive_victorias": "COMPETITIVE wins number",
    "wingman_victorias": "WINGMAN wins number",
}

INSTRUCCIONES = (
    "This is a screenshot of a Counter-Strike 2 player's stats page on csstats.gg. Read the numbers exactly as shown "
    "and fill in the JSON. Use plain numbers: no %, no thousands separators (11,445 -> 11445), decimals with a dot. "
    "If a number isn't visible or shows '-' or '---', leave it out: never guess or compute anything. In 'mapas', put "
    "each map from the 'Most Played' list with its number of matches as 'partidas', and each map from the 'Most "
    "Success' list with its percentage as 'winrate'; use the map name as written (de_inferno...)."
)


def esquema(claves: list[str]) -> dict:
    """El esquema JSON que tiene que rellenar Gemini: un número por clave y la lista de mapas."""
    return {
        "type": "object",
        "properties": {
            "campos": {
                "type": "object",
                "properties": {c: {"type": "number", "description": DESCRIPCIONES.get(c, c)} for c in claves},
            },
            "mapas": {
                "type": "array",
                "items": {
                    "type": "object",
                    "properties": {
                        "mapa": {"type": "string"},
                        "partidas": {"type": "integer"},
                        "winrate": {"type": "number"},
                    },
                    "required": ["mapa"],
                },
            },
        },
        "required": ["campos"],
    }


def _vacia(motivo: str) -> RespuestaLectura:
    return RespuestaLectura(origen="ninguno", motivo=motivo)


def _numero(valor: object) -> float | None:
    if isinstance(valor, bool) or not isinstance(valor, (int, float)):
        return None
    return float(valor)


def leer(p: PeticionLectura) -> RespuestaLectura:
    cfg = get_config()
    if not cfg.gemini_configurado:
        return _vacia("sin_clave")
    if p.tipo not in TIPOS:
        return _vacia("imagen")
    try:
        imagen = base64.b64decode(p.imagen, validate=True)
    except (binascii.Error, ValueError):
        return _vacia("imagen")
    claves = [c for c in dict.fromkeys(p.campos) if CLAVE_VALIDA.match(c)]
    if not imagen or not claves:
        return _vacia("imagen")
    if not limite.disponible(cfg.daily_limit):
        log.warning("Límite diario de Gemini alcanzado: la captura se rellena a mano")
        return _vacia("cuota")
    try:
        datos, modelo = gemini.leer_imagen(imagen, p.tipo, INSTRUCCIONES, esquema(claves))
    except gemini.GeminiError as err:
        log.warning("No se pudo leer la captura (%s): %s", err.codigo, err)
        return _vacia(err.codigo)
    limite.registrar()

    leidos = datos.get("campos") if isinstance(datos.get("campos"), dict) else {}
    campos = {c: n for c in claves if (n := _numero(leidos.get(c))) is not None}
    mapas: list[MapaLeido] = []
    for m in datos.get("mapas") or []:
        if not isinstance(m, dict) or not isinstance(m.get("mapa"), str) or not m["mapa"].strip():
            continue
        partidas = _numero(m.get("partidas"))
        winrate = _numero(m.get("winrate"))
        if partidas is None and winrate is None:
            continue
        mapas.append(
            MapaLeido(mapa=m["mapa"].strip()[:100], partidas=int(partidas) if partidas is not None else None, winrate=winrate)
        )
        if len(mapas) >= MAX_MAPAS:
            break
    log.info("Captura leída con %s: %d números y %d mapas", modelo, len(campos), len(mapas))
    return RespuestaLectura(origen="gemini", modelo=modelo, campos=campos, mapas=mapas)
