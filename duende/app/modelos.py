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
# Partidas de los últimos 7 días, de los últimos 30 o todas.
Periodo = Literal["7d", "30d", "todo"]


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


class FilaSinergia(Base):
    """Cómo le va con un compañero del equipo (o solo: sin slug ni nombre). «sin» son las demás partidas."""

    slug: str | None = None
    nombre: str | None = None
    partidas: int
    victorias: int = 0
    winrate: float | None = None
    kd: float | None = None
    partidas_sin: int = 0
    winrate_sin: float | None = None


class Sinergias(Base):
    """Con quién juega mejor. Solo compañeros del mismo bando y con un mínimo de partidas juntos (lo filtra la API)."""

    solo: FilaSinergia | None = None
    companeros: list[FilaSinergia] = Field(default_factory=list)


class FilaMomento(Base):
    """Cómo le va en un tipo de partida y, para comparar, en el resto («resto»). Claves: "1", "2", "3+" (orden en la
    sesión); "victoria", "derrota" (cómo acabó la anterior de la sesión); "manana", "tarde", "noche", "madrugada"."""

    clave: str
    partidas: int
    victorias: int = 0
    winrate: float | None = None
    kd: float | None = None
    partidas_resto: int = 0
    winrate_resto: float | None = None


class Sesiones(Base):
    """Sesiones: partidas seguidas, con menos de 45 minutos entre una y otra (lo calcula la API)."""

    sesiones: int = 0
    partidas_por_sesion: float | None = None
    por_orden: list[FilaMomento] = Field(default_factory=list)
    tras_resultado: list[FilaMomento] = Field(default_factory=list)
    por_franja: list[FilaMomento] = Field(default_factory=list)


class SeguimientoConsejo(Base):
    """Un consejo que se le dio (P6): la métrica de la que hablaba, su valor ese día («valor», con todas las partidas
    de entonces) y en las partidas jugadas desde entonces («valor_desde»). Lo calcula la API."""

    insight: str
    metrica: str
    valor: float
    dado_en: str
    dias: int
    partidas_desde: int = 0
    valor_desde: float | None = None


class MetricaNivel(Base):
    """Una métrica frente a las partidas de jugadores de su nivel de FACEIT (P8). «referencia» es lo normal en ese
    nivel: la mediana de sus partidas o, en las que salen de totales (entradas, clutches), el porcentaje de todas
    juntas. «percentil» es el % de esas partidas con un valor más bajo que el suyo, sin girar en las que es mejor bajo
    (muertes); null en las que salen de totales. Lo calcula la API."""

    metrica: str
    referencia: float
    percentil: float | None = None
    muestras: int = 0


class ComparativaNivel(Base):
    """Cómo queda frente a los jugadores de su nivel de FACEIT. «metricas», solo las que tienen muestra suficiente."""

    nivel: int
    elo: int | None = None
    partidas: int = 0
    metricas: list[MetricaNivel] = Field(default_factory=list)


# ─── Demos de CS2 (P12) ──────────────────────────────────────────────────────


class MetricasRondas(Base):
    """Lo que dicen las rondas de sus demos analizadas (lo calcula la API). Porcentajes de 0 a 100. «rating» es un
    rating propio al estilo del 2.0 de HLTV (1,00 es lo normal); «kast», el % de rondas con kill, asistencia,
    sobreviviendo o siendo tradeado; «tradeadas_pct», el % de sus muertes que un compañero vengó en menos de 5 s;
    «trades_partida», las veces por partida que él vengó a un compañero; «flash_partida», sus asistencias de flash por
    partida; «apertura_pct», el % de primeros duelos de la ronda que gana."""

    partidas: int = 0
    rondas: int = 0
    rating: float | None = None
    kast: float | None = None
    adr: float | None = None
    kpr: float | None = None
    dpr: float | None = None
    aperturas: int = 0
    aperturas_ganadas: int = 0
    apertura_pct: float | None = None
    trades: int = 0
    trades_partida: float | None = None
    muertes: int = 0
    muertes_tradeadas: int = 0
    tradeadas_pct: float | None = None
    flash_partida: float | None = None
    utilidad_ronda: float | None = None
    winrate_rondas: float | None = None


class FilaLado(Base):
    """De CT o de T: rondas, % ganadas y su rating, KAST y ADR en ellas."""

    lado: str
    rondas: int
    ganadas: int = 0
    winrate: float | None = None
    rating: float | None = None
    kast: float | None = None
    adr: float | None = None


class FilaCompra(Base):
    """Por tipo de compra de la ronda (pistola, eco, forzada, completa): rondas y % ganadas."""

    compra: str
    rondas: int
    ganadas: int = 0
    winrate: float | None = None
    kpr: float | None = None


class ZonaMuerte(Base):
    zona: str
    muertes: int
    sin_trade: int = 0


