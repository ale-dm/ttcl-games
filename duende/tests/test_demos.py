"""Lo que dice el Duende de las demos de CS2 (P12): recomendaciones y chat por reglas."""

from app import chat
from app.insights import generar_insights
from app.modelos import JugadorRef, Mensaje, PeticionChat, PeticionInsights
from app.reglas_chat import detectar_intencion

from .conftest import demos_cs2, equipo_cs2, jugador, mapa_muertes, rondas, resumen_cs2


def peticion(demos=None, rol=None, lang="es") -> PeticionInsights:
    return PeticionInsights(
        lang=lang,
        jugador=JugadorRef(slug="j3", nombre="Jugador 3"),
        juego="cs2",
        rol=rol,
        resumen=resumen_cs2(),
        equipo=equipo_cs2(),
        demos=demos,
    )


def por_id(insights, id_):
    return next((i for i in insights if i.id == id_), None)


def ids(insights) -> list[str]:
    return [i.id for i in insights]


# Inferno: 154 muertes, 73 de ellas en Banana sin trade (47 %).
INFERNO = mapa_muertes("de_inferno", 154, 125, ("Banana", 79, 73), ("Bombsite B", 14, 11))


def test_sin_demos_o_con_pocas_no_dice_nada_de_ellas():
    assert generar_insights(peticion()) == []
    pocas = demos_cs2(metricas=rondas(partidas=4, rating=0.6, kast=50.0), mapas=[INFERNO])
    assert generar_insights(peticion(pocas)) == []


def test_jugador_normal_en_sus_demos_no_tiene_nada_que_destacar():
    assert generar_insights(peticion(demos_cs2())) == []


def test_rating_y_kast_bajos_frente_al_equipo_y_la_referencia():
    d = demos_cs2(metricas=rondas(rating=0.78, kast=60.0))
    insights = generar_insights(peticion(d))
    rating = por_id(insights, "debil_rating")
    assert rating.nivel == "alto"
    assert rating.titulo == "Rating por debajo"
    assert "Tienes 0,78; el resto del equipo, 1,00, y un jugador medio anda por 1,00." in rating.texto
    assert [b.etiqueta for b in rating.barras] == ["Tú", "Equipo", "Referencia"]
    assert rating.formato == "dec"
    assert por_id(insights, "debil_kast").nivel == "alto"  # 12 puntos por debajo


def test_las_asistencias_de_flash_solo_se_reconocen_nunca_se_reprochan():
    pocas = generar_insights(peticion(demos_cs2(metricas=rondas(flash_partida=0.2))))
    assert "debil_flash_partida" not in ids(pocas)
    muchas = generar_insights(peticion(demos_cs2(metricas=rondas(flash_partida=3.1))))
    flash = por_id(muchas, "fuerte_flash_partida")
    assert flash.titulo == "Flashes que matan"


def test_al_soporte_se_le_tolera_el_rating_y_sus_flashes_pesan_mas():
    d = demos_cs2(metricas=rondas(rating=0.86, flash_partida=1.08))
    sin_rol = generar_insights(peticion(d))
    assert por_id(sin_rol, "debil_rating").nivel == "medio"
    assert "fuerte_flash_partida" not in ids(sin_rol)
    soporte = generar_insights(peticion(d, rol="soporte"))
    assert "debil_rating" not in ids(soporte)
    assert "Justo lo que pide tu rol de soporte" in por_id(soporte, "fuerte_flash_partida").texto


def test_donde_muere_sin_que_le_tradeen():
    d = demos_cs2(metricas=rondas(tradeadas_pct=18.8), mapas=[INFERNO])
    insights = generar_insights(peticion(d))
    zona = por_id(insights, "zona_sin_trade")
    assert zona.nivel == "alto"
    assert zona.titulo == "En Banana te quedas solo"
    assert zona.texto == (
        "En Inferno, el 47,4 % de tus muertes son en Banana y sin que nadie te tradee (73 de 154)."
    )
    assert "Entra a Banana con alguien detrás" in zona.consejo
    assert [(b.etiqueta, b.valor) for b in zona.barras] == [("En Banana", 47.4), ("Resto de Inferno", 33.8)]
    # Ya habla de que muere sin trade: el aviso genérico sobra.
    assert "debil_tradeadas_pct" not in ids(insights)


