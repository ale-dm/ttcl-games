from app.insights import generar_insights
from app.metricas import AJUSTES_ROL, METRICAS, NO_SE_JUZGA, PESA_MAS, TOLERA, formatear
from app.modelos import Desglose, JugadorRef, MediasEquipo, PeticionInsights, Resumen, Sinergias
from app.textos import NOMBRES_MOMENTO, NOMBRES_ROL

from .conftest import companero, equipo_cs2, momento, resumen_cs2, sesiones


def peticion(**cambios) -> PeticionInsights:
    base = dict(
        lang="es",
        jugador=JugadorRef(slug="j1", nombre="Jugador 1"),
        juego="cs2",
        resumen=resumen_cs2(),
        equipo=equipo_cs2(),
    )
    base.update(cambios)
    return PeticionInsights(**base)


def ids(insights) -> list[str]:
    return [i.id for i in insights]


def test_jugador_en_la_media_no_tiene_nada_que_destacar():
    assert generar_insights(peticion()) == []


def test_kd_alto_y_winrate_bajo_da_la_regla_especial_y_no_repite_winrate():
    r = resumen_cs2(kd=1.3, winrate=37.1)
    insights = generar_insights(peticion(resumen=r))
    assert insights[0].id == "kd_sin_victorias"
    assert insights[0].nivel == "alto"
    assert insights[0].titulo == "Tus kills no se convierten en victorias"
    assert "1,30" in insights[0].texto and "37,1 %" in insights[0].texto
    assert "debil_winrate" not in ids(insights)


def test_debilidad_frente_al_equipo_con_barras_y_consejo():
    r = resumen_cs2(datos_medios={"adr": 62.0, "hs_pct": 45.0, "kr": 0.7, "entry_pct": 50.0, "clutch_pct": 25.0})
    insights = generar_insights(peticion(resumen=r))
    adr = next(i for i in insights if i.id == "debil_adr")
    assert adr.nivel == "alto"  # 62 frente a 80: un 22 % por debajo
    assert adr.consejo
    assert [b.etiqueta for b in adr.barras] == ["Tú", "Equipo", "Referencia"]
    assert adr.barras[0].tuyo and adr.barras[0].valor == 62.0


def test_muertes_bajas_son_fortaleza_porque_menos_es_mejor():
    r = resumen_cs2(muertes_media=14.0)
    insights = generar_insights(peticion(resumen=r))
    assert "fuerte_muertes_media" in ids(insights)
    assert "debil_muertes_media" not in ids(insights)


def test_orden_debilidades_graves_primero_y_fortalezas_al_final():
    r = resumen_cs2(
        winrate=44.0,
        kd=0.75,
        datos_medios={"adr": 95.0, "hs_pct": 40.0, "kr": 0.7, "entry_pct": 50.0, "clutch_pct": 25.0},
    )
    niveles = [i.nivel for i in generar_insights(peticion(resumen=r))]
    assert niveles == sorted(niveles, key=["info", "alto", "medio", "bien"].index)
    assert niveles[-1] == "bien"


def test_racha_mala():
    insights = generar_insights(peticion(resumen=resumen_cs2(forma="DDDVDVVDVV")))
    assert "racha_mala" in ids(insights)


def test_tendencia_reciente():
    insights = generar_insights(peticion(reciente=resumen_cs2(partidas=10, kd=1.3)))
    sube = next(i for i in insights if i.id == "tendencia_sube")
    assert sube.nivel == "bien"
    assert [b.etiqueta for b in sube.barras] == ["Últimas 10", "Global"]


def test_mapa_flojo_y_mapa_fuerte():
    desglose = [
        Desglose(clave="de_nuke", partidas=6, victorias=1, winrate=16.7, kd=0.8),
        Desglose(clave="de_mirage", partidas=8, victorias=6, winrate=75.0, kd=1.2),
        Desglose(clave="de_train", partidas=2, victorias=0, winrate=0.0, kd=0.5),  # muy pocas: no cuenta
    ]
    insights = generar_insights(peticion(desglose=desglose))
    malo = next(i for i in insights if i.id == "desglose_malo")
    assert malo.titulo == "de_nuke se te atraganta"
    bueno = next(i for i in insights if i.id == "desglose_bueno")
    assert bueno.titulo == "de_mirage es tu casa"


