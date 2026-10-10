"""Lo que pasó en cada ronda, a partir de los eventos de la demo ya leídos (demo.py). Funciones puras: no saben nada
de demoparser2 ni de ficheros, así que se prueban con eventos escritos a mano.

Por ronda y jugador del equipo: lado, si su equipo ganó la ronda, kills, asistencias (y cuántas de flash), daño (como
mucho 100 por rival y ronda, sin contar el daño a compañeros), daño de utilidad, si murió (dónde y si le tradearon),
trades que dio, si ganó o perdió el duelo de apertura, lo que llevaba encima al empezar y qué tipo de compra era, y si
la ronda cuenta para el KAST (kill, asistencia, sobrevivir o ser tradeado).
"""

from dataclasses import dataclass, field
from typing import Literal

Lado = Literal["CT", "T"]
Compra = Literal["pistola", "eco", "forzada", "completa"]

# Las demos de CS2 van a 64 ticks por segundo.
TICKS_POR_SEGUNDO = 64
# Un trade es matar al que acaba de matar a un compañero en menos de 5 segundos (lo mismo que usan HLTV o Leetify).
VENTANA_TRADE = 5 * TICKS_POR_SEGUNDO
# Daño que cuenta como mucho por rival y ronda (lo que tiene de vida): un tiro de AWP de 400 son 100.
MAX_DANO_RIVAL = 100
# Rondas de pistola en MR12 (lo que juega FACEIT): la primera de cada mitad. La prórroga no tiene.
RONDAS_PISTOLA = (1, 13)
# Valor del equipo al acabar el tiempo de compra: por debajo, eco; hasta COMPLETA, forzada; desde COMPLETA, completa.
ECO = 1500
COMPLETA = 3500
# Armas cuyo daño es de utilidad.
UTILIDAD = {"hegrenade", "inferno", "molotov", "incgrenade", "flashbang", "smokegrenade", "decoy"}


@dataclass(frozen=True)
class FinRonda:
    tick: int
    ganador: Lado | None
    motivo: str | None = None


@dataclass(frozen=True)
class Muerte:
    tick: int
    victima: str | None
    atacante: str | None
    asistente: str | None = None
    # La asistencia fue de flash (el asistente cegó a la víctima).
    flash: bool = False
    lado_victima: Lado | None = None
    lado_atacante: Lado | None = None
    x: float | None = None
    y: float | None = None
    zona: str | None = None


@dataclass(frozen=True)
class Dano:
    tick: int
    atacante: str | None
    victima: str | None
    dano: int
    arma: str | None = None
    lado_atacante: Lado | None = None
    lado_victima: Lado | None = None


@dataclass(frozen=True)
class Equipo:
    """Lo que llevaba un jugador al acabar el tiempo de congelación de una ronda, y en qué lado jugaba."""

    ronda: int
    jugador: str
    lado: Lado | None
    valor: int | None


@dataclass
class Eventos:
    """Lo que hace falta de una demo, ya sin calentamiento: en orden de tick."""

    fines: list[FinRonda]
    # Tick en que acaba la congelación (empieza a jugarse) de cada ronda; puede faltar alguna.
    inicios: list[int] = field(default_factory=list)
    muertes: list[Muerte] = field(default_factory=list)
    danos: list[Dano] = field(default_factory=list)
    equipos: list[Equipo] = field(default_factory=list)


@dataclass
class RondaJugador:
    ronda: int
    lado: Lado | None = None
    gano: bool | None = None
    kills: int = 0
    asistencias: int = 0
    asistencias_flash: int = 0
    dano: int = 0
    dano_utilidad: int = 0
    murio: bool = False
    muerte_x: float | None = None
    muerte_y: float | None = None
    muerte_zona: str | None = None
    tradeado: bool = False
    trades: int = 0
    apertura: Literal["ganada", "perdida"] | None = None
    equipamiento: int | None = None
    compra: Compra | None = None
    kast: bool = False


@dataclass
class Resultado:
    fines: list[FinRonda]
    # Por jugador buscado (su id en la petición), una fila por ronda.
    jugadores: dict[str, list[RondaJugador]]
    # Dónde murió cada uno (de los diez), sin decir quién: para dibujar el mapa.
    muertes: list[tuple[float, float, str | None]]


def limites(fines: list[FinRonda], inicios: list[int]) -> list[tuple[int | None, int]]:
    """Para cada ronda, el tick en que empieza a jugarse (si se sabe) y hasta qué tick le pertenecen los eventos.

    Lo que pasa después de que acabe una ronda (las kills de salida) es de esa ronda hasta que empieza la siguiente.
    """
    resultado: list[tuple[int | None, int]] = []
    anterior = -1
    for i, fin in enumerate(fines):
        inicio = next((t for t in inicios if anterior < t <= fin.tick), None)
        if i + 1 < len(fines):
            hasta = next((t for t in inicios if fin.tick < t <= fines[i + 1].tick), fin.tick)
        else:
            hasta = 2**62
        resultado.append((inicio, hasta))
        anterior = fin.tick
    return resultado


def ronda_de(tick: int, limites_rondas: list[tuple[int | None, int]]) -> int | None:
    """Número de ronda (desde 1) de un evento, o None si es de después de la última."""
    for i, (_, hasta) in enumerate(limites_rondas):
        if tick <= hasta:
            return i + 1
    return None


