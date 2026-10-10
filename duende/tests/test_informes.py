from fastapi.testclient import TestClient

from app.informes import INFORMES, PRIORIDAD, nombre_clave, texto_informe, texto_semana
from app.main import app
from app.modelos import FilaSemana, Hecho, ItemInforme, SemanaJuego
from app.textos import INFORMES as TEXTOS_INFORMES

cliente = TestClient(app)


def informe(*hechos: Hecho, id_: str = "1", juego: str = "cs2") -> ItemInforme:
    return ItemInforme(id=id_, juego=juego, hechos=list(hechos))


def test_cada_tipo_de_hecho_tiene_texto_en_los_dos_idiomas_y_prioridad():
    assert set(PRIORIDAD) == set(TEXTOS_INFORMES) == set(INFORMES)
    for tipo, textos in TEXTOS_INFORMES.items():
        assert textos["es"] and len(textos["es"]) == len(textos["en"]), tipo


def test_una_racha_y_un_numero_unidos_segun_lo_que_dicen():
    # Racha mala y número malo: "Y encima".
    racha = Hecho(tipo="racha_derrotas", n=3)
    peor = Hecho(tipo="peor_mes", metrica="adr", valor=54.2, referencia=61.0, n=8)
    texto = texto_informe(informe(racha, peor), "es")
    assert texto.endswith("Y encima, tu peor ADR del mes: 54 (lo peor de antes, 61).")
    assert texto.startswith("Tercera derrota seguida") or texto.startswith("Van 3 derrotas seguidas")
    # Se acaba una racha mala pero el número es malo: "Eso sí"; y las siglas no pasan a minúscula.
    fin = Hecho(tipo="fin_racha_derrotas", n=4)
    kda = Hecho(tipo="bajo_media", metrica="kda", valor=0.8, referencia=2.4, n=20)
    assert "Eso sí, KDA muy por debajo de lo tuyo: 0,80 (sueles andar por 2,40)." in texto_informe(
        informe(fin, kda, juego="smite2"), "es"
    )
    # Racha buena y estreno: "Además", y el mapa sin "de_".
    buena = Hecho(tipo="racha_victorias", n=4)
    estreno = Hecho(tipo="estreno_clave", clave="de_anubis")
    assert texto_informe(informe(buena, estreno), "en").split(". ", 1)[0] in ("Fourth win in a row", "That's 4 wins in a row")


def test_como_mucho_dos_y_entre_varios_records_el_que_mas_se_pasa():
    kills = Hecho(tipo="mejor_mes", metrica="kills_media", valor=25, referencia=24, n=8)
    adr = Hecho(tipo="mejor_mes", metrica="adr", valor=130, referencia=95, n=8)
    estreno = Hecho(tipo="estreno_clave", clave="de_anubis")
    media = Hecho(tipo="sobre_media", metrica="adr", valor=130, referencia=80, n=20)
    texto = texto_informe(informe(kills, adr, estreno, media), "es")
    assert texto.startswith("Tu mejor ADR del mes: 130 (lo mejor de antes, 95).")
    assert texto.endswith("Además, primera vez en Anubis.") or texto.endswith("Además, estreno en Anubis.")
    assert "kills" not in texto


def test_sin_nada_especial_no_hay_comentario_y_la_variante_no_cambia():
    assert texto_informe(informe(), "es") is None
    assert texto_informe(informe(Hecho(tipo="desconocido")), "es") is None
    racha = Hecho(tipo="racha_derrotas_clave", n=11, clave="de_nuke")
    assert texto_informe(informe(racha, id_="7"), "es") == texto_informe(informe(racha, id_="7"), "es")
    textos = {texto_informe(informe(racha, id_=str(i)), "es") for i in range(20)}
    assert textos == {"11.ª derrota seguida en Nuke.", "Nuke ya va por 11 derrotas seguidas: se te atraganta."}


def test_nombre_de_mapas_y_dioses():
    assert nombre_clave("de_dust2") == "Dust2"
    assert nombre_clave("Loki") == "Loki"
    assert nombre_clave(None) == ""


def test_semana_con_el_mejor_el_peor_y_sin_nadie():
    mejor = FilaSemana(slug="j3", nombre="Jugador 3", partidas=5, victorias=5, winrate=100.0, kd=2.1)
    peor = FilaSemana(slug="j1", nombre="Jugador 1", partidas=7, victorias=2, winrate=28.6, kd=0.9)
    medio = FilaSemana(slug="j2", nombre="Jugador 2", partidas=6, victorias=3, winrate=50.0, kd=1.0)
    juegos = [
        SemanaJuego(juego="cs2", partidas=12, jugadores=[peor, medio, mejor], mejor=mejor, peor=peor),
        SemanaJuego(juego="smite2", partidas=4, jugadores=[medio, peor], mejor=medio, peor=peor),
        SemanaJuego(juego="smite2", partidas=1),
        # Si solo llega uno, no es "el mejor" de nadie.
        SemanaJuego(juego="smite2", partidas=5, jugadores=[peor], mejor=peor, peor=None),
    ]
    texto = texto_semana(juegos, "es").split("\n")
    assert texto[0] == (
        "**Counter-Strike 2**: 12 partidas del equipo en los últimos 7 días. El mejor, Jugador 3: 5 de 5 ganadas "
        "(100,0 %). El peor, Jugador 1: 2 de 7 (28,6 %). Que alguien le pida el secreto a Jugador 3."
    )
    assert texto[1].endswith("A Jugador 1 le toca descanso o entrenamiento, lo que prefiera.")
    assert texto[2] == "**SMITE 2**: 1 partida del equipo en los últimos 7 días. Nadie llegó a 3 partidas: no hay mejor ni peor."
    assert texto[3] == (
        "**SMITE 2**: 5 partidas del equipo en los últimos 7 días. Solo Jugador 1 llegó a 3 partidas: 2 de 7 ganadas "
        "(28,6 %). A Jugador 1 le toca descanso o entrenamiento, lo que prefiera."
    )
    assert "Best: Jugador 3, 5 of 5 won (100.0%)." in texto_semana(juegos, "en")


def test_api_de_informes_y_semana_en_camel_case():
    r = cliente.post("/v1/informes", json={"lang": "es", "items": [
        {"id": "12-j4", "juego": "smite2", "hechos": [{"tipo": "racha_derrotas", "n": 3}]},
        {"id": "13-j4", "juego": "smite2", "hechos": []},
    ]})
    assert r.status_code == 200
    items = r.json()["items"]
    assert items[0]["id"] == "12-j4" and "derrota" in items[0]["texto"]
    assert items[1] == {"id": "13-j4", "texto": None}

    r = cliente.post("/v1/semana", json={"lang": "en", "juegos": [
        {"juego": "cs2", "partidas": 3, "jugadores": [], "mejor": None, "peor": None}]})
    assert r.status_code == 200
    assert r.json()["texto"].startswith("**Counter-Strike 2**: 3 team matches")
