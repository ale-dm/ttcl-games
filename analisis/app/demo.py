"""Lectura de una demo de CS2 con demoparser2: de sus tablas saca los eventos que necesita rondas.py.

demoparser2 devuelve tablas de pandas con un nombre de columna por campo del evento; los de cada jugador del evento
van con prefijo (user_ la víctima, attacker_ el que mata). Aquí todo se lee con cuidado (columnas que faltan, steamids
como número o como texto, lados como 2/3 o "T"/"CT"), porque el formato cambia entre versiones.

**Sin probar con una demo real**: lo que sale de aquí se ha comprobado con tablas escritas a mano con las columnas que
documenta demoparser2 (ver tests/test_demo.py).
"""

import math
from collections.abc import Iterable
from typing import Any, Protocol

from .rondas import Dano, Equipo, Eventos, FinRonda, Lado, Muerte, limites

PROPS_MUERTE = ["X", "Y", "last_place_name", "team_num"]
PROPS_DANO = ["team_num"]
OTROS = ["is_warmup_period"]


class Parser(Protocol):
    """Lo que se usa de demoparser2.DemoParser (así los tests le pasan uno de mentira)."""

    def parse_header(self) -> dict[str, str]: ...

    def parse_player_info(self) -> Any: ...

    def parse_event(self, event_name: str, *, player: Any = None, other: Any = None) -> Any: ...

    def parse_ticks(self, wanted_props: Any, *, players: Any = None, ticks: Any = None) -> Any: ...


def filas(tabla: Any) -> list[dict[str, Any]]:
    """Una tabla de pandas (o una lista de dicts) como lista de dicts. Vacía si no hay tabla."""
    if tabla is None:
        return []
    if isinstance(tabla, list):
        return tabla
    if getattr(tabla, "empty", False):
        return []
    return tabla.to_dict("records")


def nulo(valor: Any) -> bool:
    return valor is None or (isinstance(valor, float) and math.isnan(valor))


def steamid(valor: Any) -> str | None:
    """Steamid como texto ("76561198..."), venga como número o como texto; None si no hay (el mundo). Un decimal no
    guarda un steamid entero (se come las últimas cifras): demoparser2 los da como texto o como entero."""
    if nulo(valor) or valor in ("", "0", 0):
        return None
    if isinstance(valor, float):
        return str(int(valor))
    return str(valor).split(".")[0]


def lado(valor: Any) -> Lado | None:
    """2 o "T"/"TERRORIST" → T; 3 o "CT" → CT; lo demás (espectador, sin dato), None."""
    if nulo(valor):
        return None
    texto = str(valor).strip().upper()
    if texto in ("2", "2.0", "T", "TERRORIST", "TERRORISTS"):
        return "T"
    if texto in ("3", "3.0", "CT", "COUNTER-TERRORIST", "COUNTER_TERRORIST", "COUNTERTERRORIST"):
        return "CT"
    return None


def entero(valor: Any) -> int | None:
    if nulo(valor):
        return None
    try:
        return int(float(valor))
    except (TypeError, ValueError):
        return None


def numero(valor: Any) -> float | None:
    if nulo(valor):
        return None
    try:
        return round(float(valor), 1)
    except (TypeError, ValueError):
        return None


def texto(valor: Any) -> str | None:
    return None if nulo(valor) or str(valor).strip() == "" else str(valor).strip()


def _evento(parser: Parser, nombre: str, **kwargs: Any) -> list[dict[str, Any]]:
    """Las filas de un evento, sin las del calentamiento. Sin ese evento en la demo, ninguna."""
    try:
        lista = filas(parser.parse_event(nombre, **kwargs))
    except Exception:  # noqa: BLE001 - demoparser2 lanza excepciones propias si el evento no existe
        return []
    return [f for f in lista if not f.get("is_warmup_period")]


def _inicio_partida(parser: Parser) -> int:
    """Tick del último "empieza la partida" (FACEIT reinicia tras el calentamiento); 0 si no hay."""
    anuncios = _evento(parser, "round_announce_match_start") or _evento(parser, "begin_new_match")
    return max((entero(f.get("tick")) or 0 for f in anuncios), default=0)


def leer(parser: Parser) -> tuple[str | None, Eventos]:
    """Mapa y eventos de la partida (lo de antes de que empiece de verdad, fuera)."""
    cabecera = parser.parse_header() or {}
    mapa = texto(cabecera.get("map_name"))
    desde = _inicio_partida(parser)

    def despues(lista: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
        return [f for f in lista if (entero(f.get("tick")) or 0) > desde]

    fines = [
        FinRonda(tick=entero(f["tick"]) or 0, ganador=lado(f.get("winner")), motivo=texto(f.get("reason")))
        for f in despues(_evento(parser, "round_end", other=OTROS))
        if lado(f.get("winner")) is not None
    ]
    fines.sort(key=lambda f: f.tick)
    inicios = sorted(entero(f.get("tick")) or 0 for f in despues(_evento(parser, "round_freeze_end", other=OTROS)))

    muertes = [
        Muerte(
            tick=entero(f.get("tick")) or 0,
            victima=steamid(f.get("user_steamid")),
            atacante=steamid(f.get("attacker_steamid")),
            asistente=steamid(f.get("assister_steamid")),
            flash=not nulo(f.get("assistedflash")) and bool(f.get("assistedflash")),
            lado_victima=lado(f.get("user_team_num")),
            lado_atacante=lado(f.get("attacker_team_num")),
            x=numero(f.get("user_X")),
            y=numero(f.get("user_Y")),
            zona=texto(f.get("user_last_place_name")),
        )
        for f in despues(_evento(parser, "player_death", player=PROPS_MUERTE, other=OTROS))
    ]
    danos = [
        Dano(
            tick=entero(f.get("tick")) or 0,
            atacante=steamid(f.get("attacker_steamid")),
            victima=steamid(f.get("user_steamid")),
            dano=entero(f.get("dmg_health")) or 0,
            arma=texto(f.get("weapon")),
            lado_atacante=lado(f.get("attacker_team_num")),
            lado_victima=lado(f.get("user_team_num")),
        )
        for f in despues(_evento(parser, "player_hurt", player=PROPS_DANO, other=OTROS))
    ]
    eventos = Eventos(fines=fines, inicios=inicios, muertes=muertes, danos=danos)
    eventos.equipos = _equipos(parser, eventos)
    return mapa, eventos


def _equipos(parser: Parser, eventos: Eventos) -> list[Equipo]:
    """Lado y valor del equipo de cada jugador al acabar la congelación de cada ronda."""
    ticks: dict[int, int] = {}
    for i, (inicio, _) in enumerate(limites(eventos.fines, eventos.inicios), start=1):
        if inicio is not None:
            ticks[inicio] = i
    if not ticks:
        return []
    try:
        lista = filas(parser.parse_ticks(["team_num", "current_equip_value"], ticks=sorted(ticks)))
    except Exception:  # noqa: BLE001
        return []
    equipos = []
    for f in lista:
        ronda = ticks.get(entero(f.get("tick")) or -1)
        jugador = steamid(f.get("steamid"))
        if ronda and jugador:
            equipos.append(
                Equipo(ronda=ronda, jugador=jugador, lado=lado(f.get("team_num")), valor=entero(f.get("current_equip_value")))
            )
    return equipos


def jugadores_de(parser: Parser) -> list[tuple[str, str | None]]:
    """Steamid y nombre de cada uno de la partida."""
    try:
        lista = filas(parser.parse_player_info())
    except Exception:  # noqa: BLE001
        return []
    return [(s, texto(f.get("name"))) for f in lista if (s := steamid(f.get("steamid")))]
