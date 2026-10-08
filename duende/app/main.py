"""API del Duende. La llama solo la API Java (no se expone al navegador)."""

import logging

from fastapi import FastAPI

from . import chat
from .config import get_config
from .insights import generar_insights
from .modelos import (
    ItemLote,
    PeticionChat,
    PeticionInsights,
    PeticionInsightsLote,
    RespuestaChat,
    RespuestaInsights,
    RespuestaInsightsLote,
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
