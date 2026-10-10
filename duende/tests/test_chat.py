from app import chat, gemini
from app.config import get_config
from app.modelos import Desglose, Mensaje, PeticionChat, Resumen
from app.reglas_chat import detectar_intencion, detectar_juego, detectar_periodo, insights_de

from .conftest import (
    Sinergias,
    companero,
    en_periodo,
    equipo_cs2,
    jugador,
    momento,
    nivel_faceit,
    resumen_cs2,
    seguido,
    sesiones,
)


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


def test_intencion_de_sesiones_antes_que_mejorar():
    assert detectar_intencion("¿Cuándo juego mejor?", 1) == "sesiones"  # no es "¿en qué mejorar?"
    assert detectar_intencion("¿Me tilteo?", 1) == "sesiones"
    assert detectar_intencion("¿A qué hora rindo más?", 1) == "sesiones"
    assert detectar_intencion("¿Cuántas partidas seguidas debería jugar?", 1) == "sesiones"
    assert detectar_intencion("When do I play best?", 1) == "sesiones"
    assert detectar_intencion("am I on tilt after a loss?", 1) == "sesiones"
    assert detectar_intencion("¿Cómo voy últimamente?", 1) == "racha"
    assert detectar_intencion("¿Con quién juego mejor?", 1) == "companeros"


def equipo_con_sesiones(n: int = 20, **filas):
    """Ana con sesiones: por defecto, se desinfla desde la 3ª y rinde más por la noche."""
    s = sesiones(
        n,
        por_orden=filas.get("por_orden", [momento("1", 20, 60.0, 30, 46.7), momento("2", 15, 60.0, 35, 48.6),
                                           momento("3+", 15, 33.3, 35, 60.0)]),
        tras_resultado=filas.get("tras_resultado", [momento("victoria", 16, 56.3, 34, 50.0),
                                                     momento("derrota", 14, 50.0, 36, 52.8)]),
        por_franja=filas.get("por_franja", [momento("tarde", 15, 40.0, 35, 57.1), momento("noche", 35, 57.1, 15, 40.0)]),
    )
    return [jugador("j1", "Ana", resumen_cs2(partidas=50), equipo_cs2(), sesiones=s)]


def test_cuando_juego_mejor():
    r = chat.responder(
        PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="¿Cuándo juego mejor?")], foco=["j1"],
                     equipo=equipo_con_sesiones())
    ).respuesta
    lineas = r.split("\n")
    assert lineas[0] == "Cómo te va según cuándo juegas a Counter-Strike 2, Ana (20 sesiones, 2,5 partidas de media):"
    assert lineas[2:9] == [
        "- **1ª de la sesión**: 60,0 % (12 de 20)",
        "- **2ª**: 60,0 % (9 de 15)",
        "- **3ª en adelante**: 33,3 % (5 de 15)",
        "- **Tras una victoria**: 56,3 % (9 de 16)",
        "- **Tras una derrota**: 50,0 % (7 de 14)",
        "- **Por la tarde**: 40,0 % (6 de 15)",
        "- **Por la noche**: 57,1 % (20 de 35)",
    ]
    assert "**Las sesiones largas se te atragantan.** A partir de la 3ª partida seguida ganas el 33,3 %" in r
    assert "para por hoy" in r
    assert "**Una derrota te arrastra" not in r


def test_cuando_juego_mejor_da_la_hora_aunque_en_el_panel_no_quepa():
    # K/D y asistencias muy por encima: dos fortalezas que pesan más que la hora (+20 puntos).
    franjas = [momento("tarde", 20, 60.0, 30, 40.0), momento("noche", 30, 40.0, 20, 60.0)]
    ana = equipo_con_sesiones(por_orden=[], tras_resultado=[], por_franja=franjas)[0]
    ana.juegos[0].resumen = resumen_cs2(partidas=50, kd=1.5, asistencias_media=8.0)
    assert "mejor_horario" not in [i.id for i in insights_de(ana, ana.juegos[0], "es")]

    r = chat.responder(
        PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="¿A qué hora juego mejor?")], foco=["j1"],
                     equipo=[ana])
    ).respuesta
    assert "**Rindes más por la tarde.** Por la tarde ganas el 60,0 % (20 partidas)" in r


