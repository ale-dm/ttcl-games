"""Contrato con la API Java (api/.../analisis/AnalisisModelos.java). En JSON, camelCase; en Python, snake_case."""

from typing import Literal

from pydantic import BaseModel, ConfigDict, Field
from pydantic.alias_generators import to_camel

Lado = Literal["CT", "T"]


class Base(BaseModel):
    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


class JugadorBuscado(Base):
    """Un jugador del equipo que puede estar en la partida. Se le busca por steamid y, si no, por su nick de FACEIT
    (en las demos de FACEIT, el nombre de cada uno es su nick)."""

    id: str
    steam_id: str | None = None
    nick: str | None = None


class PeticionAnalisis(Base):
    # La URL de la demo (la firmada de FACEIT) o el nombre de un fichero de la carpeta de demos: una de las dos.
    url: str | None = None
    archivo: str | None = None
    jugadores: list[JugadorBuscado] = Field(default_factory=list, max_length=10)


class Ronda(Base):
    numero: int
    ganador: Lado | None = None
    motivo: str | None = None


class RondaJugador(Base):
    ronda: int
    lado: Lado | None = None
    # Si su equipo ganó la ronda.
    gano: bool | None = None
    kills: int = 0
    asistencias: int = 0
    asistencias_flash: int = 0
    # Como mucho 100 por rival y ronda; sin el daño a compañeros.
    dano: int = 0
    dano_utilidad: int = 0
    murio: bool = False
    muerte_x: float | None = None
    muerte_y: float | None = None
    # Zona del mapa donde murió (el nombre que da el juego: "BombsiteA", "TopofMid"...).
    muerte_zona: str | None = None
    # Murió y alguien de su lado mató a su asesino en menos de 5 segundos.
    tradeado: bool = False
    # Kills a rivales que acababan de matar a un compañero (en menos de 5 segundos).
    trades: int = 0
    # El primer duelo de la ronda: lo ganó (la primera kill) o lo perdió (la primera muerte).
    apertura: Literal["ganada", "perdida"] | None = None
    # Valor de lo que llevaba encima al empezar a jugarse la ronda, y qué tipo de compra es.
    equipamiento: int | None = None
    compra: Literal["pistola", "eco", "forzada", "completa"] | None = None
    # Kill, asistencia, sobrevivir o ser tradeado.
    kast: bool = False


class JugadorAnalizado(Base):
    id: str
    steam_id: str
    rondas: list[RondaJugador]


class MuerteAnonima(Base):
    """Dónde murió alguien de la partida, sin decir quién: con todas se dibuja el mapa."""

    x: float
    y: float
    zona: str | None = None


class RespuestaAnalisis(Base):
    mapa: str | None = None
    rondas: list[Ronda]
    # Solo los buscados que estaban en la partida.
    jugadores: list[JugadorAnalizado]
    muertes: list[MuerteAnonima]
