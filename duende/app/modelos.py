"""Contrato con la API Java. El Duende nunca ve partidas en bruto: solo resúmenes ya calculados.

En JSON los campos van en camelCase (como los manda Java); en Python, en snake_case.
"""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

Juego = Literal["cs2", "smite2"]
Idioma = Literal["es", "en"]
Nivel = Literal["alto", "medio", "bien", "info"]
Formato = Literal["pct", "dec", "int"]


class Base(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class Resumen(Base):
    juego: Juego
    partidas: int
    victorias: int = 0
    derrotas: int = 0
    winrate: float | None = None
    kd: float | None = None
    kills_media: float | None = None
    muertes_media: float | None = None
    asistencias_media: float | None = None
    # Medias de lo específico de cada juego: adr, hs_pct, entry_pct... (CS2) o dano, oro_min, kda... (SMITE 2).
    datos_medios: dict[str, float] = Field(default_factory=dict)
    # Últimos resultados, de más reciente a más antiguo: V victoria, D derrota, ? sin dato.
    forma: str = ""
    ultima_partida: str | None = None


class MediasEquipo(Base):
    """Media del resto del equipo en ese juego (sin contar al jugador que se analiza)."""

    jugadores: int
    winrate: float | None = None
    kd: float | None = None
    kills_media: float | None = None
    muertes_media: float | None = None
    asistencias_media: float | None = None
    datos_medios: dict[str, float] = Field(default_factory=dict)


class Desglose(Base):
    """Rendimiento por mapa (CS2) o por dios (SMITE 2)."""

    clave: str
    partidas: int
    victorias: int
    winrate: float | None = None
    kd: float | None = None


class JugadorRef(Base):
    slug: str
    nombre: str


class Barra(Base):
    etiqueta: str
    valor: float
    tuyo: bool = False


class Insight(Base):
    id: str
    nivel: Nivel
    metrica: str | None = None
    titulo: str
    texto: str
    consejo: str | None = None
    barras: list[Barra] = Field(default_factory=list)
    formato: Formato | None = None


class PeticionInsights(Base):
    lang: Idioma = "es"
    jugador: JugadorRef
    juego: Juego
    resumen: Resumen
    reciente: Resumen | None = None
    equipo: MediasEquipo | None = None
    desglose: list[Desglose] = Field(default_factory=list)


class RespuestaInsights(Base):
    insights: list[Insight]


class PeticionInsightsLote(Base):
    items: list[PeticionInsights] = Field(max_length=100)


class ItemLote(Base):
    slug: str
    juego: Juego
    insights: list[Insight]


class RespuestaInsightsLote(Base):
    items: list[ItemLote]


# ─── Chat ────────────────────────────────────────────────────────────────────


class Mensaje(Base):
    rol: Literal["usuario", "duende"]
    texto: str = Field(min_length=1, max_length=2000)


class JuegoContexto(Base):
    juego: Juego
    resumen: Resumen
    reciente: Resumen | None = None
    equipo: MediasEquipo | None = None
    desglose: list[Desglose] = Field(default_factory=list)


class JugadorContexto(Base):
    slug: str
    nombre: str
    juegos: list[JuegoContexto] = Field(default_factory=list)


class PeticionChat(Base):
    lang: Idioma = "es"
    mensajes: list[Mensaje] = Field(min_length=1, max_length=30)
    # Slugs de los jugadores de los que va la conversación: ninguno (equipo), uno (perfil) o dos (comparación).
    foco: list[str] = Field(default_factory=list, max_length=2)
    # Todo el equipo con sus datos, también para preguntas generales ("¿quién tiene mejor K/D?").
    equipo: list[JugadorContexto] = Field(default_factory=list, max_length=50)
    juego: Juego | None = None


class RespuestaChat(Base):
    respuesta: str
    origen: Literal["gemini", "reglas"]
    modelo: str | None = None
    sugerencias: list[str] = Field(default_factory=list)
