"""Lectura de una demo con un demoparser2 de mentira (tablas como las que documenta demoparser2) y el endpoint."""

import gzip
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from app import demo, main
from app.config import Config, get_config
from app.modelos import JugadorBuscado

A, B, C = 76561198000000001, 76561198000000002, 76561198000000003
NAN = float("nan")


class ParserFalso:
    """Las tablas de una partida de dos rondas, con calentamiento y un reinicio antes de empezar."""

    def __init__(self) -> None:
        self.pedidos: list[tuple] = []

    def parse_header(self):
        return {"map_name": "de_mirage", "server_name": "FACEIT"}

    def parse_player_info(self):
        return [
            {"steamid": str(A), "name": "demo_uno", "team_number": 2},
            {"steamid": str(B), "name": "Demo_Dos", "team_number": 2},
            {"steamid": str(C), "name": "rival", "team_number": 3},
        ]

    def parse_event(self, event_name, *, player=None, other=None):
        self.pedidos.append((event_name, tuple(player or ()), tuple(other or ())))
        tablas = {
            "round_announce_match_start": [{"tick": 500}],
            "round_end": [
                {"tick": 300, "winner": "CT", "reason": "ct_killed", "is_warmup_period": True},
                {"tick": 400, "winner": 3, "reason": "game_commencing"},
                {"tick": 2000, "winner": "T", "reason": "t_killed"},
                {"tick": 3000, "winner": 3, "reason": "bomb_defused"},
                {"tick": 3500, "winner": NAN, "reason": NAN},
            ],
            "round_freeze_end": [{"tick": 600}, {"tick": 2100}],
            "player_death": [
                # En el calentamiento: no cuenta.
                {"tick": 200, "user_steamid": str(C), "attacker_steamid": str(A), "is_warmup_period": True},
                {
                    "tick": 700, "user_steamid": C, "attacker_steamid": A, "assister_steamid": NAN,
                    "assistedflash": NAN, "user_team_num": 3, "attacker_team_num": 2, "user_X": -120.456,
                    "user_Y": 300.0, "user_last_place_name": "TopofMid", "is_warmup_period": False,
                },
                {
                    "tick": 2200, "user_steamid": str(A), "attacker_steamid": str(C), "assister_steamid": None,
                    "assistedflash": False, "user_team_num": 3, "attacker_team_num": 2, "user_X": 10.0,
                    "user_Y": 20.0, "user_last_place_name": "BombsiteA", "is_warmup_period": False,
                },
            ],
            "player_hurt": [
                {"tick": 690, "attacker_steamid": str(A), "user_steamid": str(C), "dmg_health": 100,
                 "weapon": "ak47", "attacker_team_num": 2, "user_team_num": 3},
            ],
        }
        return tablas.get(event_name, [])

    def parse_ticks(self, wanted_props, *, players=None, ticks=None):
        self.pedidos.append(("ticks", tuple(wanted_props), tuple(ticks or ())))
        filas = []
        for tick, lado_equipo, valor in ((600, 2, 850), (2100, 3, 4700)):
            filas += [
                {"tick": tick, "steamid": A, "team_num": lado_equipo, "current_equip_value": valor},
                {"tick": tick, "steamid": B, "team_num": lado_equipo, "current_equip_value": valor - 100},
                {"tick": tick, "steamid": C, "team_num": 5 - lado_equipo, "current_equip_value": 1000},
            ]
        return filas


JUGADORES = [
    JugadorBuscado(id="j1", steam_id=str(A), nick="demo_uno"),
    # Sin steamid: se le encuentra por el nick, sin distinguir mayúsculas.
    JugadorBuscado(id="j2", nick="demo_dos"),
    # No estaba en la partida.
    JugadorBuscado(id="j3", steam_id="1", nick="demo_tres"),
]


def test_conversiones():
    assert demo.steamid(7.6561198e16) == "76561198000000000"
    assert demo.steamid("76561198000000001") == "76561198000000001"
    assert demo.steamid(NAN) is None and demo.steamid(0) is None
    assert demo.lado(2) == "T" and demo.lado("CT") == "CT" and demo.lado("TERRORIST") == "T"
    assert demo.lado(1) is None and demo.lado(NAN) is None