def test_muestra_pequena_avisa_y_recorta():
    r = resumen_cs2(partidas=3, kd=0.5, winrate=0.0, forma="DDD")
    insights = generar_insights(peticion(resumen=r))
    assert insights[0].id == "muestra"
    assert insights[0].nivel == "info"
    assert len(insights) <= 3


def test_sin_equipo_compara_con_la_referencia():
    r = resumen_cs2(datos_medios={"adr": 60.0, "hs_pct": 45.0})
    insights = generar_insights(peticion(resumen=r, equipo=None))
    adr = next(i for i in insights if i.id == "debil_adr")
    assert [b.etiqueta for b in adr.barras] == ["Tú", "Referencia"]
    assert "un jugador medio" in adr.texto


def test_ingles():
    r = resumen_cs2(kd=1.3, winrate=37.1)
    insight = generar_insights(peticion(resumen=r, lang="en"))[0]
    assert insight.titulo == "Your kills don't turn into wins"
    assert "1.30" in insight.texto and "37.1%" in insight.texto


def test_smite_usa_sus_metricas():
    r = Resumen(juego="smite2", partidas=20, winrate=50, kd=1.0, datos_medios={"kda": 1.2, "dano_min": 600})
    e = MediasEquipo(jugadores=2, winrate=50, kd=1.0, datos_medios={"kda": 2.4, "dano_min": 900})
    insights = generar_insights(peticion(juego="smite2", resumen=r, equipo=e))
    assert {"debil_kda", "debil_dano_min"} <= set(ids(insights))


# ─── Rol del jugador ─────────────────────────────────────────────────────────


def test_soporte_con_pocas_kills_no_recibe_aviso_y_si_un_bien_por_asistencias():
    r = resumen_cs2(kills_media=13.0, asistencias_media=8.0)
    sin_rol = generar_insights(peticion(resumen=r))
    kills = next(i for i in sin_rol if i.id == "debil_kills_media")  # sin saber su rol, se le regaña
    assert kills.titulo == "Kills / partida: por debajo del equipo"

    insights = generar_insights(peticion(resumen=r, rol="soporte"))
    assert not any(i.metrica == "kills_media" and i.nivel in ("alto", "medio") for i in insights)
    asistencias = next(i for i in insights if i.id == "fuerte_asistencias_media")
    assert asistencias.nivel == "bien"
    assert asistencias.texto == "Tienes 8,00; el resto del equipo, 5,00. Justo lo que pide tu rol de soporte."


def test_la_nota_del_rol_tambien_en_ingles():
    r = resumen_cs2(kills_media=13.0, asistencias_media=8.0)
    insights = generar_insights(peticion(resumen=r, rol="soporte", lang="en"))
    asistencias = next(i for i in insights if i.id == "fuerte_asistencias_media")
    assert asistencias.texto.endswith("Exactly what your support role calls for.")


def test_entry_puede_morir_mas_y_tener_peor_kd_pero_no_sin_limite():
    r = resumen_cs2(muertes_media=20.5, kd=0.88)  # un 14 % y un 12 % peor que el equipo
    assert {"debil_muertes_media", "debil_kd"} <= set(ids(generar_insights(peticion(resumen=r))))
    assert not {"debil_muertes_media", "debil_kd"} & set(ids(generar_insights(peticion(resumen=r, rol="entry"))))

    muchas = generar_insights(peticion(resumen=resumen_cs2(muertes_media=27.0), rol="entry"))
    muertes = next(i for i in muchas if i.id == "debil_muertes_media")
    assert muertes.nivel == "alto"
    assert muertes.texto.endswith("En tu rol de entry se perdona algo, pero no tanto.")


def test_awp_no_se_juzga_por_headshots_y_su_kr_pesa_mas():
    r = resumen_cs2(datos_medios={"adr": 71.0, "hs_pct": 30.0, "kr": 0.64, "entry_pct": 50.0, "clutch_pct": 25.0})
    sin_rol = ids(generar_insights(peticion(resumen=r)))
    assert "debil_hs_pct" in sin_rol
    assert "debil_kr" not in sin_rol  # un 9 % por debajo: sin rol no llega para avisar

    insights = generar_insights(peticion(resumen=r, rol="awp"))
    assert "debil_hs_pct" not in ids(insights)
    kr = next(i for i in insights if i.id == "debil_kr")
    assert kr.nivel == "medio"
    assert kr.texto.endswith("Y en tu rol de AWP, esto es lo que más cuenta.")
    # Lo suyo va antes que lo demás del mismo nivel (el ADR está un 11 % por debajo, casi igual).
    assert ids(insights).index("debil_kr") < ids(insights).index("debil_adr")


