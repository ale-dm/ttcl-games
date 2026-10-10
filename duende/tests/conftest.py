import pytest

from app import api_ttcl
from app.config import get_config
from app.modelos import (
    ComparativaNivel,
    Desglose,
    FilaCompra,
    FilaLado,
    FilaMomento,
    FilaSinergia,
    JuegoContexto,
    JugadorContexto,
    MapaMuertes,
    MediasEquipo,
    MetricaNivel,
    MetricasRondas,
    Resumen,
    ResumenDemos,
    ResumenPeriodo,
    SeguimientoConsejo,
    Sesiones,
    Sinergias,
    ZonaMuerte,
)


@pytest.fixture(autouse=True)
def sin_gemini(monkeypatch):
    """Los tests nunca llaman a Gemini (sin clave, el chat contesta por reglas) ni a la API de verdad."""
    monkeypatch.setenv("GOOGLE_API_KEY", "")
    get_config.cache_clear()
    api_ttcl.cache.limpiar()
    monkeypatch.setattr(api_ttcl, "_cliente", None)
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
    seguimiento=None,
    nivel=None,
    demos=None,
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
                seguimiento=seguimiento or [],
                nivel=nivel,
                demos=demos,
            )
        ],
    )


def nivel_faceit(nivel: int = 6, elo: int | None = 1290, **metricas: tuple[float, float | None]) -> ComparativaNivel:
    """Nivel de FACEIT con 200 partidas de ese nivel y, por cada métrica que se pase, (lo normal en él, percentil)."""
    return ComparativaNivel(
        nivel=nivel,
        elo=elo,
        partidas=200,
        metricas=[
            MetricaNivel(metrica=m, referencia=ref, percentil=pct, muestras=200) for m, (ref, pct) in metricas.items()
        ],
    )


def seguido(
    insight: str, metrica: str, valor: float, dias: int, partidas: int, valor_desde: float | None
) -> SeguimientoConsejo:
    """Un consejo de hace `dias` días, con el valor de entonces y el de las `partidas` jugadas desde entonces."""
    return SeguimientoConsejo(
        insight=insight,
        metrica=metrica,
        valor=valor,
        dado_en="2026-09-20T18:00:00Z",
        dias=dias,
        partidas_desde=partidas,
        valor_desde=valor_desde,
    )


def rondas(**cambios) -> MetricasRondas:
    """Lo de las demos de un jugador normal: 20 partidas, rating 1,00, KAST 72 %..."""
    base = dict(
        partidas=20,
        rondas=440,
        rating=1.0,
        kast=72.0,
        adr=78.0,
        kpr=0.7,
        dpr=0.68,
        aperturas=60,
        aperturas_ganadas=30,
        apertura_pct=50.0,
        trades=60,
        trades_partida=3.0,
        muertes=300,
        muertes_tradeadas=84,
        tradeadas_pct=28.0,
        flash_partida=1.0,
        utilidad_ronda=6.0,
        winrate_rondas=50.0,
    )
    base.update(cambios)
    return MetricasRondas(**base)


def mapa_muertes(mapa: str, muertes: int, sin_trade: int, *zonas: tuple[str, int, int]) -> MapaMuertes:
    """Sus muertes en un mapa y, por zona, (nombre, muertes, sin trade)."""
    return MapaMuertes(
        mapa=mapa,
        partidas=8,
        muertes=muertes,
        sin_trade=sin_trade,
        zonas=[ZonaMuerte(zona=z, muertes=m, sin_trade=s) for z, m, s in zonas],
    )


def demos_cs2(
    metricas: MetricasRondas | None = None,
    equipo: MetricasRondas | None = None,
    lados=(("CT", 220, 1.0, 50.0), ("T", 220, 1.0, 50.0)),
    economia=(),
    mapas=(),
) -> ResumenDemos:
    """Demos de un jugador: sus números, los del resto del equipo, CT y T (lado, rondas, rating, winrate), compras
    (compra, rondas, winrate) y mapas. Por defecto, todo normal."""
    return ResumenDemos(
        metricas=metricas or rondas(),
        equipo=equipo or rondas(partidas=40, rondas=880),
        lados=[FilaLado(lado=l, rondas=n, ganadas=round(n * w / 100), winrate=w, rating=r, kast=72.0, adr=78.0)
               for l, n, r, w in lados],
        economia=[FilaCompra(compra=c, rondas=n, ganadas=round(n * w / 100), winrate=w, kpr=0.7) for c, n, w in economia],
        mapas=list(mapas),
    )


def en_periodo(periodo: str, resumen: Resumen, equipo: MediasEquipo | None = None) -> ResumenPeriodo:
    return ResumenPeriodo(periodo=periodo, resumen=resumen, equipo=equipo)


__all__ = [
    "resumen_cs2", "equipo_cs2", "companero", "momento", "sesiones", "jugador", "en_periodo", "seguido", "Desglose",
    "Sinergias",
]
