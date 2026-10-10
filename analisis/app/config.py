"""Configuración del trabajador de análisis, leída del entorno."""

import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path


@dataclass(frozen=True)
class Config:
    # Carpeta con demos que se dejan a mano (descargadas desde la sala de la partida en FACEIT). Solo se leen demos
    # de dentro de ella.
    carpeta: Path
    # Tamaño máximo de una demo, comprimida o no (las de CS2 rondan los 100-300 MB descomprimidas).
    max_mb: int
    # Cuánto se espera a que termine una descarga.
    timeout_s: int

    @property
    def max_bytes(self) -> int:
        return self.max_mb * 1024 * 1024


def _entero(nombre: str, defecto: int) -> int:
    try:
        return int(os.environ.get(nombre, defecto))
    except ValueError:
        return defecto


# Por defecto, demos/ en la raíz del proyecto (en Docker, /demos).
CARPETA_POR_DEFECTO = Path(__file__).resolve().parents[2] / "demos"


@lru_cache
def get_config() -> Config:
    carpeta = os.environ.get("TTCL_DEMOS_DIR", "").strip()
    return Config(
        carpeta=Path(carpeta).resolve() if carpeta else CARPETA_POR_DEFECTO,
        max_mb=_entero("ANALISIS_MAX_MB", 800),
        timeout_s=_entero("ANALISIS_TIMEOUT_S", 300),
    )
