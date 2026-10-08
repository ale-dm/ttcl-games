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
