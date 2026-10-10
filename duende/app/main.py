"""API del Duende. La llama solo la API Java (no se expone al navegador)."""

import logging

from fastapi import FastAPI

from . import chat
from .config import get_config
from .informes import texto_informe, texto_semana
from .insights import generar_insights
from .modelos import (
    ItemLote,
    PeticionChat,
    PeticionInformes,
    PeticionInsights,
    PeticionInsightsLote,
    PeticionSemana,
    RespuestaChat,
    RespuestaInformes,
    RespuestaInsights,
    RespuestaInsightsLote,
    RespuestaSemana,
    TextoInforme,
)

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")

app = FastAPI(
    title="TTCL Games · Duende",
    version="0.1.0",
    description="Recomendaciones a partir de estadísticas y chatbot del Duende.",
)


@app.get("/health")
def health() -> dict:
    cfg = get_config()
    return {"ok": True, "gemini": cfg.gemini_configurado, "modelo": cfg.gemini_model if cfg.gemini_configurado else None}


@app.post("/v1/insights", response_model=RespuestaInsights)
def insights(peticion: PeticionInsights) -> RespuestaInsights:
    return RespuestaInsights(insights=generar_insights(peticion))


@app.post("/v1/insights/lote", response_model=RespuestaInsightsLote)
def insights_lote(peticion: PeticionInsightsLote) -> RespuestaInsightsLote:
    return RespuestaInsightsLote(
        items=[ItemLote(slug=p.jugador.slug, juego=p.juego, insights=generar_insights(p)) for p in peticion.items]
    )


@app.post("/v1/chat", response_model=RespuestaChat)
def responder(peticion: PeticionChat) -> RespuestaChat:
    return chat.responder(peticion)


@app.post("/v1/informes", response_model=RespuestaInformes)
def informes(peticion: PeticionInformes) -> RespuestaInformes:
    """Lo que dice el Duende de cada partida (P10), a partir de lo que la API ha visto de especial en ella."""
    return RespuestaInformes(
        items=[TextoInforme(id=i.id, texto=texto_informe(i, peticion.lang)) for i in peticion.items]
    )


@app.post("/v1/semana", response_model=RespuestaSemana)
def semana(peticion: PeticionSemana) -> RespuestaSemana:
    """Lo que dice el Duende de la semana (P11): el mejor y el peor de cada juego."""
    return RespuestaSemana(texto=texto_semana(peticion.juegos, peticion.lang))
