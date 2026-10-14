"""Capturas de csstats (P13): leerlas con Gemini, la recomendación de la evolución y el chat."""

import base64

import pytest
from fastapi.testclient import TestClient

from app import chat, gemini, lectura
from app.config import get_config
from app.insights import fecha_corta, generar_insights
from app.main import app
from app.modelos import Evolucion, FilaEvolucion, JugadorRef, Mensaje, PeticionChat, PeticionInsights, PeticionLectura, Tramo
from app.reglas_chat import detectar_intencion

from .conftest import equipo_cs2, jugador, resumen_cs2

PNG = base64.b64encode(b"\x89PNG\r\n\x1a\n" + b"x" * 40).decode()
CLAVES = ["rating", "kd", "winrate", "partidas", "kills"]


@pytest.fixture
def con_gemini(monkeypatch):
    """Con clave de Gemini, un límite diario nuevo y un Gemini de mentira que devuelve lo que se le diga."""
    monkeypatch.setenv("GOOGLE_API_KEY", "clave-de-prueba")
    get_config.cache_clear()
    monkeypatch.setattr(lectura, "limite", chat._Limite())
    llamadas: list[dict] = []

    def preparar(respuesta: dict | Exception):
        def leer_imagen(imagen, tipo, instrucciones, esquema):
            llamadas.append({"imagen": imagen, "tipo": tipo, "esquema": esquema})
            if isinstance(respuesta, Exception):
                raise respuesta
            return respuesta, "gemini-de-prueba"

        monkeypatch.setattr(gemini, "leer_imagen", leer_imagen)
        return llamadas

    return preparar


def peticion(**cambios) -> PeticionLectura:
    return PeticionLectura(**{"imagen": PNG, "tipo": "image/png", "campos": CLAVES, **cambios})


def test_sin_clave_se_rellena_a_mano():
    r = lectura.leer(peticion())
    assert (r.origen, r.motivo, r.campos, r.mapas) == ("ninguno", "sin_clave", {}, [])


def test_lee_los_numeros_y_los_mapas(con_gemini):
    llamadas = con_gemini({
        "campos": {"rating": 0.74, "kd": 0.6, "winrate": 43, "partidas": 344, "kills": True, "inventado": 9,
                   "dano": 404607},
        "mapas": [{"mapa": "de_inferno", "partidas": 109}, {"mapa": "de_ancient", "winrate": 67},
                  {"mapa": "", "partidas": 3}, {"mapa": "de_nuke"}],
    })
    r = lectura.leer(peticion())
    assert r.origen == "gemini" and r.modelo == "gemini-de-prueba" and r.motivo is None
    # Solo las claves pedidas, y solo números (un booleano no vale).
    assert r.campos == {"rating": 0.74, "kd": 0.6, "winrate": 43.0, "partidas": 344.0}
    assert [(m.mapa, m.partidas, m.winrate) for m in r.mapas] == [("de_inferno", 109, None), ("de_ancient", None, 67.0)]
    # A Gemini le llega la imagen y un esquema con una descripción por clave.
    assert llamadas[0]["imagen"].startswith(b"\x89PNG")
    propiedades = llamadas[0]["esquema"]["properties"]["campos"]["properties"]
    assert list(propiedades) == CLAVES
    assert "HLTV RATING" in propiedades["rating"]["description"]


def test_imagen_que_no_vale_o_gemini_que_falla(con_gemini):
    con_gemini({"campos": {}})
    assert lectura.leer(peticion(tipo="image/gif")).motivo == "imagen"
    assert lectura.leer(peticion(imagen="esto no es base64!")).motivo == "imagen"
    con_gemini(gemini.GeminiError("cuota", "sin cuota"))
    r = lectura.leer(peticion())
    assert (r.origen, r.motivo) == ("ninguno", "cuota")


def test_cada_lectura_cuenta_para_el_limite_diario(con_gemini, monkeypatch):
    con_gemini({"campos": {"kd": 1.1}})
    monkeypatch.setenv("DUENDE_DAILY_LIMIT", "1")
    get_config.cache_clear()
    assert lectura.leer(peticion()).origen == "gemini"
    assert lectura.leer(peticion()).motivo == "cuota"


def test_endpoint_en_camel_case(con_gemini):
    con_gemini({"campos": {"kd": 0.6}, "mapas": [{"mapa": "de_dust2", "partidas": 98}]})
    r = TestClient(app).post("/v1/capturas/leer", json={"imagen": PNG, "tipo": "image/png", "campos": ["kd"]})
    assert r.status_code == 200
    assert r.json() == {"origen": "gemini", "modelo": "gemini-de-prueba", "motivo": None, "campos": {"kd": 0.6},
                        "mapas": [{"mapa": "de_dust2", "partidas": 98, "winrate": None}]}


def test_hay_descripcion_para_lo_que_pide_la_api():
    # Las claves de Capturas.CAMPOS en Java: si se añade una allí, hay que describirla aquí.
    claves = [
        "rating", "kd", "winrate", "hs_pct", "adr", "kast", "partidas", "victorias", "derrotas", "empates", "kills",
        "muertes", "asistencias", "headshots", "dano", "rondas", "clutch_pct", "clutch_1v1_ganados",
        "clutch_1v1_perdidos", "clutch_1v2_ganados", "clutch_1v2_perdidos", "clutch_1v3_ganados",
        "clutch_1v3_perdidos", "clutch_1v4_ganados", "clutch_1v4_perdidos", "clutch_1v5_ganados",
        "clutch_1v5_perdidos", "entry_ronda", "entry_exito", "entry_exito_t", "entry_exito_ct", "entry_intentos",
        "entry_intentos_t", "entry_intentos_ct", "premier_actual", "premier_mejor", "premier_victorias",
        "competitive_victorias", "wingman_victorias",
    ]
    assert set(lectura.DESCRIPCIONES) == set(claves)