def test_cuando_juego_mejor_con_pocas_sesiones_sin_nada_o_sin_foco():
    pocas = chat.responder(
        PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="¿Me tilteo?")], foco=["j1"],
                     equipo=equipo_con_sesiones(12))
    ).respuesta
    assert "Con 12 sesiones aún no saco conclusiones: a partir de 15" in pocas
    assert "atragantan" not in pocas

    normal = [momento("3+", 15, 50.0, 35, 52.0)]
    nada = chat.responder(
        PeticionChat(lang="en", mensajes=[Mensaje(rol="usuario", texto="When do I play best?")], foco=["j1"],
                     equipo=equipo_con_sesiones(por_orden=normal, tras_resultado=[], por_franja=[]))
    ).respuesta
    assert nada.startswith("How you do depending on when you play Counter-Strike 2, Ana (20 sessions, 2.5 matches")
    assert "- **3rd onwards**: 50.0% (8 of 15)" in nada
    assert nada.endswith("No tilt and no magic hour: you play about the same whenever you play.")

    sin_datos = jugador("j1", "Ana", resumen_cs2(), equipo_cs2())
    r = chat.responder(
        PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="¿Me tilteo?")], foco=["j1"], equipo=[sin_datos])
    )
    assert r.respuesta == "Aún no hay partidas de Counter-Strike 2 para saberlo."
    r = chat.responder(
        PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="¿Cuándo jugamos mejor?")],
                     equipo=equipo_con_sesiones())
    )
    assert r.respuesta.startswith("Dime de quién")
    assert "¿Cuándo juego mejor?" in chat.responder(
        PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="hola")], foco=["j1"], equipo=equipo_con_sesiones())
    ).sugerencias


def test_las_sesiones_llegan_a_gemini(monkeypatch):
    monkeypatch.setenv("GOOGLE_API_KEY", "clave-de-prueba")
    get_config.cache_clear()
    sistemas = []

    def falso(sistema, contenidos, temperatura=0.8, herramientas=None):
        sistemas.append(sistema)
        return "Respuesta de Gemini", "gemini-falso"

    monkeypatch.setattr(gemini, "generar", falso)
    chat.responder(
        PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="¿Me tilteo? (Gemini)")], foco=["j1"],
                     equipo=equipo_con_sesiones())
    )
    sistema = sistemas[0]
    assert '"sesiones":{"sesiones":20,"partidasPorSesion":2.5,"porOrden":[' in sistema
    assert '"clave":"3+","partidas":15,"victorias":5,"winrate":33.3' in sistema
    assert "Con menos de 15 sesiones no saques conclusiones" in sistema
    assert "Las sesiones largas se te atragantan" in sistema  # y la recomendación ya calculada


# ─── Periodos ────────────────────────────────────────────────────────────────


def test_detectar_periodo():
    assert detectar_periodo("¿Cómo voy esta semana?", None) == "7d"
    assert detectar_periodo("how did I do last week?", None) == "7d"
    assert detectar_periodo("¿Y en los últimos 30 días?", None) == "30d"
    assert detectar_periodo("¿qué tal este mes?", "7d") == "30d"  # la pregunta manda sobre la página
    assert detectar_periodo("¿Cuál es mi K/D en total?", "7d") == "todo"
    assert detectar_periodo("¿Qué hago bien?", "7d") == "7d"
    assert detectar_periodo("¿Qué hago bien?", None) is None


