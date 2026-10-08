from app.insights import generar_insights
from app.metricas import formatear
from app.modelos import Desglose, JugadorRef, PeticionInsights

from .conftest import equipo_cs2, resumen_cs2


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
    from app.modelos import MediasEquipo, Resumen

    r = Resumen(juego="smite2", partidas=20, winrate=50, kd=1.0, datos_medios={"kda": 1.2, "dano_min": 600})
    e = MediasEquipo(jugadores=2, winrate=50, kd=1.0, datos_medios={"kda": 2.4, "dano_min": 900})
    insights = generar_insights(peticion(juego="smite2", resumen=r, equipo=e))
    assert {"debil_kda", "debil_dano_min"} <= set(ids(insights))


def test_formatear():
    assert formatear(37.14, "pct", "es") == "37,1 %"
    assert formatear(37.14, "pct", "en") == "37.1%"
    assert formatear(1.2, "dec", "es") == "1,20"
    assert formatear(12345, "int", "es") == "12.345"
    assert formatear(1234, "int", "es") == "1234"
    assert formatear(12345, "int", "en") == "12,345"
    assert formatear(None, "pct", "es") == "—"
