from app import chat, gemini
from app.config import get_config
from app.modelos import Desglose, Mensaje, PeticionChat
from app.reglas_chat import detectar_intencion, detectar_juego

from .conftest import equipo_cs2, jugador, resumen_cs2


def equipo():
    return [
        jugador("j1", "Ana", resumen_cs2(kd=1.3, winrate=37.1), equipo_cs2(kd=1.0, winrate=52.0),
                desglose=[Desglose(clave="de_nuke", partidas=6, victorias=1, winrate=16.7),
                          Desglose(clave="de_mirage", partidas=8, victorias=6, winrate=75.0)]),
        jugador("j2", "Bea", resumen_cs2(kd=0.8, winrate=55.0, forma="DDDDVVVDVD"), equipo_cs2(kd=1.15, winrate=43.0)),
    ]


def preguntar(texto: str, foco=None, lang="es") -> chat.RespuestaChat:
    return chat.responder(
        PeticionChat(lang=lang, mensajes=[Mensaje(rol="usuario", texto=texto)], foco=foco or [], equipo=equipo())
    )


def test_intenciones():
    assert detectar_intencion("¿En qué tengo que mejorar?", 1) == "mejorar"
    assert detectar_intencion("¿Qué hago bien?", 1) == "fuerte"
    assert detectar_intencion("¿Cómo voy últimamente?", 1) == "racha"
    assert detectar_intencion("¿Qué mapa se me da peor?", 1) == "desglose"
    assert detectar_intencion("¿Quién es el mejor en CS2?", 0) == "ranking"
    assert detectar_intencion("¿Quién es mejor?", 2) == "comparar"
    assert detectar_intencion("¿En qué tiene que mejorar Bea?", 2) == "mejorar"
    assert detectar_intencion("what's my k/d", 1) == "stats"
    assert detectar_intencion("hola", 0) == "hola"
    assert detectar_juego("y en smite?", None) == "smite2"
    assert detectar_juego("en el CS", "smite2") == "cs2"


def test_mejorar_usa_las_recomendaciones_del_motor():
    r = preguntar("¿En qué tengo que mejorar?", foco=["j1"])
    assert r.origen == "reglas"
    assert "Tus kills no se convierten en victorias" in r.respuesta
    assert r.respuesta.count("\n- ") >= 1
    assert "¿Qué hago bien?" in r.sugerencias


def test_el_nombre_en_la_pregunta_cambia_el_foco():
    r = preguntar("¿En qué tiene que mejorar Bea?", foco=["j1"])
    assert "Bea" in r.respuesta


def test_comparar_dos():
    r = preguntar("¿Quién es mejor?", foco=["j1", "j2"])
    assert "Ana vs Bea" in r.respuesta
    assert "→" in r.respuesta


def test_ranking_sin_foco():
    r = preguntar("¿Quién es el peor del equipo en CS2? ¿quién tiene que mejorar?")
    assert "Así está el equipo" in r.respuesta
    assert "Quien más tiene que mejorar" in r.respuesta


def test_mapas():
    r = preguntar("¿Qué mapa se me da peor?", foco=["j1"])
    assert "de_nuke" in r.respuesta and "de_mirage" in r.respuesta


def test_racha_en_ingles():
    r = preguntar("how have I been doing lately?", foco=["j2"], lang="en")
    assert "Last 10" in r.respuesta and "L L L L W" in r.respuesta


def test_con_gemini_usa_gemini_y_cachea(monkeypatch):
    monkeypatch.setenv("GOOGLE_API_KEY", "clave-de-prueba")
    get_config.cache_clear()
    llamadas = []

    def falso(sistema, contenidos, temperatura=0.8):
        llamadas.append((sistema, contenidos))
        return "Respuesta de Gemini", "gemini-falso"

    monkeypatch.setattr(gemini, "generar", falso)
    peticion = PeticionChat(
        lang="es",
        mensajes=[Mensaje(rol="duende", texto="¡Hola!"), Mensaje(rol="usuario", texto="¿En qué mejoro?")],
        foco=["j1"],
        equipo=equipo(),
    )
    r1 = chat.responder(peticion)
    r2 = chat.responder(peticion)
    assert (r1.origen, r1.modelo, r1.respuesta) == ("gemini", "gemini-falso", "Respuesta de Gemini")
    assert r2.respuesta == r1.respuesta
    assert len(llamadas) == 1  # la segunda sale de la caché
    sistema, contenidos = llamadas[0]
    assert "Tus kills no se convierten en victorias" in sistema  # recibe las recomendaciones calculadas
    assert [c.role for c in contenidos] == ["user"]  # el saludo inicial del Duende no se manda


def test_si_gemini_falla_responden_las_reglas(monkeypatch):
    monkeypatch.setenv("GOOGLE_API_KEY", "clave-de-prueba")
    get_config.cache_clear()

    def falla(*_args, **_kwargs):
        raise gemini.GeminiError("cuota", "sin cuota")

    monkeypatch.setattr(gemini, "generar", falla)
    r = preguntar("¿En qué tengo que mejorar? (sin cuota)", foco=["j1"])
    assert r.origen == "reglas"
    assert "Tus kills no se convierten en victorias" in r.respuesta
