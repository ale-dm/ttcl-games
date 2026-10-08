// Contrato común de las fuentes de datos. Cada juego tiene un adaptador que traduce su API a estas formas,
// así el worker y las estadísticas no saben nada de FACEIT ni de Hi-Rez.
import type { Juego } from "../juegos";

export interface CuentaResuelta {
  externalId: string;
  nombre: string;
}

export interface ParticipacionExterna {
  /** ID del jugador en la fuente. El worker solo guarda las de miembros del equipo. */
  externalPlayerId: string;
  gano: boolean | null;
  kills: number | null;
  muertes: number | null;
  asistencias: number | null;
  /** Lo específico de cada juego (ADR, HS%, daño, curación...). Números o texto, nunca objetos anidados. */
  datos: Record<string, number | string | null>;
}

export interface PartidaExterna {
  externalId: string;
  juego: Juego;
  jugadaEn: Date;
  duracionSeg: number | null;
  modo: string | null;
  participaciones: ParticipacionExterna[];
}

export interface FuenteJuego {
  readonly juego: Juego;
  /** Busca la cuenta por nick. Devuelve null si no existe. */
  resolverCuenta(nick: string): Promise<CuentaResuelta | null>;
  /**
   * Partidas recientes de la cuenta (la más nueva primero), sin las que ya están en `conocidas`:
   * así no se piden de nuevo los detalles de partidas que ya se guardaron.
   */
  partidasRecientes(
    externalId: string,
    limite: number,
    conocidas?: ReadonlySet<string>,
  ): Promise<PartidaExterna[]>;
}
