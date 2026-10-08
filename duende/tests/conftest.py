import pytest

from app.config import get_config
from app.modelos import Desglose, JuegoContexto, JugadorContexto, MediasEquipo, Resumen


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


def jugador(slug: str, nombre: str, resumen: Resumen, equipo: MediasEquipo | None = None, desglose=None, rol=None):
    return JugadorContexto(
        slug=slug,
        nombre=nombre,
        juegos=[JuegoContexto(juego=resumen.juego, rol=rol, resumen=resumen, equipo=equipo, desglose=desglose or [])],
    )


__all__ = ["resumen_cs2", "equipo_cs2", "jugador", "Desglose"]
