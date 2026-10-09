from fastapi.testclient import TestClient

from app.main import app

cliente = TestClient(app)

RESUMEN = {
    "juego": "cs2",
    "partidas": 30,
    "victorias": 11,
    "derrotas": 19,
    "winrate": 36.7,
    "kd": 1.25,
    "killsMedia": 20.1,
    "muertesMedia": 16.1,
    "asistenciasMedia": 4.0,
    "datosMedios": {"adr": 84.0, "hs_pct": 47.0},
    "forma": "VDDVVVDDDD",
    "ultimaPartida": "2026-10-08T10:00:00Z",
}


def test_health():
    r = cliente.get("/health")
    assert r.status_code == 200
    assert r.json()["ok"] is True


def test_insights_camel_case():
    r = cliente.post(
        "/v1/insights",
        json={"lang": "es", "jugador": {"slug": "j1", "nombre": "Jugador 1"}, "juego": "cs2", "resumen": RESUMEN},
    )
    assert r.status_code == 200
    insights = r.json()["insights"]
    assert insights[0]["id"] == "kd_sin_victorias"
    assert {"id", "nivel", "titulo", "texto", "consejo", "barras", "formato", "metrica"} <= insights[0].keys()


def test_insights_con_rol():
    resumen = {**RESUMEN, "winrate": 50.0, "kd": 1.0, "killsMedia": 12.0, "asistenciasMedia": 9.0}
    equipo = {"jugadores": 2, "winrate": 50.0, "kd": 1.0, "killsMedia": 18.0, "asistenciasMedia": 5.0}
    cuerpo = {"lang": "es", "jugador": {"slug": "j2", "nombre": "J2"}, "juego": "cs2", "resumen": resumen, "equipo": equipo}

    def ids(peticion: dict) -> list[str]:
        r = cliente.post("/v1/insights", json=peticion)
        assert r.status_code == 200
        return [i["id"] for i in r.json()["insights"]]

    assert "debil_kills_media" in ids(cuerpo)
    assert ids({**cuerpo, "rol": None}) == ids(cuerpo)  # la API manda null si no lo ha dicho
    con_rol = ids({**cuerpo, "rol": "soporte"})
    assert "debil_kills_media" not in con_rol
    assert "fuerte_asistencias_media" in con_rol


def test_insights_con_sinergias_en_camel_case():
    fila = {"slug": "j3", "nombre": "Jugador 3", "partidas": 20, "victorias": 13, "winrate": 65.0, "kd": 1.1,
            "partidasSin": 10, "winrateSin": 38.0}
    r = cliente.post(
        "/v1/insights",
        json={"lang": "es", "jugador": {"slug": "j1", "nombre": "Jugador 1"}, "juego": "cs2", "resumen": RESUMEN,
              "sinergias": {"solo": None, "companeros": [fila]}},
    )
    assert r.status_code == 200
    bueno = next(i for i in r.json()["insights"] if i["id"] == "companero_bueno")
    assert bueno["texto"] == "Con Jugador 3 ganas el 65,0 % de 20 partidas; sin Jugador 3, el 38,0 %."


def test_insights_con_sesiones_en_camel_case():
    def fila(clave, partidas, victorias, winrate, resto, winrate_resto):
        return {"clave": clave, "partidas": partidas, "victorias": victorias, "winrate": winrate, "kd": 1.0,
                "partidasResto": resto, "winrateResto": winrate_resto}

    sesiones = {
        "sesiones": 18,
        "partidasPorSesion": 2.8,
        "porOrden": [fila("1", 18, 10, 55.6, 32, 46.9), fila("2", 14, 8, 57.1, 36, 47.2), fila("3+", 18, 6, 33.3, 32, 56.3)],
        "trasResultado": [],
        "porFranja": [fila("tarde", 12, 8, 66.7, 38, 42.1)],
    }
    r = cliente.post(
        "/v1/insights",
        json={"lang": "es", "jugador": {"slug": "j3", "nombre": "Jugador 3"}, "juego": "cs2", "resumen": RESUMEN,
              "sesiones": sesiones},
    )
    assert r.status_code == 200
    por_id = {i["id"]: i for i in r.json()["insights"]}
    assert por_id["tilt_sesion"]["texto"] == (
        "A partir de la 3ª partida seguida ganas el 33,3 % (18 partidas); en las dos primeras, el 56,3 %."
    )
    assert por_id["mejor_horario"]["titulo"] == "Rindes más por la tarde"


def test_insights_lote():
    item = {"lang": "en", "jugador": {"slug": "j1", "nombre": "Jugador 1"}, "juego": "cs2", "resumen": RESUMEN}
    r = cliente.post("/v1/insights/lote", json={"items": [item, {**item, "jugador": {"slug": "j2", "nombre": "J2"}}]})
    assert r.status_code == 200
    items = r.json()["items"]
    assert [i["slug"] for i in items] == ["j1", "j2"]
    assert items[0]["insights"][0]["titulo"] == "Your kills don't turn into wins"


def test_chat():
    r = cliente.post(
        "/v1/chat",
        json={
            "lang": "es",
            "mensajes": [{"rol": "usuario", "texto": "¿En qué tengo que mejorar?"}],
            "foco": ["j1"],
            "equipo": [{"slug": "j1", "nombre": "Jugador 1", "juegos": [{"juego": "cs2", "resumen": RESUMEN}]}],
        },
    )
    assert r.status_code == 200
    cuerpo = r.json()
    assert cuerpo["origen"] == "reglas"
    assert "Tus kills no se convierten en victorias" in cuerpo["respuesta"]
    assert cuerpo["sugerencias"]


def test_chat_valida_la_entrada():
    assert cliente.post("/v1/chat", json={"lang": "es", "mensajes": []}).status_code == 422
    assert cliente.post("/v1/chat", json={"lang": "fr", "mensajes": [{"rol": "usuario", "texto": "x"}]}).status_code == 422
