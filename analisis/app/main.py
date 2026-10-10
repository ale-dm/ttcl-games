"""Trabajador de análisis de demos de CS2 (P12). Lo llama solo la API Java (no se expone al navegador): le da la URL de
una demo o el nombre de un fichero de la carpeta de demos y los jugadores del equipo que buscar, y devuelve lo que
hizo cada uno en cada ronda. No guarda nada: la demo se borra al acabar y los datos los guarda la API.
"""

import logging
import unicodedata

from fastapi import FastAPI
from fastapi.responses import JSONResponse

from . import demo
from .config import get_config
from .descarga import DemoNoValida, DescargaFallida, demo_local
from .modelos import (
    JugadorAnalizado,
    JugadorBuscado,
    MuerteAnonima,
    PeticionAnalisis,
    RespuestaAnalisis,
    Ronda,
    RondaJugador,
)
from .rondas import analizar as analizar_eventos

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("analisis")

app = FastAPI(
    title="TTCL Games · Análisis de demos",
    version="0.1.0",
    description="Lee demos de CS2 y devuelve lo que hizo cada jugador del equipo en cada ronda.",
)


def _crear_parser(ruta: str) -> demo.Parser:
    from demoparser2 import DemoParser  # Importado aquí: los tests no lo necesitan.

    return DemoParser(ruta)


crear_parser = _crear_parser


def _normalizar(nick: str | None) -> str:
    sin_tildes = unicodedata.normalize("NFKD", (nick or "").strip().lower())
    return "".join(c for c in sin_tildes if not unicodedata.combining(c))


def buscar(jugadores: list[JugadorBuscado], en_partida: list[tuple[str, str | None]]) -> dict[str, str]:
    """Steamid de la demo → id del jugador buscado: por steamid y, si no está, por nick."""
    steamids = {s for s, _ in en_partida}
    por_nick = {_normalizar(n): s for s, n in en_partida if n}
    encontrados: dict[str, str] = {}
    for j in jugadores:
        s = j.steam_id if j.steam_id in steamids else por_nick.get(_normalizar(j.nick)) if j.nick else None
        if s and s not in encontrados:
            encontrados[s] = j.id
    return encontrados


def analizar_parser(parser: demo.Parser, jugadores: list[JugadorBuscado]) -> RespuestaAnalisis:
    mapa, eventos = demo.leer(parser)
    if not eventos.fines:
        raise DemoNoValida("La demo no tiene ninguna ronda jugada.")
    buscados = buscar(jugadores, demo.jugadores_de(parser))
    r = analizar_eventos(eventos, buscados)
    steam_de = {id_: s for s, id_ in buscados.items()}
    return RespuestaAnalisis(
        mapa=mapa,
        rondas=[Ronda(numero=i, ganador=f.ganador, motivo=f.motivo) for i, f in enumerate(r.fines, start=1)],
        jugadores=[
            JugadorAnalizado(
                id=id_,
                steam_id=steam_de[id_],
                rondas=[RondaJugador.model_validate(vars(f)) for f in filas],
            )
            for id_, filas in r.jugadores.items()
        ],
        muertes=[MuerteAnonima(x=x, y=y, zona=zona) for x, y, zona in r.muertes],
    )


@app.get("/health")
def health() -> dict:
    return {"ok": True}


@app.post("/v1/analizar", response_model=RespuestaAnalisis, response_model_by_alias=True)
def analizar(peticion: PeticionAnalisis):
    cfg = get_config()
    try:
        with demo_local(cfg, url=peticion.url, archivo=peticion.archivo) as ruta:
            respuesta = analizar_parser(crear_parser(str(ruta)), peticion.jugadores)
    except DemoNoValida as err:
        log.info("Demo no válida: %s", err)
        return JSONResponse(status_code=422, content={"error": str(err)})
    except DescargaFallida as err:
        log.warning("Descarga fallida: %s", err)
        return JSONResponse(status_code=503, content={"error": str(err)})
    except Exception as err:  # noqa: BLE001 - demoparser2 lanza sus propias excepciones con una demo rota
        log.exception("No se pudo leer la demo")
        return JSONResponse(status_code=422, content={"error": f"No se pudo leer la demo ({type(err).__name__})."})
    log.info(
        "Demo de %s: %d rondas, %d jugadores del equipo",
        respuesta.mapa,
        len(respuesta.rondas),
        len(respuesta.jugadores),
    )
    return respuesta
