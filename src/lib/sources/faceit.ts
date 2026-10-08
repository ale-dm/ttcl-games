// Adaptador de CS2 con la FACEIT Data API (https://developers.faceit.com/docs/tools/data-api). Requiere una API key
// de FACEIT. Solo sirve para jugadores que tengan cuenta de FACEIT: las estadísticas de partidas de CS2 no se
// exponen por la API de Steam.
import type { CuentaResuelta, FuenteJuego, ParticipacionExterna, PartidaExterna } from "./types";
import { HttpError, getJson } from "./http";

const BASE_POR_DEFECTO = "https://open.faceit.com/data/v4";

interface FaceitJugador {
  player_id: string;
  nickname: string;
}

interface FaceitHistorial {
  items: Array<{ match_id: string; started_at: number; finished_at: number }>;
}

type ValorStat = string | number | undefined;

interface FaceitEstadisticasPartida {
  rounds?: Array<{
    round_stats?: { Map?: string };
    teams?: Array<{
      players?: Array<{
        player_id: string;
        player_stats?: Record<string, ValorStat>;
      }>;
    }>;
  }>;
}

const numero = (v: ValorStat): number | null => {
  if (v === undefined || v === null || v === "") return null;
  const n = Number(v);
  return Number.isFinite(n) ? n : null;
};

/**
 * Traduce las estadísticas de una partida de FACEIT a participaciones normalizadas. Exportada para los tests.
 * Los nombres de campo (Kills, Result, "K/D Ratio"...) son los de la respuesta de FACEIT; si cambian, falla aquí.
 */
export function mapearEstadisticasFaceit(estadisticas: FaceitEstadisticasPartida): {
  mapa: string | null;
  participaciones: ParticipacionExterna[];
} {
  const ronda = estadisticas.rounds?.[0];
  const mapa = ronda?.round_stats?.Map ?? null;
  const participaciones: ParticipacionExterna[] = [];
  for (const equipo of ronda?.teams ?? []) {
    for (const jugador of equipo.players ?? []) {
      const s = jugador.player_stats ?? {};
      participaciones.push({
        externalPlayerId: jugador.player_id,
        gano: s.Result === undefined ? null : String(s.Result) === "1",
        kills: numero(s.Kills),
        muertes: numero(s.Deaths),
        asistencias: numero(s.Assists),
        datos: {
          mapa,
          kd_ratio: numero(s["K/D Ratio"]),
          hs_pct: numero(s["Headshots %"]),
          mvps: numero(s.MVPs),
          adr: numero(s.ADR),
        },
      });
    }
  }
  return { mapa, participaciones };
}

export class FaceitFuente implements FuenteJuego {
  readonly juego = "cs2" as const;
  private readonly base: string;

  constructor(
    private readonly apiKey: string,
    base: string = BASE_POR_DEFECTO,
  ) {
    this.base = base.replace(/\/$/, "");
  }

  private cabeceras() {
    return { Authorization: `Bearer ${this.apiKey}` };
  }

  async resolverCuenta(nick: string): Promise<CuentaResuelta | null> {
    try {
      const j = await getJson<FaceitJugador>(
        `${this.base}/players?nickname=${encodeURIComponent(nick)}&game=cs2`,
        {
          headers: this.cabeceras(),
        },
      );
      return { externalId: j.player_id, nombre: j.nickname };
    } catch (err) {
      if (err instanceof HttpError && err.status === 404) return null;
      throw err;
    }
  }

  async partidasRecientes(
    externalId: string,
    limite: number,
    conocidas: ReadonlySet<string> = new Set(),
  ): Promise<PartidaExterna[]> {
    const historial = await getJson<FaceitHistorial>(
      `${this.base}/players/${encodeURIComponent(externalId)}/history?game=cs2&offset=0&limit=${limite}`,
      { headers: this.cabeceras() },
    );

    const partidas: PartidaExterna[] = [];
    // Secuencial a propósito: FACEIT limita las peticiones por minuto y aquí no hay prisa.
    for (const item of historial.items.filter((i) => !conocidas.has(i.match_id))) {
      const stats = await getJson<FaceitEstadisticasPartida>(
        `${this.base}/matches/${encodeURIComponent(item.match_id)}/stats`,
        {
          headers: this.cabeceras(),
        },
      );
      const { mapa, participaciones } = mapearEstadisticasFaceit(stats);
      partidas.push({
        externalId: item.match_id,
        juego: "cs2",
        jugadaEn: new Date(item.started_at * 1000),
        duracionSeg: item.finished_at && item.started_at ? item.finished_at - item.started_at : null,
        modo: mapa,
        participaciones,
      });
    }
    return partidas;
  }
}