def test_lo_que_pide_el_rol_se_reconoce_antes():
    r = resumen_cs2(asistencias_media=5.4)  # un 8 % por encima: sin rol no destaca
    assert "fuerte_asistencias_media" not in ids(generar_insights(peticion(resumen=r)))
    assert "fuerte_asistencias_media" in ids(generar_insights(peticion(resumen=r, rol="soporte")))


def test_rol_desconocido_o_de_otro_juego_cuenta_como_ninguno():
    r = resumen_cs2(kills_media=13.0, asistencias_media=8.0)
    sin_rol = generar_insights(peticion(resumen=r))
    assert generar_insights(peticion(resumen=r, rol="guardian")) == sin_rol  # es un rol de SMITE 2
    assert generar_insights(peticion(resumen=r, rol="francotirador")) == sin_rol


def test_guardian_de_smite_no_se_juzga_por_kills_dano_ni_oro():
    r = Resumen(
        juego="smite2", partidas=20, winrate=50, kd=1.0, kills_media=3.0, asistencias_media=15.0,
        datos_medios={"kda": 3.0, "dano_min": 600, "oro_min": 400, "mitigado": 26000},
    )
    e = MediasEquipo(
        jugadores=2, winrate=50, kd=1.0, kills_media=7.0, asistencias_media=8.0,
        datos_medios={"kda": 3.0, "dano_min": 900, "oro_min": 520, "mitigado": 10000},
    )
    flojas = {"debil_kills_media", "debil_dano_min", "debil_oro_min"}
    assert flojas <= set(ids(generar_insights(peticion(juego="smite2", resumen=r, equipo=e))))

    insights = generar_insights(peticion(juego="smite2", resumen=r, equipo=e, rol="guardian"))
    assert not flojas & set(ids(insights))
    assert {"fuerte_mitigado", "fuerte_asistencias_media"} <= set(ids(insights))
    assert all("rol de guardián" in i.texto for i in insights)


def test_cada_rol_tiene_nombre_y_solo_ajusta_metricas_que_existen():
    for juego, roles in AJUSTES_ROL.items():
        claves = {m.clave for m in METRICAS[juego]}
        for rol, ajustes in roles.items():
            assert set(NOMBRES_ROL[rol]) == {"es", "en"}
            assert set(ajustes) <= claves, f"{juego}/{rol}"
            assert set(ajustes.values()) <= {NO_SE_JUZGA, TOLERA, PESA_MAS}
    # Ningún rol se llama igual en los dos juegos: la web traduce el rol sin mirar el juego.
    assert not set(AJUSTES_ROL["cs2"]) & set(AJUSTES_ROL["smite2"])
    assert set(NOMBRES_ROL) == set(AJUSTES_ROL["cs2"]) | set(AJUSTES_ROL["smite2"])


# ─── Con quién juegas mejor ──────────────────────────────────────────────────


def con(*companeros, solo=None) -> Sinergias:
    return Sinergias(solo=solo, companeros=list(companeros))


def test_con_un_companero_ganas_mucho_mas_que_sin_el():
    insights = generar_insights(peticion(sinergias=con(companero("Jugador 3", 20, 65.0, 10, 38.0))))
    bueno = next(i for i in insights if i.id == "companero_bueno")
    assert bueno.nivel == "bien"
    assert bueno.titulo == "Con Jugador 3 vas a otro nivel"
    assert bueno.texto == "Con Jugador 3 ganas el 65,0 % de 20 partidas; sin Jugador 3, el 38,0 %."
    assert bueno.consejo
    assert bueno.formato == "pct"
    assert [(b.etiqueta, b.valor, b.tuyo) for b in bueno.barras] == [
        ("Con Jugador 3", 65.0, True),
        ("Sin Jugador 3", 38.0, False),
    ]


def test_con_un_companero_ganas_mucho_menos_que_sin_el():
    insights = generar_insights(peticion(sinergias=con(companero("Bea", 12, 30.0, 18, 55.0))))
    malo = next(i for i in insights if i.id == "companero_malo")
    assert malo.nivel == "medio"
    assert malo.titulo == "Con Bea no termina de cuajar"
    assert "repartid roles" in malo.consejo  # el consejo de CS2
    smite = generar_insights(
        peticion(juego="smite2", resumen=Resumen(juego="smite2", partidas=30), equipo=None,
                 sinergias=con(companero("Bea", 12, 30.0, 18, 55.0)))
    )
    assert "composición" in next(i for i in smite if i.id == "companero_malo").consejo