def equipo_con_semana():
    """Ana, en la media con todas sus partidas, va como un tiro esta semana; Bea no ha jugado estos días."""
    semana = en_periodo(
        "7d",
        resumen_cs2(partidas=8, victorias=6, derrotas=2, winrate=75.0, kd=1.4,
                    datos_medios={"adr": 90.0, "hs_pct": 30.0, "kr": 0.8, "entry_pct": 50.0, "clutch_pct": 25.0}),
        equipo_cs2(jugadores=1),
    )
    return [
        jugador("j1", "Ana", resumen_cs2(), equipo_cs2(), periodos=[semana]),
        jugador("j2", "Bea", resumen_cs2(kd=1.2, winrate=60.0), equipo_cs2()),
    ]


def preguntar_con(texto: str, equipo, foco=None, lang="es", periodo=None) -> str:
    return chat.responder(
        PeticionChat(lang=lang, mensajes=[Mensaje(rol="usuario", texto=texto)], foco=foco or [], equipo=equipo,
                     periodo=periodo)
    ).respuesta


def test_como_voy_esta_semana_frente_a_siempre():
    r = preguntar_con("¿Cómo voy esta semana?", equipo_con_semana(), foco=["j1"])
    assert r.split("\n") == [
        "**Últimos 7 días.** Ana en Counter-Strike 2: 8 partidas (6 victorias y 2 derrotas).",
        "",
        "- Winrate: **75,0 %** (con todas: 50,0 %)",
        "- K/D: **1,40** (con todas: 1,00)",
        "- ADR: **90** (con todas: 80)",
        "- % headshot: **30,0 %** (con todas: 45,0 %)",
        "",
        "Mejor que de costumbre. Sigue así.",
    ]
    en = preguntar_con("How am I doing this week?", equipo_con_semana(), foco=["j1"], lang="en")
    assert en.startswith("**Last 7 days.** Ana in Counter-Strike 2: 8 matches (6 wins and 2 losses).")


def test_con_un_periodo_los_consejos_son_de_esos_dias():
    # Con todas sus partidas, Ana va en la media; esta semana, la puntería no acompaña.
    assert "Pocos headshots" not in preguntar_con("¿En qué tengo que mejorar?", equipo_con_semana(), foco=["j1"])
    r = preguntar_con("¿En qué tengo que mejorar esta semana?", equipo_con_semana(), foco=["j1"])
    assert r.startswith("**Últimos 7 días.** Esto es lo que yo trabajaría en Counter-Strike 2, Ana:")
    assert "- **Pocos headshots.**" in r


def test_el_periodo_de_la_pagina_vale_si_la_pregunta_no_dice_otro():
    con_pagina = preguntar_con("¿Qué hago bien?", equipo_con_semana(), foco=["j1"], periodo="7d")
    assert con_pagina.startswith("**Últimos 7 días.**")
    en_total = preguntar_con("¿Qué hago bien en total?", equipo_con_semana(), foco=["j1"], periodo="7d")
    assert not en_total.startswith("**")
    assert not preguntar_con("¿Qué hago bien?", equipo_con_semana(), foco=["j1"], periodo="todo").startswith("**")


def test_sin_partidas_en_esos_dias():
    assert preguntar_con("¿Cómo voy esta semana?", equipo_con_semana(), foco=["j2"]) == (
        "**Últimos 7 días.** Bea no ha jugado en estos días."
    )
    assert preguntar_con("¿Cómo voy en smite este mes?", equipo_con_semana(), foco=["j1"]) == (
        "**Últimos 30 días.** Ana no ha jugado a SMITE 2 en estos días."
    )
    assert preguntar_con("¿Qué hago bien esta semana?", equipo_con_semana(), foco=["j2"]) == (
        "**Últimos 7 días.** Bea no tiene partidas."
    )
    sin_nadie = [jugador("j2", "Bea", resumen_cs2(), equipo_cs2())]
    assert preguntar_con("¿Cómo vamos este mes?", sin_nadie) == (
        "**Últimos 30 días.** Nadie del equipo ha jugado en estos días."
    )