# ─── Evolución ───────────────────────────────────────────────────────────────


def evolucion(partidas=24, kd=0.72, kd_antes=0.6, adr=58.0, adr_antes=57.0, hs=36.0, hs_antes=36.0, winrate=44.0) -> Evolucion:
    return Evolucion(
        desde="2026-10-04T20:30:00Z",
        hasta="2026-10-13T21:00:00Z",
        dias=9,
        filas=[
            FilaEvolucion(clave="kd", antes=kd_antes, despues=0.61, diferencia=0.01, mejora=1),
            FilaEvolucion(clave="adr", formato="int", antes=adr_antes, despues=57.3),
            FilaEvolucion(clave="hs_pct", formato="pct", antes=hs_antes, despues=36.0),
            FilaEvolucion(clave="winrate", formato="pct", antes=43.0, despues=43.5),
            FilaEvolucion(clave="rating", antes=0.72, despues=0.74, diferencia=0.02, mejora=1),
            FilaEvolucion(clave="partidas", tipo="total", formato="int", antes=320, despues=344, diferencia=24),
        ],
        tramo=Tramo(partidas=partidas, victorias=13, derrotas=11, winrate=winrate, kd=kd, hs_pct=hs, adr=adr),
    )


def insights(e: Evolucion | None, lang="es"):
    return generar_insights(PeticionInsights(
        lang=lang, jugador=JugadorRef(slug="j1", nombre="Ana"), juego="cs2", resumen=resumen_cs2(),
        equipo=equipo_cs2(), capturas=e,
    ))


def por_id(lista, id_):
    return next((i for i in lista if i.id == id_), None)


def test_juega_mejor_desde_la_captura():
    i = por_id(insights(evolucion()), "evolucion_mejora")
    assert i.nivel == "bien"
    assert i.titulo == "Vas a más desde el 4 de octubre"
    assert i.texto == "En las 24 partidas desde tu captura del 4 de octubre: K/D 0,72; antes llevabas 0,60."
    assert [(b.etiqueta, b.valor, b.tuyo) for b in i.barras] == [("Desde el 4 de octubre", 0.72, True), ("Antes", 0.6, False)]


def test_juega_peor_desde_la_captura_y_en_ingles():
    i = por_id(insights(evolucion(kd=0.45)), "evolucion_baja")
    assert i.nivel == "alto"  # un 25 % menos
    en = por_id(insights(evolucion(kd=0.52), lang="en"), "evolucion_baja")
    assert en.nivel == "medio"
    assert en.titulo == "A dip since October 4"


def test_con_pocas_partidas_o_sin_cambios_no_dice_nada():
    assert not [i for i in insights(evolucion(partidas=6)) if i.id.startswith("evolucion")]
    assert not [i for i in insights(evolucion(kd=0.62, adr=58.0)) if i.id.startswith("evolucion")]
    assert not [i for i in insights(None) if i.id.startswith("evolucion")]


def test_fecha_corta():
    assert fecha_corta("2026-10-04T20:30:00Z", "es") == "4 de octubre"
    assert fecha_corta("2026-01-15", "en") == "January 15"
    assert fecha_corta("ayer", "es") == "ayer"


# ─── Chat ────────────────────────────────────────────────────────────────────


def preguntar(texto: str, capturas: Evolucion | None, lang="es") -> chat.RespuestaChat:
    equipo = [jugador("j1", "Ana", resumen_cs2(), equipo_cs2())]
    equipo[0].juegos[0].capturas = capturas
    return chat.responder(
        PeticionChat(lang=lang, mensajes=[Mensaje(rol="usuario", texto=texto)], foco=["j1"], equipo=equipo)
    )


def test_intenciones_de_la_evolucion():
    for pregunta in ("¿He mejorado desde la última captura?", "¿Cómo he evolucionado?", "¿Qué tal mi progreso?",
                     "Have I improved?", "compara mis capturas de csstats"):
        assert detectar_intencion(pregunta, 1) == "evolucion", pregunta
    assert detectar_intencion("¿En qué tengo que mejorar?", 1) == "mejorar"
    assert detectar_intencion("¿Ha funcionado lo que me dijiste?", 1) == "seguimiento"


def test_he_mejorado():
    r = preguntar("¿He mejorado desde la última captura?", evolucion())
    assert r.intencion == "evolucion"
    texto = r.respuesta
    assert texto.startswith("**Ana · del 4 de octubre al 13 de octubre**")
    assert "- Partidas entre medias: **24** (13 ganadas y 11 perdidas)" in texto
    assert "- K/D: **0,72** (antes llevabas 0,60)" in texto
    assert "- Rating: 0,72 → **0,74**" in texto
    assert "**Vas a más desde el 4 de octubre.**" in texto
    assert "¿He mejorado desde la última captura?" in r.sugerencias


def test_sin_dos_capturas_lo_dice():
    r = preguntar("¿He mejorado?", None)
    assert "Aún no hay dos capturas de csstats de Ana" in r.respuesta
    assert "¿He mejorado desde la última captura?" not in r.sugerencias
    en = preguntar("Have I improved?", evolucion(kd=0.6, adr=57.0), lang="en")
    assert "Right at your usual level" in en.respuesta