def test_sin_muestra_o_sin_diferencia_clara_no_dice_nada_del_companero():
    casos = [
        con(companero("Bea", 20, 60.0, 10, 46.0)),  # 14 puntos: no llega
        con(companero("Bea", 4, 100.0, 26, 40.0)),  # pocas partidas con ella
        con(companero("Bea", 26, 60.0, 4, 0.0)),  # pocas partidas sin ella
        con(companero("Bea", 20, 60.0, 0, None)),  # siempre juntos: nada con qué comparar
        con(),
        None,
    ]
    for sinergias in casos:
        ids_ = ids(generar_insights(peticion(sinergias=sinergias)))
        assert not {"companero_bueno", "companero_malo"} & set(ids_), sinergias


def test_solo_habla_del_mejor_y_del_peor_companero():
    sinergias = con(
        companero("Ana", 20, 70.0, 10, 40.0),  # +30
        companero("Bea", 15, 60.0, 15, 40.0),  # +20
        companero("Carla", 10, 30.0, 20, 55.0),  # −25
        companero("Dani", 10, 40.0, 20, 56.0),  # −16
    )
    insights = generar_insights(peticion(sinergias=sinergias))
    companeros = [(i.id, i.titulo) for i in insights if i.id.startswith("companero_")]
    assert companeros == [
        ("companero_malo", "Con Carla no termina de cuajar"),
        ("companero_bueno", "Con Ana vas a otro nivel"),
    ]


def test_companero_en_ingles():
    insights = generar_insights(peticion(lang="en", sinergias=con(companero("Jugador 3", 20, 65.0, 10, 38.0))))
    bueno = next(i for i in insights if i.id == "companero_bueno")
    assert bueno.titulo == "You click with Jugador 3"
    assert bueno.texto == "With Jugador 3 you win 65.0% of 20 matches; without Jugador 3, 38.0%."
    assert [b.etiqueta for b in bueno.barras] == ["With Jugador 3", "Without Jugador 3"]


# ─── Sesiones y tilt ─────────────────────────────────────────────────────────

# 1ª y 2ª, en la media; desde la 3ª, 20 puntos menos.
ORDEN_TILT = (momento("1", 20, 55.0, 35, 45.7), momento("2", 15, 55.0, 40, 47.5), momento("3+", 15, 35.0, 30, 55.0))


def test_a_partir_de_la_tercera_seguida_se_desinfla():
    insights = generar_insights(peticion(sesiones=sesiones(por_orden=ORDEN_TILT)))
    tilt = next(i for i in insights if i.id == "tilt_sesion")
    assert tilt.nivel == "medio"
    assert tilt.titulo == "Las sesiones largas se te atragantan"
    assert tilt.texto == "A partir de la 3ª partida seguida ganas el 35,0 % (15 partidas); en las dos primeras, el 55,0 %."
    assert "si pierdes dos seguidas, para por hoy" in tilt.consejo
    assert tilt.formato == "pct"
    assert [(b.etiqueta, b.valor, b.tuyo) for b in tilt.barras] == [
        ("3ª en adelante", 35.0, True),
        ("1ª y 2ª", 55.0, False),
    ]
    # Con 25 puntos o más, es de mejorar ya.
    grave = sesiones(por_orden=[momento("3+", 15, 28.0, 30, 55.0)])
    assert next(i for i in generar_insights(peticion(sesiones=grave)) if i.id == "tilt_sesion").nivel == "alto"


def test_sin_sesiones_o_partidas_suficientes_no_hay_aviso_de_tilt():
    casos = [
        sesiones(14, por_orden=ORDEN_TILT),  # 14 sesiones: aún no se puede saber
        sesiones(por_orden=[momento("3+", 9, 20.0, 40, 55.0)]),  # pocas partidas desde la 3ª
        sesiones(por_orden=[momento("3+", 40, 20.0, 9, 55.0)]),  # pocas en las dos primeras
        sesiones(por_orden=[momento("3+", 15, 41.0, 30, 55.0)]),  # 14 puntos: no llega
        sesiones(por_orden=[momento("3+", 15, 35.0, 0, None)]),  # nada con qué comparar
        sesiones(),
        None,
    ]
    for s in casos:
        assert not {"tilt_sesion", "tilt_derrota"} & set(ids(generar_insights(peticion(sesiones=s)))), s