def test_una_zona_sin_trade_que_no_es_tanto_o_con_pocas_muertes_no_salta():
    poco = mapa_muertes("de_inferno", 154, 60, ("Banana", 30, 25))  # 16 %
    assert "zona_sin_trade" not in ids(generar_insights(peticion(demos_cs2(mapas=[poco]))))
    pocas = mapa_muertes("de_nuke", 12, 9, ("Outside", 9, 9))  # 75 %, pero de 12 muertes
    assert "zona_sin_trade" not in ids(generar_insights(peticion(demos_cs2(mapas=[pocas]))))
    medio = mapa_muertes("de_nuke", 100, 50, ("Outside", 35, 30))  # 30 %
    assert por_id(generar_insights(peticion(demos_cs2(mapas=[medio]))), "zona_sin_trade").nivel == "medio"


def test_al_lurker_no_se_le_dice_que_muere_solo():
    d = demos_cs2(metricas=rondas(tradeadas_pct=12.0), mapas=[INFERNO])
    insights = generar_insights(peticion(d, rol="lurker"))
    assert "zona_sin_trade" not in ids(insights)
    assert "debil_tradeadas_pct" not in ids(insights)


def test_de_t_rinde_mucho_menos_que_de_ct():
    d = demos_cs2(lados=(("CT", 711, 1.65, 47.4), ("T", 697, 0.9, 51.8)))
    lado = por_id(generar_insights(peticion(d)), "lado_debil")
    assert lado.nivel == "alto"
    assert lado.titulo == "De T te apagas"
    assert lado.texto == "De T tu rating es 0,90; de CT, 1,65. De T ganas el 51,8 % de las rondas (697)."
    assert lado.consejo.startswith("De T, entra detrás del entry")
    assert [(b.etiqueta, b.valor, b.tuyo) for b in lado.barras] == [("De T", 0.9, True), ("De CT", 1.65, False)]


def test_de_ct_con_menos_diferencia_es_para_vigilar_y_con_pocas_rondas_nada():
    d = demos_cs2(lados=(("CT", 300, 0.85, 45.0), ("T", 300, 1.15, 55.0)))
    lado = por_id(generar_insights(peticion(d)), "lado_debil")
    assert lado.nivel == "medio" and lado.consejo.startswith("De CT")
    pocas = demos_cs2(lados=(("CT", 40, 0.6, 45.0), ("T", 40, 1.4, 55.0)))
    assert "lado_debil" not in ids(generar_insights(peticion(pocas)))


def test_las_forzadas_no_salen():
    d = demos_cs2(economia=(("pistola", 40, 50.0), ("forzada", 60, 25.0), ("completa", 300, 62.0)))
    f = por_id(generar_insights(peticion(d)), "forzadas_malas")
    assert f.texto == "Ganas el 25,0 % de las rondas forzadas (60); con compra completa, el 62,0 %."
    assert [b.valor for b in f.barras] == [25.0, 62.0]
    normales = demos_cs2(economia=(("forzada", 60, 44.0),))
    assert "forzadas_malas" not in ids(generar_insights(peticion(normales)))


def test_en_ingles():
    d = demos_cs2(metricas=rondas(rating=0.78), lados=(("CT", 711, 1.65, 47.4), ("T", 697, 0.9, 51.8)),
                  mapas=[INFERNO])
    insights = generar_insights(peticion(d, lang="en"))
    assert por_id(insights, "zona_sin_trade").texto == (
        "On Inferno, 47.4% of your deaths happen at Banana with no one trading you (73 of 154)."
    )
    assert por_id(insights, "lado_debil").titulo == "You switch off on T"
    assert por_id(insights, "debil_rating").titulo == "Rating below the bar"


# ─── Chat ────────────────────────────────────────────────────────────────────


