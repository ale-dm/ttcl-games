"""Lo que pasó en cada ronda, con eventos escritos a mano."""

from app.rondas import (
    Dano,
    Equipo,
    Eventos,
    FinRonda,
    Muerte,
    analizar,
    limites,
    ronda_de,
    tipo_compra,
)

# A y B son del equipo (T en la 1ª ronda, CT en la 2ª); C y D, rivales.
A, B, C, D = "76561198000000001", "76561198000000002", "76561198000000003", "76561198000000004"
BUSCADOS = {A: "j1", B: "j2"}


def partida() -> Eventos:
    return Eventos(
        fines=[FinRonda(1000, "T", "t_win"), FinRonda(2000, "T", "t_win")],
        inicios=[100, 1100],
        muertes=[
            # Ronda 1: A abre matando a C con la flash de B...
            Muerte(200, victima=C, atacante=A, asistente=B, flash=True, lado_victima="CT", lado_atacante="T",
                   x=1.0, y=2.0, zona="Middle"),
            # ...D mata a A en el A y B lo tradea en menos de 5 segundos.
            Muerte(300, victima=A, atacante=D, lado_victima="T", lado_atacante="CT", x=10.0, y=20.0, zona="BombsiteA"),
            Muerte(400, victima=D, atacante=B, lado_victima="CT", lado_atacante="T", x=11.0, y=21.0, zona="BombsiteA"),
            # Kill de salida: después del final de la ronda 1 y antes de que empiece la 2. Es de la 1.
            Muerte(1050, victima=C, atacante=B, lado_victima="CT", lado_atacante="T", x=5.0, y=5.0, zona="CTSpawn"),
            # Ronda 2: C mata a B nada más empezar y nadie le tradea (la siguiente muerte es 8 segundos después).
            Muerte(1200, victima=B, atacante=C, lado_victima="CT", lado_atacante="T", x=-3.0, y=4.0, zona="Middle"),
            Muerte(1712, victima=A, atacante=D, asistente=C, lado_victima="CT", lado_atacante="T", x=0.0, y=0.0,
                   zona="Jungle"),
        ],
        danos=[
            # 120 de un tiro a C: cuentan 100. Y 30 de HE a D: daño de utilidad.
            Dano(150, A, C, 120, "ak47", "T", "CT"),
            Dano(180, A, D, 30, "hegrenade", "T", "CT"),
            Dano(190, A, D, 90, "ak47", "T", "CT"),  # 30 + 90 > 100: de este tiro cuentan 70.
            # El daño a un compañero no cuenta.
            Dano(250, B, A, 50, "ak47", "T", "T"),
        ],
        equipos=[
            Equipo(1, A, "T", 800),
            Equipo(1, B, "T", 700),
            Equipo(2, A, "CT", 1200),
            Equipo(2, B, "CT", 4100),
        ],
    )


def test_limites_y_ronda_de_cada_evento():
    lims = limites([FinRonda(1000, "T"), FinRonda(2000, "CT")], [100, 1100])
    assert lims[0] == (100, 1100)
    assert lims[1][0] == 1100
    assert ronda_de(50, lims) == 1
    assert ronda_de(1050, lims) == 1  # kill de salida
    assert ronda_de(1100, lims) == 1
    assert ronda_de(1101, lims) == 2
    assert ronda_de(99999, lims) == 2


def test_sin_inicios_cada_ronda_acaba_en_su_final():
    lims = limites([FinRonda(1000, "T"), FinRonda(2000, "CT")], [])
    assert lims[0] == (None, 1000)
    assert ronda_de(1050, lims) == 2


def test_kills_aperturas_trades_y_kast():
    r = analizar(partida(), BUSCADOS)
    a1, a2 = r.jugadores["j1"]
    b1, b2 = r.jugadores["j2"]

    assert (a1.ronda, a1.lado, a1.gano) == (1, "T", True)
    assert a1.kills == 1 and a1.apertura == "ganada"
    assert a1.murio and a1.muerte_zona == "BombsiteA" and (a1.muerte_x, a1.muerte_y) == (10.0, 20.0)
    assert a1.tradeado
    assert b1.trades == 1
    assert b1.kills == 2  # el trade y la kill de salida
    assert b1.asistencias == 1 and b1.asistencias_flash == 1
    assert a1.kast and b1.kast

    assert (a2.lado, a2.gano) == ("CT", False)
    assert b2.apertura == "perdida"
    assert b2.murio and not b2.tradeado and not b2.kast
    assert a2.murio and not a2.tradeado and not a2.kast
    assert a2.apertura is None


def test_dano_con_tope_por_rival_y_sin_companeros():
    a1 = analizar(partida(), BUSCADOS).jugadores["j1"][0]
    assert a1.dano == 100 + 100  # 100 a C (de 120) y 100 a D (30 + 70 de 90)
    assert a1.dano_utilidad == 30
    b1 = analizar(partida(), BUSCADOS).jugadores["j2"][0]
    assert b1.dano == 0


def test_equipamiento_y_tipo_de_compra():
    r = analizar(partida(), BUSCADOS)
    assert [f.compra for f in r.jugadores["j1"]] == ["pistola", "eco"]
    assert [f.compra for f in r.jugadores["j2"]] == ["pistola", "completa"]
    assert r.jugadores["j1"][1].equipamiento == 1200
    assert tipo_compra(13, 5000) == "pistola"
    assert tipo_compra(5, 2500) == "forzada"
    assert tipo_compra(5, None) is None


def test_muertes_anonimas_de_todos():
    r = analizar(partida(), BUSCADOS)
    assert len(r.muertes) == 6
    assert (10.0, 20.0, "BombsiteA") in r.muertes


def test_ni_el_mundo_ni_los_companeros_cuentan_como_kill_ni_como_apertura():
    eventos = Eventos(
        fines=[FinRonda(1000, "CT")],
        inicios=[100],
        muertes=[
            Muerte(200, victima=C, atacante=None, lado_victima="CT"),  # caída
            Muerte(250, victima=B, atacante=A, lado_victima="T", lado_atacante="T"),  # fuego amigo
            Muerte(300, victima=A, atacante=A, lado_victima="T", lado_atacante="T"),  # suicidio
            Muerte(400, victima=D, atacante=B, lado_victima="CT", lado_atacante="T"),
        ],
        equipos=[Equipo(1, A, "T", 900), Equipo(1, B, "T", 900)],
    )
    a, b = (analizar(eventos, BUSCADOS).jugadores[j][0] for j in ("j1", "j2"))
    assert a.kills == 0 and a.murio and a.apertura is None
    assert b.kills == 1 and b.murio and b.apertura == "ganada"
    assert a.gano is False


def test_sin_equipo_el_lado_sale_de_las_muertes():
    eventos = Eventos(
        fines=[FinRonda(1000, "CT")],
        muertes=[Muerte(200, victima=C, atacante=A, lado_victima="T", lado_atacante="CT")],
    )
    a = analizar(eventos, BUSCADOS).jugadores["j1"][0]
    assert a.lado == "CT" and a.gano is True and a.compra == "pistola"
    b = analizar(eventos, BUSCADOS).jugadores["j2"][0]
    assert b.lado is None and b.gano is None and b.kast  # sobrevivió