def test_una_derrota_arrastra_a_la_siguiente():
    tras = [momento("victoria", 18, 60.0, 32, 48.0), momento("derrota", 17, 33.0, 33, 58.0)]
    insights = generar_insights(peticion(sesiones=sesiones(tras_resultado=tras)))
    derrota = next(i for i in insights if i.id == "tilt_derrota")
    assert derrota.nivel == "alto"  # 25 puntos
    assert derrota.titulo == "Una derrota te arrastra a la siguiente"
    assert derrota.texto == (
        "Después de perder, la siguiente la ganas el 33,0 % de las veces (17 partidas); el resto, el 58,0 %."
    )
    assert [b.etiqueta for b in derrota.barras] == ["Tras una derrota", "El resto"]
    # Si ya salta el tilt de las sesiones largas, no se repite: suele ser lo mismo.
    las_dos = generar_insights(peticion(sesiones=sesiones(por_orden=ORDEN_TILT, tras_resultado=tras)))
    assert "tilt_sesion" in ids(las_dos) and "tilt_derrota" not in ids(las_dos)


def test_mejor_hora_del_dia():
    franjas = [
        momento("tarde", 15, 50.0, 30, 50.0),
        momento("noche", 20, 65.0, 25, 40.0),  # +25
        momento("madrugada", 6, 100.0, 39, 46.0),  # más diferencia, pero con 6 partidas no cuenta
    ]
    insights = generar_insights(peticion(sesiones=sesiones(por_franja=franjas)))
    hora = next(i for i in insights if i.id == "mejor_horario")
    assert hora.nivel == "bien"
    assert hora.titulo == "Rindes más por la noche"
    assert hora.texto == "Por la noche ganas el 65,0 % (20 partidas); el resto del día, el 40,0 %."
    assert hora.consejo == "Si vas a jugar para subir, juega por la noche; a otras horas, partidas tranquilas."
    assert [(b.etiqueta, b.valor, b.tuyo) for b in hora.barras] == [("Por la noche", 65.0, True), ("Resto del día", 40.0, False)]
    # Entre cuatro franjas alguna sale mejor por casualidad: hacen falta 20 puntos.
    justa = sesiones(por_franja=[momento("noche", 20, 59.0, 25, 40.0)])
    assert "mejor_horario" not in ids(generar_insights(peticion(sesiones=justa)))
    pocas = sesiones(10, por_franja=franjas)
    assert "mejor_horario" not in ids(generar_insights(peticion(sesiones=pocas)))


def test_sesiones_en_ingles():
    s = sesiones(por_orden=ORDEN_TILT, por_franja=[momento("madrugada", 20, 65.0, 25, 40.0)])
    insights = generar_insights(peticion(lang="en", sesiones=s))
    tilt = next(i for i in insights if i.id == "tilt_sesion")
    assert tilt.titulo == "Long sessions wear you down"
    assert tilt.texto == "From your 3rd match in a row you win 35.0% (15 matches); in the first two, 55.0%."
    assert [b.etiqueta for b in tilt.barras] == ["3rd onwards", "1st and 2nd"]
    hora = next(i for i in insights if i.id == "mejor_horario")
    assert hora.titulo == "You play best late at night"
    assert hora.texto == "Late at night you win 65.0% (20 matches); the rest of the day, 40.0%."


def test_cada_fila_de_las_sesiones_tiene_nombre():
    # Las mismas claves que Estadisticas.sesiones (Java) y los textos momento.* de la web.
    claves = {"1", "2", "3+", "victoria", "derrota", "manana", "tarde", "noche", "madrugada"}
    assert set(NOMBRES_MOMENTO) == claves
    assert all(set(n) == {"es", "en"} for n in NOMBRES_MOMENTO.values())


def test_formatear():
    assert formatear(37.14, "pct", "es") == "37,1 %"
    assert formatear(37.14, "pct", "en") == "37.1%"
    assert formatear(1.2, "dec", "es") == "1,20"
    assert formatear(12345, "int", "es") == "12.345"
    assert formatear(1234, "int", "es") == "1234"
    assert formatear(12345, "int", "en") == "12,345"
    assert formatear(None, "pct", "es") == "—"
