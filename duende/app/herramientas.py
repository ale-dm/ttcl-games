"""Herramientas del chat con Gemini (P9): consultas a la API Java para lo que no está en los resúmenes ("¿cómo voy en
Mirage este mes?", "¿qué pasó en mis dos últimas derrotas?", "¿con quién juego mejor esta semana?", "¿cómo voy de T
este mes?", que sale de las demos, P12).

Devuelven los datos de la API tal cual (recortados para no gastar tokens), nunca texto: todo número sale de la API.
"""

import logging

from google.genai import types

from . import api_ttcl
from .modelos import PeticionChat
from .reglas_chat import normalizar

log = logging.getLogger("duende.herramientas")

JUEGOS = ["cs2", "smite2"]
PERIODOS = ["7d", "30d", "todo"]
# Partidas que se le pasan a Gemini de cada consulta (el resumen ya cuenta todas).
MAX_PARTIDAS = 10
# Lo que no le sirve a Gemini de cada partida.
SOBRA_EN_PARTIDA = {"partidaId", "juego"}
# Zonas de cada mapa que se le pasan de las demos (las de más muertes sin trade).
MAX_ZONAS = 3


def _periodo() -> dict:
    return {
        "type": "string",
        "enum": PERIODOS,
        "description": "Últimos 7 días, últimos 30 días o todas las partidas (por defecto, todas).",
    }


def declaraciones(nombres: list[str]) -> types.Tool:
    """Las cinco herramientas, con los nombres del equipo como únicos jugadores posibles."""
    jugador = {"type": "string", "enum": nombres, "description": "Nombre del jugador del equipo."}
    juego = {"type": "string", "enum": JUEGOS, "description": "cs2 (Counter-Strike 2) o smite2 (SMITE 2)."}
    return types.Tool(
        function_declarations=[
            types.FunctionDeclaration(
                name="buscar_partidas",
                description=(
                    "Partidas de un jugador en un juego, filtradas por mapa (CS2) o dios (SMITE 2), resultado, días o "
                    "las últimas n. Devuelve el resumen de esas partidas (winrate, K/D, medias...), la media del resto "
                    "del equipo con el mismo filtro y las más recientes (hasta 10), con mapa o dios, resultado, K/D/A y "
                    "compañeros. Para preguntas sobre un mapa o dios concreto, unas fechas, solo victorias o derrotas "
                    "o partidas concretas."
                ),
                parameters_json_schema={
                    "type": "object",
                    "properties": {
                        "jugador": jugador,
                        "juego": juego,
                        "mapa_o_dios": {"type": "string", "description": "Mapa (Mirage, Nuke...) o dios (Zeus, Loki...)."},
                        "resultado": {"type": "string", "enum": ["victoria", "derrota"]},
                        "desde": {"type": "string", "description": "Primer día que cuenta, AAAA-MM-DD."},
                        "hasta": {"type": "string", "description": "Último día que cuenta (incluido), AAAA-MM-DD."},
                        "ultimas": {
                            "type": "integer",
                            "minimum": 1,
                            "maximum": 100,
                            "description": "Solo las n más recientes de las que pasan los demás filtros.",
                        },
                    },
                    "required": ["jugador", "juego"],
                },
            ),
            types.FunctionDeclaration(
                name="desglose",
                description="Partidas, victorias, winrate y K/D de un jugador por mapa (CS2) o por dios (SMITE 2).",
                parameters_json_schema={
                    "type": "object",
                    "properties": {"jugador": jugador, "juego": juego, "periodo": _periodo()},
                    "required": ["jugador", "juego"],
                },
            ),
            types.FunctionDeclaration(
                name="comparar",
                description="Dos jugadores del equipo cara a cara en un juego, métrica a métrica.",
                parameters_json_schema={
                    "type": "object",
                    "properties": {"jugador_a": jugador, "jugador_b": jugador, "juego": juego, "periodo": _periodo()},
                    "required": ["jugador_a", "jugador_b", "juego"],
                },
            ),
            types.FunctionDeclaration(
                name="sinergias",
                description=(
                    "Con qué compañeros del equipo gana más o menos un jugador (partidas en el mismo bando, con su "
                    "winrate con y sin cada uno) y cómo le va solo."
                ),
                parameters_json_schema={
                    "type": "object",
                    "properties": {"jugador": jugador, "juego": juego, "periodo": _periodo()},
                    "required": ["jugador", "juego"],
                },
            ),
            types.FunctionDeclaration(
                name="demos",
                description=(
                    "Lo que dicen las rondas de las demos analizadas de CS2 de un jugador: rating, KAST, duelos de "
                    "apertura, trades, asistencias de flash, CT y T, rondas ganadas según la compra y dónde muere en "
                    "cada mapa (y cuántas veces sin trade), con la media del resto del equipo. Solo CS2."
                ),
                parameters_json_schema={
                    "type": "object",
                    "properties": {"jugador": jugador, "periodo": _periodo()},
                    "required": ["jugador"],
                },
            ),
        ]
    )


def _objeto(datos: object) -> dict:
    if not isinstance(datos, dict):
        raise api_ttcl.ApiError("la API devolvió algo inesperado")
    return datos