class MapaMuertes(Base):
    """Sus muertes en un mapa ("de_inferno"): en total, sin trade y por zona (las de más muertes sin trade primero)."""

    mapa: str
    partidas: int = 0
    muertes: int = 0
    sin_trade: int = 0
    zonas: list[ZonaMuerte] = Field(default_factory=list)


class ResumenDemos(Base):
    """Todo lo de sus demos (P12), con la media del resto del equipo («equipo», cada jugador pesa igual)."""

    metricas: MetricasRondas
    equipo: MetricasRondas | None = None
    lados: list[FilaLado] = Field(default_factory=list)
    economia: list[FilaCompra] = Field(default_factory=list)
    mapas: list[MapaMuertes] = Field(default_factory=list)


class ResumenPeriodo(Base):
    """Resumen de los últimos días (7 o 30), con la media del resto del equipo en esos mismos días."""

    periodo: Periodo
    resumen: Resumen
    equipo: MediasEquipo | None = None


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
    # Rol declarado en ese juego (entry, soporte... o solo, guardian...). Uno que no es del juego cuenta como ninguno.
    rol: str | None = None
    resumen: Resumen
    reciente: Resumen | None = None
    equipo: MediasEquipo | None = None
    desglose: list[Desglose] = Field(default_factory=list)
    sinergias: Sinergias | None = None
    sesiones: Sesiones | None = None
    # Cómo han ido los consejos que se le dieron (vacío si se piden por periodo).
    seguimiento: list[SeguimientoConsejo] = Field(default_factory=list)
    # Su nivel de FACEIT y lo normal en él (P8): con él se compara en vez de con las referencias fijas.
    nivel: ComparativaNivel | None = None
    # Lo que dicen las rondas de sus demos de CS2 analizadas (P12), si tiene alguna.
    demos: ResumenDemos | None = None


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


# ─── Informe de cada partida (P10) y semana (P11) ────────────────────────────


class Hecho(Base):
    """Algo especial de una partida frente a su historial. Lo calcula la API; aquí solo se cuenta."""

    tipo: str
    metrica: str | None = None
    valor: float | None = None
    referencia: float | None = None
    n: int | None = None
    clave: str | None = None


class ItemInforme(Base):
    # Lo elige la API para casar la respuesta (una partida con dos del equipo son dos informes).
    id: str
    juego: Juego
    hechos: list[Hecho] = Field(default_factory=list)


class PeticionInformes(Base):
    lang: Idioma = "es"
    items: list[ItemInforme] = Field(default_factory=list, max_length=200)


class TextoInforme(Base):
    id: str
    texto: str | None = None


class RespuestaInformes(Base):
    items: list[TextoInforme]


class FilaSemana(Base):
    slug: str
    nombre: str
    partidas: int
    victorias: int = 0
    winrate: float | None = None
    kd: float | None = None


class SemanaJuego(Base):
    """Los últimos 7 días de un juego: el mejor y el peor ya los elige la API (con un mínimo de partidas)."""

    juego: Juego
    partidas: int = 0
    jugadores: list[FilaSemana] = Field(default_factory=list)
    mejor: FilaSemana | None = None
    peor: FilaSemana | None = None


class PeticionSemana(Base):
    lang: Idioma = "es"
    juegos: list[SemanaJuego] = Field(default_factory=list)


class RespuestaSemana(Base):
    texto: str


# ─── Chat ────────────────────────────────────────────────────────────────────


class Mensaje(Base):
    rol: Literal["usuario", "duende"]
    texto: str = Field(min_length=1, max_length=2000)


class JuegoContexto(Base):
    juego: Juego
    rol: str | None = None
    resumen: Resumen
    reciente: Resumen | None = None
    equipo: MediasEquipo | None = None
    desglose: list[Desglose] = Field(default_factory=list)
    sinergias: Sinergias | None = None
    sesiones: Sesiones | None = None
    # Los últimos 7 y 30 días, los que tengan partidas: para "¿cómo voy esta semana?". Lo demás, con todas.
    periodos: list[ResumenPeriodo] = Field(default_factory=list)
    seguimiento: list[SeguimientoConsejo] = Field(default_factory=list)
    nivel: ComparativaNivel | None = None
    demos: ResumenDemos | None = None


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
    # El periodo que se ve en la página: se usa si la pregunta no dice otro ("esta semana", "este mes"...).
    periodo: Periodo | None = None
    # Hoy en la zona del equipo ("2026-10-10"), para que Gemini sepa qué días pedir al consultar la API (P9).
    hoy: str | None = None


class RespuestaChat(Base):
    respuesta: str
    origen: Literal["gemini", "reglas"]
    modelo: str | None = None
    # De qué iba la pregunta según las reglas (mejorar, companeros... o ayuda si no la entienden), conteste quien
    # conteste. La web la devuelve con cada valoración: así se ve qué temas se responden peor.
    intencion: str | None = None
    sugerencias: list[str] = Field(default_factory=list)