def test_el_equipo_esta_semana():
    # Sin nadie en el foco, la clasificación de esos días: Bea no ha jugado, así que solo sale Ana.
    r = preguntar_con("¿Quién es el mejor esta semana?", equipo_con_semana())
    assert r.startswith("**Últimos 7 días.** Así está el equipo en Counter-Strike 2:")
    assert "**Ana** (75,0 %)" in r and "Bea" not in r
    assert preguntar_con("¿Cómo vamos esta semana?", equipo_con_semana()).startswith(
        "**Últimos 7 días.** Así está el equipo"
    )


def test_companeros_desglose_y_sesiones_siempre_con_todas():
    r = preguntar_con("¿Con quién juego mejor esta semana?", equipo_con_sinergias(), foco=["j1"])
    assert r.startswith("Esto lo miro con todas las partidas: por días solo separo los números generales.")
    assert "- **Bea**: ganas el 65,0 % (13 de 20)" in r


def test_los_ultimos_dias_llegan_a_gemini(monkeypatch):
    monkeypatch.setenv("GOOGLE_API_KEY", "clave-de-prueba")
    get_config.cache_clear()
    sistemas = []

    def falso(sistema, contenidos, temperatura=0.8, herramientas=None):
        sistemas.append(sistema)
        return "Respuesta de Gemini", "gemini-falso"

    monkeypatch.setattr(gemini, "generar", falso)
    equipo = equipo_con_semana()
    equipo[1].juegos[0].periodos = [en_periodo("30d", resumen_cs2(partidas=12, winrate=58.3))]
    chat.responder(
        PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="¿Cómo voy esta semana? (Gemini)")],
                     foco=["j1"], equipo=equipo, periodo="30d")
    )
    sistema = sistemas[0]
    assert '"periodo_seleccionado":"30d"' in sistema
    assert '"periodos":[{"periodo":"7d","resumen":{"juego":"cs2","partidas":8' in sistema  # Ana, con todo
    assert '"periodos":[{"periodo":"30d","resumen":{"juego":"cs2","partidas":12' in sistema  # Bea, solo el resumen
    assert "«periodos» trae además el resumen de los últimos 7 días" in sistema


# ─── Memoria de consejos ─────────────────────────────────────────────────────


def test_intencion_de_seguimiento():
    assert detectar_intencion("¿Ha funcionado lo que me dijiste?", 1) == "seguimiento"
    assert detectar_intencion("¿Me sirvió tu consejo?", 1) == "seguimiento"
    assert detectar_intencion("did your advice work?", 1) == "seguimiento"
    assert detectar_intencion("¿He mejorado en lo que me has dicho?", 1) == "seguimiento"  # no es "mejorar"
    assert detectar_intencion("What should I work on?", 1) == "mejorar"


def equipo_con_seguimiento():
    s = [
        seguido("debil_adr", "adr", 70.0, 15, 12, 90.0),
        seguido("debil_muertes_media", "muertes_media", 18.0, 20, 10, 20.0),
        seguido("debil_hs_pct", "hs_pct", 40.0, 3, 2, 42.0),
        seguido("debil_kd", "kd", 0.9, 2, 0, None),
    ]
    return [jugador("j1", "Ana", resumen_cs2(), equipo_cs2(), seguimiento=s)]


