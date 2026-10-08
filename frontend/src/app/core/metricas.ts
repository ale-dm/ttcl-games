import { FormatoValor, Juego, MediasEquipo, ResumenJuego } from './modelos';
import { Clave } from './textos';

export interface Metrica {
  clave: string;
  formato: FormatoValor;
  /** Hacia dónde es mejor. "neutral": no hay ganador (partidas jugadas). */
  mejor: 'alto' | 'bajo' | 'neutral';
}

const M = (clave: string, formato: FormatoValor, mejor: Metrica['mejor'] = 'alto'): Metrica => ({
  clave,
  formato,
  mejor,
});

/** Mismas claves que la API Java (Estadisticas.valor) y el Duende (metricas.py). */
export const METRICAS: Record<string, Metrica> = Object.fromEntries(
  [
    M('partidas', 'int', 'neutral'),
    M('winrate', 'pct'),
    M('kd', 'dec'),
    M('kills_media', 'dec'),
    M('muertes_media', 'dec', 'bajo'),
    M('asistencias_media', 'dec'),
    M('adr', 'int'),
    M('hs_pct', 'pct'),
    M('kr', 'dec'),
    M('entry_pct', 'pct'),
    M('clutch_pct', 'pct'),
    M('mvps', 'dec'),
    M('multikills', 'dec'),
    M('dano_utilidad', 'int'),
    M('kda', 'dec'),
    M('dano_min', 'int'),
    M('oro_min', 'int'),
    M('dano', 'int'),
    M('mitigado', 'int'),
    M('curacion', 'int'),
  ].map((m) => [m.clave, m]),
);

export function metrica(clave: string): Metrica {
  return METRICAS[clave] ?? M(clave, 'dec');
}

export function claveTexto(clave: string): Clave {
  return `metrica.${clave}` as Clave;
}

/** Las cifras que se enseñan arriba del todo en el perfil, por juego. */
export const KPIS: Record<Juego, string[]> = {
  cs2: ['winrate', 'kd', 'adr', 'hs_pct', 'entry_pct', 'clutch_pct'],
  smite2: ['winrate', 'kda', 'kd', 'dano_min', 'oro_min', 'asistencias_media'],
};

/** Columnas de la tabla de ranking, por juego. */
export const COLUMNAS_RANKING: Record<Juego, string[]> = {
  cs2: ['partidas', 'winrate', 'kd', 'adr', 'hs_pct', 'entry_pct', 'clutch_pct'],
  smite2: ['partidas', 'winrate', 'kda', 'kd', 'dano_min', 'oro_min', 'mitigado'],
};

/** Valor de una métrica en un resumen o en las medias del equipo. */
export function valorDe(fuente: ResumenJuego | MediasEquipo | null | undefined, clave: string): number | null {
  if (!fuente) return null;
  switch (clave) {
    case 'partidas':
      return 'partidas' in fuente ? fuente.partidas : null;
    case 'winrate':
      return fuente.winrate;
    case 'kd':
      return fuente.kd;
    case 'kills_media':
      return fuente.killsMedia;
    case 'muertes_media':
      return fuente.muertesMedia;
    case 'asistencias_media':
      return fuente.asistenciasMedia;
    default:
      return fuente.datosMedios[clave] ?? null;
  }
}
