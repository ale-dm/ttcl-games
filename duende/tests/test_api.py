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


def test_insights_con_el_nivel_de_faceit_en_camel_case():
    resumen = {**RESUMEN, "winrate": 50.0, "kd": 1.0, "datosMedios": {"adr": 84.0, "hs_pct": 38.0}}
    nivel = {"nivel": 6, "elo": 1290, "partidas": 200, "metricas": [
        {"metrica": "hs_pct", "referencia": 44.0, "percentil": 20.0, "muestras": 200}]}
    r = cliente.post(
        "/v1/insights",
        json={"lang": "es", "jugador": {"slug": "j1", "nombre": "J1"}, "juego": "cs2", "resumen": resumen,
              "nivel": nivel},
    )
    assert r.status_code == 200
    hs = next(i for i in r.json()["insights"] if i["id"] == "debil_hs_pct")
    assert "nivel 6 de FACEIT anda por 44,0 %" in hs["texto"]
    assert hs["barras"][-1] == {"etiqueta": "Nivel 6", "valor": 44.0, "tuyo": False}
    # La API manda null si no sabe su nivel.
    sin = cliente.post(
        "/v1/insights",
        json={"lang": "es", "jugador": {"slug": "j1", "nombre": "J1"}, "juego": "cs2", "resumen": resumen,
              "nivel": None},
    )
    assert sin.status_code == 200


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


def test_insights_con_seguimiento_en_camel_case():
    seguimiento = [{"insight": "debil_adr", "metrica": "adr", "valor": 70.0, "dadoEn": "2026-09-24T18:00:00Z",
                    "dias": 15, "partidasDesde": 12, "valorDesde": 90.0}]
    r = cliente.post(
        "/v1/insights",
        json={"lang": "es", "jugador": {"slug": "j3", "nombre": "Jugador 3"}, "juego": "cs2", "resumen": RESUMEN,
              "seguimiento": seguimiento},
    )
    assert r.status_code == 200
    bien = next(i for i in r.json()["insights"] if i["id"] == "consejo_funciona")
    assert bien["texto"].startswith("Hace 15 días te avisé: «Poco daño por ronda».")


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
    assert cuerpo["intencion"] == "mejorar"
    assert "Tus kills no se convierten en victorias" in cuerpo["respuesta"]
    assert cuerpo["sugerencias"]


def test_chat_con_periodos_en_camel_case():
    semana = {**RESUMEN, "partidas": 6, "victorias": 5, "derrotas": 1, "winrate": 83.3}
    r = cliente.post(
        "/v1/chat",
        json={
            "lang": "es",
            "periodo": "7d",
            "mensajes": [{"rol": "usuario", "texto": "¿Cómo voy?"}],
            "foco": ["j1"],
            "equipo": [{"slug": "j1", "nombre": "Jugador 1", "juegos": [
                {"juego": "cs2", "resumen": RESUMEN, "periodos": [{"periodo": "7d", "resumen": semana, "equipo": None}]}
            ]}],
        },
    )
    assert r.status_code == 200
    assert r.json()["respuesta"].startswith(
        "**Últimos 7 días.** Jugador 1 en Counter-Strike 2: 6 partidas (5 victorias y 1 derrota)."
    )
    assert cliente.post("/v1/chat", json={"periodo": "1a", "mensajes": [{"rol": "usuario", "texto": "x"}]}).status_code == 422


def test_chat_valida_la_entrada():
    assert cliente.post("/v1/chat", json={"lang": "es", "mensajes": []}).status_code == 422
    assert cliente.post("/v1/chat", json={"lang": "fr", "mensajes": [{"rol": "usuario", "texto": "x"}]}).status_code == 422


def test_insights_con_las_demos_en_camel_case():
    """Lo que manda la API de las demos (P12), tal cual: MetricasRondas, lados, economía y mapas en camelCase."""
    metricas = {"partidas": 52, "rondas": 1129, "rating": 1.28, "kast": 76.8, "adr": 82.9, "kpr": 0.81, "dpr": 0.61,
                "aperturas": 207, "aperturasGanadas": 70, "aperturaPct": 33.8, "trades": 183, "tradesPartida": 3.52,
                "muertes": 687, "muertesTradeadas": 129, "tradeadasPct": 18.8, "flashPartida": 0.33,
                "utilidadRonda": 3.6, "winrateRondas": 51.6}
    demos = {
        "metricas": metricas,
        "equipo": {**metricas, "rating": 1.17, "tradeadasPct": 35.5},
        "lados": [{"lado": "CT", "rondas": 567, "ganadas": 288, "winrate": 50.8, "rating": 1.25, "kast": 78.8,
                   "adr": 80.5},
                  {"lado": "T", "rondas": 562, "ganadas": 295, "winrate": 52.5, "rating": 1.3, "kast": 74.7,
                   "adr": 85.3}],
        "economia": [{"compra": "forzada", "rondas": 252, "ganadas": 113, "winrate": 44.8, "kpr": 0.79}],
        "mapas": [{"mapa": "de_inferno", "partidas": 10, "muertes": 154, "sinTrade": 125,
                   "zonas": [{"zona": "Banana", "muertes": 79, "sinTrade": 73}]}],
    }
    r = cliente.post(
        "/v1/insights",
        json={"lang": "es", "jugador": {"slug": "j3", "nombre": "Jugador 3"}, "juego": "cs2", "resumen": RESUMEN,
              "rol": "entry", "demos": demos},
    )
    assert r.status_code == 200
    zona = next(i for i in r.json()["insights"] if i["id"] == "zona_sin_trade")
    assert zona["titulo"] == "En Banana te quedas solo"
    assert zona["metrica"] == "tradeadas_pct"
