import pytest

from app.config import get_config
from app.modelos import (
    Desglose,
    FilaMomento,
    FilaSinergia,
    JuegoContexto,
    JugadorContexto,
    MediasEquipo,
    Resumen,
    ResumenPeriodo,
    Sesiones,
    Sinergias,
)


@pytest.fixture(autouse=True)
def sin_gemini(monkeypatch):
    """Los tests nunca llaman a Gemini: sin clave, el chat contesta por reglas."""
    monkeypatch.setenv("GOOGLE_API_KEY", "")
    get_config.cache_clear()
    yield
    get_config.cache_clear()


def resumen_cs2(**cambios) -> Resumen:
    base = dict(
        juego="cs2",
        partidas=30,
        victorias=15,
        derrotas=15,
        winrate=50.0,
        kd=1.0,
        kills_media=18.0,
        muertes_media=18.0,
        asistencias_media=5.0,
        datos_medios={"adr": 80.0, "hs_pct": 45.0, "kr": 0.7, "entry_pct": 50.0, "clutch_pct": 25.0},
        forma="VDVDVDVDVD",
    )
    base.update(cambios)
    return Resumen(**base)


def equipo_cs2(**cambios) -> MediasEquipo:
    base = dict(
        jugadores=2,
        winrate=50.0,
        kd=1.0,
        kills_media=18.0,
        muertes_media=18.0,
        asistencias_media=5.0,
        datos_medios={"adr": 80.0, "hs_pct": 45.0, "kr": 0.7, "entry_pct": 50.0, "clutch_pct": 25.0},
    )
    base.update(cambios)
    return MediasEquipo(**base)


def companero(nombre: str, partidas: int, winrate: float, partidas_sin: int, winrate_sin: float | None) -> FilaSinergia:
    """Fila de sinergia con un compañero: tantas partidas con él y tantas sin él, con su winrate."""
    return FilaSinergia(
        slug=nombre.lower().replace(" ", ""),
        nombre=nombre,
        partidas=partidas,
        victorias=round(partidas * winrate / 100),
        winrate=winrate,
        kd=1.0,
        partidas_sin=partidas_sin,
        winrate_sin=winrate_sin,
    )


def momento(clave: str, partidas: int, winrate: float, partidas_resto: int, winrate_resto: float | None) -> FilaMomento:
    """Fila de las sesiones: tantas partidas de ese tipo y tantas del resto, con su winrate."""
    return FilaMomento(
        clave=clave,
        partidas=partidas,
        victorias=round(partidas * winrate / 100),
        winrate=winrate,
        kd=1.0,
        partidas_resto=partidas_resto,
        winrate_resto=winrate_resto,
    )


def sesiones(n: int = 20, por_orden=(), tras_resultado=(), por_franja=()) -> Sesiones:
    """Sesiones con las filas que se pasen; por defecto, 20 sesiones de 2,5 partidas sin nada raro."""
    return Sesiones(
        sesiones=n,
        partidas_por_sesion=2.5,
        por_orden=list(por_orden),
        tras_resultado=list(tras_resultado),
        por_franja=list(por_franja),
    )


def jugador(
    slug: str,
    nombre: str,
    resumen: Resumen,
    equipo: MediasEquipo | None = None,
    desglose=None,
    rol=None,
    sinergias=None,
    sesiones=None,
    periodos=None,
):
    return JugadorContexto(
        slug=slug,
        nombre=nombre,
        juegos=[
            JuegoContexto(
                juego=resumen.juego,
                rol=rol,
                resumen=resumen,
                equipo=equipo,
                desglose=desglose or [],
                sinergias=sinergias,
                sesiones=sesiones,
                periodos=periodos or [],
            )
        ],
    )


def en_periodo(periodo: str, resumen: Resumen, equipo: MediasEquipo | None = None) -> ResumenPeriodo:
    return ResumenPeriodo(periodo=periodo, resumen=resumen, equipo=equipo)


__all__ = [
    "resumen_cs2", "equipo_cs2", "companero", "momento", "sesiones", "jugador", "en_periodo", "Desglose", "Sinergias"
]