def test_ha_funcionado_lo_que_me_dijiste():
    r = preguntar_con("¿Ha funcionado lo que me dijiste?", equipo_con_seguimiento(), foco=["j1"])
    assert r.split("\n")[:6] == [
        "Lo que te he ido diciendo en Counter-Strike 2, Ana:",
        "",
        "- **Poco daño por ronda** (hace 15 días): ADR 70 → 90 en 12 partidas desde entonces. Funciona.",
        "- **Mueres demasiado** (hace 20 días): Muertes / partida 18,00 → 20,00 en 10 partidas desde entonces. "
        "Sigue sin mejorar.",
        "- **Pocos headshots** (hace 3 días): % headshot 40,0 % → 42,0 % en 2 partidas desde entonces. "
        "Aún es pronto para saberlo.",
        "- **Mueres más de lo que matas** (hace 2 días): K/D 0,90; aún no has jugado desde entonces.",
    ]
    assert "**Mejora en ADR.** Lo que estés haciendo, funciona" in r
    assert "**Muertes / partida sigue sin mejorar.** Toca insistir: No asomes solo" in r


def test_seguimiento_sin_consejos_sin_foco_y_sugerencias():
    sin = [jugador("j1", "Ana", resumen_cs2(), equipo_cs2())]
    assert preguntar_con("did your advice work?", sin, foco=["j1"], lang="en").startswith(
        "I don't have any Counter-Strike 2 tips of yours to review yet."
    )
    assert preguntar_con("¿Ha funcionado lo que me dijiste?", equipo_con_seguimiento()).startswith("Dime de quién")
    con = chat.responder(
        PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="hola")], foco=["j1"],
                     equipo=equipo_con_seguimiento())
    ).sugerencias
    assert con[:2] == ["¿En qué tengo que mejorar?", "¿Ha funcionado lo que me dijiste?"]
    sin_sug = chat.responder(PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="hola")], foco=["j1"],
                                          equipo=sin)).sugerencias
    assert "¿Ha funcionado lo que me dijiste?" not in sin_sug


def test_el_seguimiento_llega_a_gemini(monkeypatch):
    monkeypatch.setenv("GOOGLE_API_KEY", "clave-de-prueba")
    get_config.cache_clear()
    sistemas = []

    def falso(sistema, contenidos, temperatura=0.8, herramientas=None):
        sistemas.append(sistema)
        return "Respuesta de Gemini", "gemini-falso"

    monkeypatch.setattr(gemini, "generar", falso)
    chat.responder(
        PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="¿Ha funcionado? (Gemini)")], foco=["j1"],
                     equipo=equipo_con_seguimiento())
    )
    sistema = sistemas[0]
    assert '"insight":"debil_adr","metrica":"adr","valor":70.0' in sistema
    assert '"partidasDesde":12,"valorDesde":90.0' in sistema
    assert "«seguimiento» son los consejos que ya le diste" in sistema
    assert "Mejora en ADR" in sistema  # y la recomendación ya calculada


# ─── Nivel de FACEIT (P8) ────────────────────────────────────────────────────


def equipo_con_nivel():
    ana = nivel_faceit(
        adr=(82.0, 38.0), hs_pct=(44.0, 81.0), muertes_media=(17.0, 90.0), entry_pct=(47.0, None)
    )
    return [
        jugador("j1", "Ana", resumen_cs2(), equipo_cs2(), nivel=ana),
        jugador("j2", "Bea", resumen_cs2(), equipo_cs2(), nivel=nivel_faceit(nivel=8, elo=1802)),
    ]


def preguntar_a(equipo, texto: str, foco=(), lang="es", periodo=None) -> chat.RespuestaChat:
    return chat.responder(
        PeticionChat(lang=lang, mensajes=[Mensaje(rol="usuario", texto=texto)], foco=list(foco), equipo=equipo,
                     periodo=periodo)
    )


def test_intencion_de_nivel():
    assert detectar_intencion("¿Cómo voy para mi nivel?", 1) == "nivel"
    assert detectar_intencion("¿Quién tiene más ELO?", 0) == "nivel"
    assert detectar_intencion("How am I doing for my FACEIT level?", 1) == "nivel"
    assert detectar_intencion("¿Qué tengo que mejorar para subir de nivel?", 1) == "mejorar"