def equipo():
    d = demos_cs2(
        metricas=rondas(rating=1.28, kast=76.8, aperturas=207, aperturas_ganadas=70, apertura_pct=33.8,
                        trades_partida=3.52, tradeadas_pct=18.8, flash_partida=0.33),
        equipo=rondas(partidas=130, rating=1.17, kast=79.7),
        lados=(("CT", 567, 1.25, 50.8), ("T", 562, 1.3, 52.5)),
        economia=(("pistola", 104, 57.7), ("eco", 165, 15.8), ("forzada", 252, 44.8), ("completa", 608, 63.2)),
        mapas=[INFERNO],
    )
    return [
        jugador("j3", "Jugador 3", resumen_cs2(), equipo_cs2(), demos=d, rol="entry"),
        jugador("j1", "Jugador 1", resumen_cs2(), equipo_cs2(), demos=demos_cs2(metricas=rondas(rating=1.4))),
        jugador("j4", "Jugador 4", resumen_cs2(juego="smite2", datos_medios={})),
    ]


def preguntar(texto: str, foco=None, lang="es") -> chat.RespuestaChat:
    return chat.responder(
        PeticionChat(lang=lang, mensajes=[Mensaje(rol="usuario", texto=texto)], foco=foco or [], equipo=equipo())
    )


def test_intenciones_de_las_demos():
    for pregunta in ("¿Dónde muero más?", "¿Cómo voy de T?", "¿Qué tal mi KAST?", "¿Cuál es mi rating?",
                     "¿Me tradean?", "¿Gano las forzadas?", "where do I die the most?", "how am I doing on CT?",
                     "¿Y en las rondas de pistola?"):
        assert detectar_intencion(pregunta, 1) == "demos", pregunta
    # Lo de siempre no cambia.
    assert detectar_intencion("¿Qué mapa se me da peor?", 1) == "desglose"
    assert detectar_intencion("¿Cuál es el mejor mapa de todos?", 1) == "desglose"
    assert detectar_intencion("¿Con quién juego mejor?", 1) == "companeros"
    assert detectar_intencion("¿Cómo voy para mi nivel?", 1) == "nivel"


def test_donde_muero_mas():
    r = preguntar("¿Dónde muero más?", foco=["j3"])
    assert r.origen == "reglas" and r.intencion == "demos"
    texto = r.respuesta
    assert "**Jugador 3 · 20 partidas con demo (440 rondas)**" in texto
    assert "- Rating: **1,28** (equipo 1,17)" in texto
    assert "- Duelos de apertura: ganas **70 de 207** (33,8 %)" in texto
    assert "te tradean el **18,8 %** de tus muertes" in texto
    assert "- De CT: rating 1,25, ganas el 50,8 % de las rondas · De T: rating 1,30" in texto
    assert "completa 63,2 % · forzada 44,8 % · eco 15,8 % · pistola 57,7 %" in texto
    assert "Donde más mueres sin que te tradeen: **Banana** en Inferno (73 de tus 154 muertes en ese mapa)." in texto
    assert "**En Banana te quedas solo.**" in texto
    assert "¿Dónde muero más?" in r.sugerencias


def test_sin_demos_lo_dice_y_en_smite_tambien():
    r = preguntar("¿Dónde muero más?", foco=["j4"])
    assert "no tengo ninguna demo" in r.respuesta.lower() or "solo las analizo en CS2" in r.respuesta
    r = preguntar("¿Cuál es mi rating?", foco=["j1"])
    assert "**Jugador 1 · 20 partidas con demo" in r.respuesta


def test_quien_tiene_mejor_rating():
    r = preguntar("¿Quién tiene mejor rating?")
    assert r.respuesta.startswith("Rating de las demos del equipo:")
    assert r.respuesta.index("Jugador 1") < r.respuesta.index("Jugador 3")


def test_demos_en_ingles_y_con_un_periodo():
    r = preguntar("Where do I die the most?", foco=["j3"], lang="en")
    assert "Where you die untraded the most: **Banana** on Inferno" in r.respuesta
    r = preguntar("¿Dónde muero más esta semana?", foco=["j3"])
    assert r.respuesta.startswith("Esto lo miro con todas las partidas")