def tipo_compra(ronda: int, valor: int | None) -> Compra | None:
    if ronda in RONDAS_PISTOLA:
        return "pistola"
    if valor is None:
        return None
    if valor < ECO:
        return "eco"
    return "forzada" if valor < COMPLETA else "completa"


def analizar(eventos: Eventos, buscados: dict[str, str]) -> Resultado:
    """Rondas de los jugadores buscados (steamid de la demo → id en la petición)."""
    lims = limites(eventos.fines, eventos.inicios)
    n = len(eventos.fines)
    filas: dict[str, list[RondaJugador]] = {
        id_: [RondaJugador(ronda=r) for r in range(1, n + 1)] for id_ in buscados.values()
    }

    def fila(steamid: str | None, ronda: int | None) -> RondaJugador | None:
        if steamid is None or ronda is None or steamid not in buscados:
            return None
        return filas[buscados[steamid]][ronda - 1]

    # Lado y equipo de cada uno al empezar la ronda.
    for e in eventos.equipos:
        if f := fila(e.jugador, e.ronda if 1 <= e.ronda <= n else None):
            f.lado = e.lado or f.lado
            f.equipamiento = e.valor

    muertes = sorted(eventos.muertes, key=lambda m: m.tick)
    por_ronda: dict[int, list[Muerte]] = {}
    for m in muertes:
        if (r := ronda_de(m.tick, lims)) is not None:
            por_ronda.setdefault(r, []).append(m)

    for r, lista in por_ronda.items():
        primera = True
        for i, m in enumerate(lista):
            enemigo = _es_enemigo(m)
            victima = fila(m.victima, r)
            if victima:
                victima.murio = True
                victima.muerte_x, victima.muerte_y, victima.muerte_zona = m.x, m.y, m.zona
                victima.lado = victima.lado or m.lado_victima
            atacante = fila(m.atacante, r) if enemigo else None
            if atacante:
                atacante.kills += 1
                atacante.lado = atacante.lado or m.lado_atacante
            if enemigo and primera:
                primera = False
                if atacante:
                    atacante.apertura = "ganada"
                if victima:
                    victima.apertura = "perdida"
            if m.asistente and m.asistente != m.atacante and (asist := fila(m.asistente, r)):
                if asist.lado is None or asist.lado != m.lado_victima:
                    asist.asistencias += 1
                    if m.flash:
                        asist.asistencias_flash += 1
            # ¿Alguien de su lado mató al que le mató en menos de VENTANA_TRADE? Entonces le tradearon.
            if enemigo:
                for siguiente in lista[i + 1 :]:
                    if siguiente.tick - m.tick > VENTANA_TRADE:
                        break
                    if siguiente.victima == m.atacante and _vengado_por_su_lado(m, siguiente):
                        if victima:
                            victima.tradeado = True
                        if vengador := fila(siguiente.atacante, r):
                            vengador.trades += 1
                        break

    # Daño: como mucho MAX_DANO_RIVAL por rival y ronda, sin contar el que se hace a los de su lado.
    acumulado: dict[tuple[int, str, str], int] = {}
    for d in eventos.danos:
        r = ronda_de(d.tick, lims)
        f = fila(d.atacante, r)
        if not f or not d.victima or d.victima == d.atacante or d.dano <= 0:
            continue
        if d.lado_atacante and d.lado_victima and d.lado_atacante == d.lado_victima:
            continue
        clave = (r, d.atacante, d.victima)  # type: ignore[assignment]
        cuenta = min(d.dano, MAX_DANO_RIVAL - acumulado.get(clave, 0))  # type: ignore[arg-type]
        if cuenta <= 0:
            continue
        acumulado[clave] = acumulado.get(clave, 0) + cuenta  # type: ignore[index]
        f.dano += cuenta
        if (d.arma or "").lower() in UTILIDAD:
            f.dano_utilidad += cuenta

    for r, fin in enumerate(eventos.fines, start=1):
        for lista_filas in filas.values():
            f = lista_filas[r - 1]
            f.gano = None if fin.ganador is None or f.lado is None else fin.ganador == f.lado
            f.compra = tipo_compra(r, f.equipamiento)
            f.kast = f.kills > 0 or f.asistencias > 0 or not f.murio or f.tradeado

    anonimas = [(m.x, m.y, m.zona) for m in muertes if m.x is not None and m.y is not None and ronda_de(m.tick, lims)]
    return Resultado(fines=eventos.fines, jugadores=filas, muertes=anonimas)  # type: ignore[arg-type]


def _es_enemigo(m: Muerte) -> bool:
    """Una kill de verdad: ni suicidio, ni el mundo, ni un compañero."""
    if not m.atacante or m.atacante == m.victima:
        return False
    return not (m.lado_atacante and m.lado_victima and m.lado_atacante == m.lado_victima)


def _vengado_por_su_lado(muerte: Muerte, siguiente: Muerte) -> bool:
    """Si quien mató al asesino era del lado de la primera víctima (sin lados, se da por hecho)."""
    if not siguiente.atacante or siguiente.atacante == siguiente.victima:
        return False
    if siguiente.lado_atacante and muerte.lado_victima:
        return siguiente.lado_atacante == muerte.lado_victima
    return True