def test_como_voy_para_mi_nivel():
    r = preguntar_a(equipo_con_nivel(), "¿Cómo voy para mi nivel?", foco=["j1"])
    assert r.intencion == "nivel"
    assert r.respuesta.startswith(
        "**Ana · nivel 6 de FACEIT (1290 ELO)**. Comparado con 200 partidas de jugadores de ese nivel:"
    )
    assert "- ADR: **80** · nivel 6: 82 · mejor que el 38 % de las partidas" in r.respuesta
    # En las muertes, mejor es tener menos: muere más que en el 90 % de las partidas.
    assert "- Muertes / partida: **18,00** · nivel 6: 17,00 · mejor que el 10 % de las partidas" in r.respuesta
    # Las entradas salen del total del nivel: sin percentil.
    assert "- Éxito de entrada: **50,0 %** · nivel 6: 47,0 %\n" in r.respuesta + "\n"
    assert r.respuesta.endswith("Donde más destaca para su nivel: % headshot. Donde más le queda: Muertes / partida.")

    en = preguntar_a(equipo_con_nivel(), "How am I doing for my level?", foco=["j1"], lang="en")
    assert "**Ana · FACEIT level 6 (1,290 ELO)**" in en.respuesta
    assert "- ADR: **80** · level 6: 82 · better than 38% of matches" in en.respuesta


def test_niveles_del_equipo_y_sin_nivel():
    equipo = preguntar_a(equipo_con_nivel(), "¿Quién tiene más nivel?")
    assert equipo.respuesta == (
        "Niveles de FACEIT del equipo:\n\n- Bea: nivel 8 (1802 ELO)\n- Ana: nivel 6 (1290 ELO)"
    )
    # Sin nivel (aún no se ha sincronizado con FACEIT) o en SMITE 2.
    assert "Aún no sé el nivel de FACEIT de Ana" in preguntar("¿Cómo voy para mi nivel?", foco=["j1"]).respuesta
    assert "Aún no sé el nivel de FACEIT de nadie" in preguntar("¿Quién tiene más ELO?").respuesta
    smite = [jugador("j4", "Dani", Resumen(juego="smite2", partidas=20))]
    assert "El nivel solo lo sé de FACEIT (CS2)" in preguntar_a(smite, "¿Y mi nivel?", foco=["j4"]).respuesta
    # Con nivel pero sin partidas suficientes de jugadores de ese nivel.
    poco = [jugador("j1", "Ana", resumen_cs2(), nivel=nivel_faceit())]
    assert "Aún no tengo partidas suficientes" in preguntar_a(poco, "¿Cómo voy para mi nivel?", foco=["j1"]).respuesta


def test_nivel_con_periodo_sugerencias_y_gemini(monkeypatch):
    # Con un periodo, se avisa de que es con todas las partidas (la comparación con el nivel se hace con todas).
    semana = preguntar_a(equipo_con_nivel(), "¿Cómo voy para mi nivel esta semana?", foco=["j1"])
    assert semana.respuesta.startswith("Esto lo miro con todas las partidas")
    # Se sugiere preguntarlo solo si se sabe su nivel.
    assert "¿Cómo voy para mi nivel?" in semana.sugerencias
    assert "¿Cómo voy para mi nivel?" not in preguntar("hola", foco=["j1"]).sugerencias

    monkeypatch.setenv("GOOGLE_API_KEY", "clave-de-prueba")
    get_config.cache_clear()
    sistemas = []

    def falso(sistema, contenidos, temperatura=0.8, herramientas=None):
        sistemas.append(sistema)
        return "Respuesta de Gemini", "gemini-falso"

    monkeypatch.setattr(gemini, "generar", falso)
    preguntar_a(equipo_con_nivel(), "¿Cómo voy para mi nivel? (Gemini)", foco=["j1"])
    sistema = sistemas[0]
    assert "«nivel» es su nivel de FACEIT" in sistema
    assert '"metrica":"adr","referencia":82.0,"percentil":38.0' in sistema
    assert '"nivel":{"nivel":8,"elo":1802}' in sistema  # del resto, solo el nivel y el ELO


