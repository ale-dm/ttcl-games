from app import chat, gemini
from app.config import get_config
from app.modelos import Desglose, Mensaje, PeticionChat
from app.reglas_chat import detectar_intencion, detectar_juego, insights_de

from .conftest import Sinergias, companero, equipo_cs2, jugador, resumen_cs2


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


def test_intencion_de_companeros_antes_que_quien():
    assert detectar_intencion("¿Con quién juego mejor?", 1) == "companeros"
    assert detectar_intencion("¿Con quién juego mejor?", 2) == "companeros"  # no es "¿quién es mejor?"
    assert detectar_intencion("who do I play best with?", 1) == "companeros"
    assert detectar_intencion("¿Cuál es nuestro mejor dúo?", 0) == "companeros"
    assert detectar_intencion("¿Quién es mejor?", 2) == "comparar"


def equipo_con_sinergias():
    ana = Sinergias(
        solo=companero("solo", 6, 33.3, 24, 58.3).model_copy(update={"slug": None, "nombre": None}),
        companeros=[
            companero("Bea", 20, 65.0, 10, 38.0).model_copy(update={"slug": "j2"}),
            companero("Carla", 12, 41.7, 18, 61.1),
        ],
    )
    bea = Sinergias(companeros=[companero("Ana", 20, 65.0, 8, 50.0).model_copy(update={"slug": "j1"})])
    return [
        jugador("j1", "Ana", resumen_cs2(), equipo_cs2(), sinergias=ana),
        jugador("j2", "Bea", resumen_cs2(), equipo_cs2(), sinergias=bea),
    ]


def test_con_quien_juego_mejor():
    peticion = PeticionChat(
        lang="es", mensajes=[Mensaje(rol="usuario", texto="¿Con quién juego mejor?")], foco=["j1"],
        equipo=equipo_con_sinergias(),
    )
    r = chat.responder(peticion).respuesta
    lineas = r.split("\n")
    assert lineas[0] == "Con quién te va mejor en Counter-Strike 2, Ana:"
    assert "- **Bea**: ganas el 65,0 % (13 de 20); sin Bea, el 38,0 %." in lineas
    assert lineas.index("- **Bea**: ganas el 65,0 % (13 de 20); sin Bea, el 38,0 %.") < lineas.index(
        "- **Carla**: ganas el 41,7 % (5 de 12); sin Carla, el 61,1 %."
    )
    assert "- **Solo**: ganas el 33,3 % (2 de 6)." in lineas
    # Y lo que el Duende saca de ahí, con su consejo.
    assert "**Con Bea vas a otro nivel.** Buscad partidas juntos" in r
    assert "**Con Carla no termina de cuajar.**" in r


def test_con_quien_da_el_consejo_aunque_en_el_panel_no_quepa():
    # K/D y asistencias muy por encima: dos fortalezas que pesan más que el compañero (+20 puntos).
    s = Sinergias(companeros=[companero("Bea", 20, 60.0, 10, 40.0).model_copy(update={"slug": "j2"})])
    ana = jugador("j1", "Ana", resumen_cs2(kd=1.5, asistencias_media=8.0), equipo_cs2(), sinergias=s)
    assert "companero_bueno" not in [i.id for i in insights_de(ana, ana.juegos[0], "es")]

    r = chat.responder(
        PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="¿Con quién juego mejor?")], foco=["j1"],
                     equipo=[ana])
    )
    assert "**Con Bea vas a otro nivel.** Buscad partidas juntos" in r.respuesta


def test_con_quien_sin_datos_y_en_ingles():
    sin_datos = jugador("j1", "Ana", resumen_cs2(), equipo_cs2())
    r = chat.responder(
        PeticionChat(lang="en", mensajes=[Mensaje(rol="usuario", texto="who do I play best with?")], foco=["j1"],
                     equipo=[sin_datos])
    )
    assert r.respuesta == "Not enough Counter-Strike 2 matches with anyone on the team to tell yet."


def test_mejor_duo_del_equipo_sin_foco():
    r = chat.responder(
        PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="¿Cuál es nuestro mejor dúo?")],
                     equipo=equipo_con_sinergias())
    ).respuesta
    # Ana + Bea sale de las dos fichas, pero cuenta una vez.
    assert r.split("\n") == [
        "Los mejores dúos en Counter-Strike 2:",
        "",
        "- **Ana + Bea**: 65,0 % (13 de 20)",
        "- **Ana + Carla**: 41,7 % (5 de 12)",
    ]


def test_sugerencias_de_companeros():
    uno = chat.responder(PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="hola")], foco=["j1"],
                                      equipo=equipo_con_sinergias()))
    assert "¿Con quién juego mejor?" in uno.sugerencias
    todos = chat.responder(PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="hola")],
                                        equipo=equipo_con_sinergias()))
    assert "¿Cuál es nuestro mejor dúo?" in todos.sugerencias


def test_mejorar_tiene_en_cuenta_el_rol():
    r = resumen_cs2(kills_media=12.0, asistencias_media=4.0)

    def mejorar(rol):
        bea = jugador("j2", "Bea", r, equipo_cs2(), rol=rol)
        mensajes = [Mensaje(rol="usuario", texto="¿En qué tengo que mejorar?")]
        return chat.responder(PeticionChat(lang="es", mensajes=mensajes, foco=["j2"], equipo=[bea])).respuesta

    assert "Kills / partida" in mejorar(None)
    respuesta = mejorar("soporte")
    assert "en Counter-Strike 2 (rol de soporte), Bea:" in respuesta
    assert "Asistencias / partida" in respuesta
    assert "Kills / partida" not in respuesta


def test_el_rol_llega_a_gemini(monkeypatch):
    monkeypatch.setenv("GOOGLE_API_KEY", "clave-de-prueba")
    get_config.cache_clear()
    sistemas = []

    def falso(sistema, contenidos, temperatura=0.8):
        sistemas.append(sistema)
        return "Respuesta de Gemini", "gemini-falso"

    monkeypatch.setattr(gemini, "generar", falso)
    ana = jugador("j1", "Ana", resumen_cs2(), equipo_cs2(), rol="awp")
    bea = jugador("j2", "Bea", resumen_cs2(kills_media=12.0), equipo_cs2(), rol="soporte")
    chat.responder(
        PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="¿Qué tal mi rol?")], foco=["j1"], equipo=[ana, bea])
    )
    sistema = sistemas[0]
    assert '"rol":"awp"' in sistema  # el del foco
    assert '"rol":"soporte"' in sistema  # y el del resto del equipo
    assert "a un soporte o un guardián no le pidas kills" in sistema


def test_las_sinergias_llegan_a_gemini(monkeypatch):
    monkeypatch.setenv("GOOGLE_API_KEY", "clave-de-prueba")
    get_config.cache_clear()
    sistemas = []

    def falso(sistema, contenidos, temperatura=0.8):
        sistemas.append(sistema)
        return "Respuesta de Gemini", "gemini-falso"

    monkeypatch.setattr(gemini, "generar", falso)
    chat.responder(
        PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="¿Con quién juego mejor? (Gemini)")],
                     foco=["j1"], equipo=equipo_con_sinergias())
    )
    sistema = sistemas[0]
    assert '"nombre":"Bea","partidas":20' in sistema and '"winrateSin":38.0' in sistema
    assert "«winrateSin» es su winrate en el resto de partidas" in sistema
    assert "Con Bea vas a otro nivel" in sistema  # y la recomendación ya calculada


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
