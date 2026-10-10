"""Configuración del Duende, leída del entorno. Sin GOOGLE_API_KEY el chat responde con reglas, nunca falla."""

import os
from dataclasses import dataclass
from functools import lru_cache


@dataclass(frozen=True)
class Config:
    google_api_key: str
    gemini_model: str
    gemini_fallback_models: tuple[str, ...]
    gemini_timeout_ms: int
    max_tokens: int
    # Respuestas nuevas de Gemini en 24 h (las de reglas y las cacheadas no cuentan).
    daily_limit: int
    # API Java a la que Gemini puede consultar lo que no está en los resúmenes (P9). Vacía: sin consultas.
    api_url: str
    api_timeout_ms: int
    # Consultas a la API que puede hacer Gemini en cada respuesta (0: ninguna).
    max_consultas: int

    @property
    def consultas_activas(self) -> bool:
        return bool(self.api_url) and self.max_consultas > 0

    @property
    def gemini_configurado(self) -> bool:
        return bool(self.google_api_key)

    def modelos(self) -> list[str]:
        """El modelo principal y los de respaldo, en orden y sin repetir."""
        vistos: list[str] = []
        for m in (self.gemini_model, *self.gemini_fallback_models):
            if m and m not in vistos:
                vistos.append(m)
        return vistos


def _entero(nombre: str, defecto: int) -> int:
    try:
        return int(os.environ.get(nombre, defecto))
    except ValueError:
        return defecto


@lru_cache
def get_config() -> Config:
    return Config(
        google_api_key=os.environ.get("GOOGLE_API_KEY", "").strip(),
        gemini_model=os.environ.get("GEMINI_MODEL", "gemini-2.5-flash").strip(),
        gemini_fallback_models=tuple(
            m.strip() for m in os.environ.get("GEMINI_FALLBACK_MODELS", "gemini-2.5-pro").split(",") if m.strip()
        ),
        gemini_timeout_ms=_entero("GEMINI_TIMEOUT_MS", 20000),
        max_tokens=_entero("DUENDE_MAX_TOKENS", 1024),
        daily_limit=_entero("DUENDE_DAILY_LIMIT", 200),
        api_url=os.environ.get("API_URL", "http://localhost:8080").strip().rstrip("/"),
        api_timeout_ms=_entero("API_TIMEOUT_MS", 5000),
        max_consultas=_entero("DUENDE_MAX_CONSULTAS", 4),
    )