def _juego(juego: object) -> str:
    if juego not in JUEGOS:
        raise ValueError(f"Juego desconocido: {juego}. Tiene que ser cs2 o smite2.")
    return str(juego)


def _periodo_api(periodo: object) -> str | None:
    """El periodo para la API; "todo" (o nada) no hace falta mandarlo."""
    if periodo in (None, "", "todo"):
        return None
    if periodo not in PERIODOS:
        raise ValueError(f"Periodo desconocido: {periodo}. Tiene que ser 7d, 30d o todo.")
    return str(periodo)


class Herramientas:
    """Las herramientas de una pregunta: las declaraciones para Gemini, el límite de consultas y quién las ejecuta."""

    def __init__(self, p: PeticionChat, maximo: int) -> None:
        self.maximo = maximo
        self.usadas: list[str] = []
        self._slugs: dict[str, str] = {}
        for j in p.equipo:
            self._slugs[normalizar(j.nombre)] = j.slug
            self._slugs[normalizar(j.slug)] = j.slug
        self.tool = declaraciones([j.nombre for j in p.equipo])

    def ejecutar(self, nombre: str, args: dict) -> dict:
        """Lo que se le devuelve a Gemini: {"resultado": datos de la API} o {"error": por qué no se pudo}."""
        self.usadas.append(nombre)
        funciones = {
            "buscar_partidas": self._buscar_partidas,
            "desglose": self._desglose,
            "comparar": self._comparar,
            "sinergias": self._sinergias,
            "demos": self._demos,
        }
        if nombre not in funciones:
            return {"error": f"No existe la herramienta {nombre}."}
        try:
            return {"resultado": funciones[nombre](**args)}
        except (api_ttcl.ApiError, ValueError, TypeError) as err:
            log.info("Consulta %s(%s) sin datos: %s", nombre, args, err)
            return {"error": str(err)}
        except Exception as err:  # noqa: BLE001 - una consulta que falla no puede tumbar la respuesta
            log.warning("Consulta %s(%s) rota: %s", nombre, args, err)
            return {"error": "La consulta ha fallado."}

    def _slug(self, jugador: object) -> str:
        slug = self._slugs.get(normalizar(str(jugador or "")))
        if not slug:
            raise ValueError(f"No hay nadie en el equipo que se llame {jugador}.")
        return slug

    def _buscar_partidas(
        self, jugador, juego, mapa_o_dios=None, resultado=None, desde=None, hasta=None, ultimas=None
    ) -> dict:
        datos = _objeto(
            api_ttcl.consultar(
                f"/api/jugadores/{self._slug(jugador)}/consulta",
                {
                    "juego": _juego(juego),
                    "clave": mapa_o_dios or None,
                    "resultado": resultado or None,
                    "desde": desde or None,
                    "hasta": hasta or None,
                    "ultimas": int(ultimas) if ultimas is not None else None,
                    "limite": MAX_PARTIDAS,
                },
            )
        )
        partidas = [{k: v for k, v in pa.items() if k not in SOBRA_EN_PARTIDA} for pa in datos.get("partidas", [])]
        return {"resumen": datos.get("resumen"), "equipo": datos.get("equipo"), "partidas": partidas}

    def _desglose(self, jugador, juego, periodo=None) -> dict:
        datos = _objeto(
            api_ttcl.consultar(
                f"/api/jugadores/{self._slug(jugador)}/juegos/{_juego(juego)}", {"periodo": _periodo_api(periodo)}
            )
        )
        return {"partidas": (datos.get("resumen") or {}).get("partidas"), "desglose": datos.get("desglose", [])}

    def _comparar(self, jugador_a, jugador_b, juego, periodo=None) -> dict:
        datos = _objeto(
            api_ttcl.consultar(
                "/api/comparar",
                {
                    "a": self._slug(jugador_a),
                    "b": self._slug(jugador_b),
                    "juego": _juego(juego),
                    "periodo": _periodo_api(periodo),
                },
            )
        )
        return {
            "a": (datos.get("a") or {}).get("nombre"),
            "b": (datos.get("b") or {}).get("nombre"),
            "partidas_a": (datos.get("resumenA") or {}).get("partidas", 0),
            "partidas_b": (datos.get("resumenB") or {}).get("partidas", 0),
            "filas": datos.get("filas", []),
        }

    def _demos(self, jugador, periodo=None) -> dict:
        datos = _objeto(
            api_ttcl.consultar(
                f"/api/jugadores/{self._slug(jugador)}/demos", {"periodo": _periodo_api(periodo)}
            )
        )
        mapas = [{**m, "zonas": (m.get("zonas") or [])[:MAX_ZONAS]} for m in datos.get("mapas", [])]
        return {**datos, "mapas": mapas}

    def _sinergias(self, jugador, juego, periodo=None) -> dict:
        return _objeto(
            api_ttcl.consultar(
                f"/api/jugadores/{self._slug(jugador)}/sinergias",
                {"juego": _juego(juego), "periodo": _periodo_api(periodo)},
            )
        )
