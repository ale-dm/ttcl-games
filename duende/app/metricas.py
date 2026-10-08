"""Catálogo de métricas por juego: de dónde sale cada valor, hacia dónde es mejor y una referencia orientativa.

Las referencias son valores de un jugador medio-bueno de FACEIT (CS2) o de partidas casuales (SMITE 2). Sirven para
no dar por bueno algo solo porque el resto del equipo va igual de mal.
"""

from dataclasses import dataclass
from typing import Literal

from .modelos import Formato, Idioma, Juego, MediasEquipo, Resumen

CAMPOS_COMUNES = {"winrate", "kd", "kills_media", "muertes_media", "asistencias_media"}


@dataclass(frozen=True)
class Metrica:
    clave: str
    mejor: Literal["alto", "bajo"]
    formato: Formato
    nombre_es: str
    nombre_en: str
    referencia: float | None = None

    def nombre(self, lang: Idioma) -> str:
        return self.nombre_es if lang == "es" else self.nombre_en

    def valor(self, fuente: Resumen | MediasEquipo | None) -> float | None:
        if fuente is None:
            return None
        if self.clave in CAMPOS_COMUNES:
            return getattr(fuente, self.clave)
        return fuente.datos_medios.get(self.clave)


METRICAS: dict[Juego, list[Metrica]] = {
    "cs2": [
        Metrica("winrate", "alto", "pct", "Winrate", "Win rate", 50),
        Metrica("kd", "alto", "dec", "K/D", "K/D", 1.0),
        Metrica("adr", "alto", "int", "ADR", "ADR", 80),
        Metrica("hs_pct", "alto", "pct", "% headshot", "Headshot %", 45),
        Metrica("kr", "alto", "dec", "Kills / ronda", "Kills / round", 0.7),
        Metrica("entry_pct", "alto", "pct", "Éxito de entrada", "Entry success", 50),
        Metrica("clutch_pct", "alto", "pct", "Clutches ganados", "Clutches won", 25),
        Metrica("kills_media", "alto", "dec", "Kills / partida", "Kills / match"),
        Metrica("muertes_media", "bajo", "dec", "Muertes / partida", "Deaths / match"),
        Metrica("asistencias_media", "alto", "dec", "Asistencias / partida", "Assists / match"),
        Metrica("dano_utilidad", "alto", "int", "Daño de utilidad", "Utility damage"),
    ],
    "smite2": [
        Metrica("winrate", "alto", "pct", "Winrate", "Win rate", 50),
        Metrica("kda", "alto", "dec", "KDA", "KDA", 2.0),
        Metrica("kd", "alto", "dec", "K/D", "K/D", 1.0),
        Metrica("dano_min", "alto", "int", "Daño / min", "Damage / min"),
        Metrica("oro_min", "alto", "int", "Oro / min", "Gold / min"),
        Metrica("mitigado", "alto", "int", "Daño mitigado", "Damage mitigated"),
        Metrica("kills_media", "alto", "dec", "Kills / partida", "Kills / match"),
        Metrica("muertes_media", "bajo", "dec", "Muertes / partida", "Deaths / match"),
        Metrica("asistencias_media", "alto", "dec", "Asistencias / partida", "Assists / match"),
    ],
}

NOMBRE_JUEGO: dict[Juego, str] = {"cs2": "Counter-Strike 2", "smite2": "SMITE 2"}

# ─── Roles ───────────────────────────────────────────────────────────────────
# Cuánto cuenta cada métrica según el rol. Las que no aparecen cuentan lo normal (1). Los roles son los mismos que
# acepta la API (Juego.java) y que traduce la web (textos.ts).

NO_SE_JUZGA = 0.0  # Puede tenerla baja y estar haciendo su trabajo: nunca es una debilidad.
TOLERA = 0.5  # Se acepta que vaya algo por debajo: hace falta el doble de distancia para avisar.
PESA_MAS = 1.5  # Es lo suyo: se avisa antes, se reconoce antes y va primero.

AJUSTES_ROL: dict[Juego, dict[str, dict[str, float]]] = {
    "cs2": {
        "entry": {"muertes_media": TOLERA, "kd": TOLERA, "clutch_pct": TOLERA, "entry_pct": PESA_MAS},
        "awp": {
            "hs_pct": NO_SE_JUZGA,
            "asistencias_media": TOLERA,
            "dano_utilidad": TOLERA,
            "kr": PESA_MAS,
            "entry_pct": PESA_MAS,
        },
        "soporte": {
            "kills_media": NO_SE_JUZGA,
            "kd": TOLERA,
            "adr": TOLERA,
            "kr": TOLERA,
            "entry_pct": TOLERA,
            "asistencias_media": PESA_MAS,
            "dano_utilidad": PESA_MAS,
        },
        "lurker": {
            "asistencias_media": NO_SE_JUZGA,
            "entry_pct": NO_SE_JUZGA,
            "dano_utilidad": TOLERA,
            "clutch_pct": PESA_MAS,
            "kr": PESA_MAS,
        },
        "igl": {
            "kills_media": TOLERA,
            "kd": TOLERA,
            "adr": TOLERA,
            "kr": TOLERA,
            "hs_pct": TOLERA,
            "winrate": PESA_MAS,
            "dano_utilidad": PESA_MAS,
        },
        "rifler": {"adr": PESA_MAS, "kr": PESA_MAS},
    },
    "smite2": {
        "solo": {"asistencias_media": TOLERA, "mitigado": PESA_MAS},
        "jungla": {"mitigado": NO_SE_JUZGA, "kills_media": PESA_MAS},
        "mid": {"mitigado": NO_SE_JUZGA, "dano_min": PESA_MAS},
        "guardian": {
            "kills_media": NO_SE_JUZGA,
            "dano_min": NO_SE_JUZGA,
            "oro_min": NO_SE_JUZGA,
            "kd": TOLERA,
            "asistencias_media": PESA_MAS,
            "mitigado": PESA_MAS,
        },
        "carry": {"mitigado": NO_SE_JUZGA, "dano_min": PESA_MAS, "oro_min": PESA_MAS},
    },
}


def metrica(juego: Juego, clave: str) -> Metrica | None:
    return next((m for m in METRICAS[juego] if m.clave == clave), None)


def rol_de(juego: Juego, rol: str | None) -> str | None:
    """El rol si es uno de los de ese juego; si no se ha dicho o no es de ese juego, None."""
    return rol if rol in AJUSTES_ROL[juego] else None


def factor_rol(juego: Juego, rol: str | None, clave: str) -> float:
    """Cuánto cuenta la métrica `clave` para ese rol (1 si no hay rol o no la ajusta)."""
    return AJUSTES_ROL[juego].get(rol or "", {}).get(clave, 1.0)


def formatear(valor: float | None, formato: Formato | None, lang: Idioma) -> str:
    """Número para leer: 37,1 % / 1,24 en español; 37.1% / 1.24 en inglés."""
    if valor is None:
        return "—"
    if formato == "int":
        entero = round(valor)
        if lang == "en":
            return f"{entero:,}"
        # Como Intl es-ES: sin separador hasta 9999, con punto a partir de 10 000.
        return f"{entero:,}".replace(",", ".") if abs(entero) >= 10000 else str(entero)

    texto = f"{valor:.1f}" if formato == "pct" else f"{valor:.2f}"
    if lang == "es":
        texto = texto.replace(".", ",")
    if formato == "pct":
        return f"{texto} %" if lang == "es" else f"{texto}%"
    return texto