# ─── Consultas a la API (P9) ─────────────────────────────────────────────────


def test_con_gemini_y_la_api_configurada_puede_consultar(monkeypatch):
    monkeypatch.setenv("GOOGLE_API_KEY", "clave-de-prueba")
    monkeypatch.setenv("API_URL", "http://api:8080")
    monkeypatch.setenv("DUENDE_MAX_CONSULTAS", "3")
    get_config.cache_clear()
    recibido = {}

    def falso(sistema, contenidos, temperatura=0.8, herramientas=None):
        recibido.update(sistema=sistema, herramientas=herramientas)
        return "Respuesta de Gemini", "gemini-falso"

    monkeypatch.setattr(gemini, "generar", falso)
    peticion = PeticionChat(lang="es", mensajes=[Mensaje(rol="usuario", texto="¿Cómo voy en Mirage? (P9)")],
                            foco=["j1"], equipo=equipo(), hoy="2026-10-10")
    assert chat.responder(peticion).origen == "gemini"
    h = recibido["herramientas"]
    assert h.maximo == 3
    assert h.tool.function_declarations[0].parameters_json_schema["properties"]["jugador"]["enum"] == ["Ana", "Bea"]
    assert "usa las herramientas: consultan la API del equipo" in recibido["sistema"]
    assert '"hoy":"2026-10-10"' in recibido["sistema"]

    # Sin API_URL (o con 0 consultas), Gemini contesta solo con los resúmenes.
    monkeypatch.setenv("API_URL", "")
    get_config.cache_clear()
    chat.responder(peticion.model_copy(update={"mensajes": [Mensaje(rol="usuario", texto="¿Y en Nuke? (P9)")]}))
    assert recibido["herramientas"] is None
    assert "usa las herramientas" not in recibido["sistema"]


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

    def falso(sistema, contenidos, temperatura=0.8, herramientas=None):
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

    def falso(sistema, contenidos, temperatura=0.8, herramientas=None):
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

    def falso(sistema, contenidos, temperatura=0.8, herramientas=None):
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


def test_la_respuesta_dice_de_que_iba_la_pregunta(monkeypatch):
    # Con reglas, lo que han entendido; "ayuda" si no la entienden (lo que más interesa al revisar las valoraciones).
    assert preguntar("¿En qué tengo que mejorar?", foco=["j1"]).intencion == "mejorar"
    assert preguntar("¿Con quién juego mejor?", foco=["j1"]).intencion == "companeros"
    assert preguntar("¿Qué tal el tiempo?", foco=["j1"]).intencion == "ayuda"

    # Con Gemini, la misma lectura de la pregunta, también si la respuesta sale de la caché.
    monkeypatch.setenv("GOOGLE_API_KEY", "clave-de-prueba")
    get_config.cache_clear()
    monkeypatch.setattr(gemini, "generar", lambda *_args, **_kwargs: ("Respuesta de Gemini", "gemini-falso"))
    r1 = preguntar("¿Cuándo juego mejor? (intención)", foco=["j1"])
    r2 = preguntar("¿Cuándo juego mejor? (intención)", foco=["j1"])
    assert (r1.origen, r1.intencion) == ("gemini", "sesiones")
    assert r2.intencion == "sesiones"


def test_si_gemini_falla_responden_las_reglas(monkeypatch):
    monkeypatch.setenv("GOOGLE_API_KEY", "clave-de-prueba")
    get_config.cache_clear()

    def falla(*_args, **_kwargs):
        raise gemini.GeminiError("cuota", "sin cuota")

    monkeypatch.setattr(gemini, "generar", falla)
    r = preguntar("¿En qué tengo que mejorar? (sin cuota)", foco=["j1"])
    assert r.origen == "reglas"
    assert "Tus kills no se convierten en victorias" in r.respuesta