def test_lee_la_partida_sin_calentamiento_ni_reinicios():
    parser = ParserFalso()
    mapa, eventos = demo.leer(parser)
    assert mapa == "de_mirage"
    assert [(f.tick, f.ganador) for f in eventos.fines] == [(2000, "T"), (3000, "CT")]
    assert eventos.inicios == [600, 2100]
    assert len(eventos.muertes) == 2
    m = eventos.muertes[0]
    assert (m.victima, m.atacante, m.lado_victima, m.zona, m.x) == (str(C), str(A), "CT", "TopofMid", -120.5)
    assert not m.flash and m.asistente is None
    assert [(e.ronda, e.jugador, e.lado, e.valor) for e in eventos.equipos if e.jugador == str(A)] == [
        (1, str(A), "T", 850),
        (2, str(A), "CT", 4700),
    ]
    # Pide las posiciones de la víctima y el lado de cada uno.
    assert ("player_death", ("X", "Y", "last_place_name", "team_num"), ("is_warmup_period",)) in parser.pedidos


def test_analiza_a_los_del_equipo_que_estaban():
    r = main.analizar_parser(ParserFalso(), JUGADORES)
    assert r.mapa == "de_mirage"
    assert [(x.numero, x.ganador) for x in r.rondas] == [(1, "T"), (2, "CT")]
    assert {j.id for j in r.jugadores} == {"j1", "j2"}
    j1 = next(j for j in r.jugadores if j.id == "j1")
    assert j1.steam_id == str(A)
    assert [(f.lado, f.gano, f.kills, f.apertura, f.compra) for f in j1.rondas] == [
        ("T", True, 1, "ganada", "pistola"),
        ("CT", True, 0, "perdida", "completa"),
    ]
    assert j1.rondas[0].dano == 100
    assert j1.rondas[1].muerte_zona == "BombsiteA"
    datos = r.model_dump(by_alias=True)
    assert datos["jugadores"][0]["rondas"][0]["asistenciasFlash"] == 0
    assert datos["muertes"][0] == {"x": -120.5, "y": 300.0, "zona": "TopofMid"}


@pytest.fixture
def carpeta(tmp_path: Path, monkeypatch):
    cfg = Config(carpeta=tmp_path.resolve(), max_mb=1, timeout_s=5)
    monkeypatch.setattr(main, "get_config", lambda: cfg)
    monkeypatch.setattr(main, "crear_parser", lambda ruta: ParserFalso())
    get_config.cache_clear()
    return tmp_path


def test_endpoint_con_una_demo_de_la_carpeta(carpeta: Path):
    with gzip.open(carpeta / "1-abc.dem.gz", "wb") as f:
        f.write(b"PBDEMS2\x00" + b"\x00" * 100)
    cliente = TestClient(main.app)
    r = cliente.post(
        "/v1/analizar",
        json={"archivo": "1-abc.dem.gz", "jugadores": [{"id": "j1", "steamId": str(A), "nick": "demo_uno"}]},
    )
    assert r.status_code == 200
    datos = r.json()
    assert datos["mapa"] == "de_mirage"
    assert datos["jugadores"][0]["id"] == "j1"
    assert datos["jugadores"][0]["rondas"][0]["kast"] is True


def test_endpoint_rechaza_lo_que_no_es_una_demo(carpeta: Path):
    (carpeta / "otra.dem").write_bytes(b"no soy una demo")
    cliente = TestClient(main.app)
    assert cliente.post("/v1/analizar", json={"archivo": "otra.dem", "jugadores": []}).status_code == 422
    assert cliente.post("/v1/analizar", json={"archivo": "../fuera.dem", "jugadores": []}).status_code == 422
    assert cliente.post("/v1/analizar", json={"jugadores": []}).status_code == 422
    r = cliente.post("/v1/analizar", json={"url": "ftp://demos/x.dem", "jugadores": []})
    assert r.status_code == 422 and "http" in r.json()["error"]


def test_health():
    assert TestClient(main.app).get("/health").json() == {"ok": True}
