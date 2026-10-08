// Cálculo de estadísticas a partir de las participaciones guardadas. Funciones puras: no tocan la base de datos.
import type { Juego } from "../juegos";

export interface ParticipacionFila {
  juego: Juego;
  jugadaEn: Date;
  gano: boolean | null;
  kills: number | null;
  muertes: number | null;
  asistencias: number | null;
  datos: Record<string, number | string | null>;
}

export interface ResumenJuego {
  juego: Juego;
  partidas: number;
  victorias: number;
  derrotas: number;
  /** Porcentaje 0-100 sobre las partidas con resultado conocido. Null si no hay ninguna. */
  winrate: number | null;
  /** K/D global: kills totales entre muertes totales. */
  kd: number | null;
  killsMedia: number | null;
  muertesMedia: number | null;
  asistenciasMedia: number | null;
  /** Media de cada campo numérico de `datos` (ADR, HS%, daño...). */
  datosMedios: Record<string, number>;
  /** Últimos resultados, de más reciente a más antigua: "V" victoria, "D" derrota, "?" sin dato. */
  forma: string;
  ultimaPartida: Date | null;
}

const LONGITUD_FORMA = 10;

const redondear = (n: number, decimales = 2) => Math.round(n * 10 ** decimales) / 10 ** decimales;

function media(valores: number[]): number | null {
  if (valores.length === 0) return null;
  return redondear(valores.reduce((a, b) => a + b, 0) / valores.length);
}

/**
 * Resume las participaciones de un jugador. Si `desde` se indica, solo cuentan las partidas posteriores a esa fecha.
 * Devuelve un resumen por juego, en el orden de `JUEGOS`, solo de los juegos con alguna partida.
 */
export function resumirParticipaciones(
  filas: ParticipacionFila[],
  opciones: { desde?: Date } = {},
): ResumenJuego[] {
  const dentro = opciones.desde ? filas.filter((f) => f.jugadaEn >= opciones.desde!) : filas;
  const porJuego = new Map<Juego, ParticipacionFila[]>();
  for (const f of dentro) porJuego.set(f.juego, [...(porJuego.get(f.juego) ?? []), f]);

  const resumenes: ResumenJuego[] = [];
  for (const [juego, lista] of porJuego) {
    const ordenadas = [...lista].sort((a, b) => b.jugadaEn.getTime() - a.jugadaEn.getTime());
    const victorias = lista.filter((f) => f.gano === true).length;
    const derrotas = lista.filter((f) => f.gano === false).length;
    const conResultado = victorias + derrotas;

    const kills = lista.map((f) => f.kills).filter((n): n is number => n !== null);
    const muertes = lista.map((f) => f.muertes).filter((n): n is number => n !== null);
    const sumaKills = kills.reduce((a, b) => a + b, 0);
    const sumaMuertes = muertes.reduce((a, b) => a + b, 0);

    const datosNumericos: Record<string, number[]> = {};
    for (const f of lista) {
      for (const [clave, valor] of Object.entries(f.datos)) {
        if (typeof valor === "number" && Number.isFinite(valor)) (datosNumericos[clave] ??= []).push(valor);
      }
    }
    const datosMedios: Record<string, number> = {};
    for (const [clave, valores] of Object.entries(datosNumericos)) {
      const m = media(valores);
      if (m !== null) datosMedios[clave] = m;
    }

    resumenes.push({
      juego,
      partidas: lista.length,
      victorias,
      derrotas,
      winrate: conResultado ? redondear((victorias / conResultado) * 100, 1) : null,
      kd: sumaMuertes > 0 ? redondear(sumaKills / sumaMuertes) : null,
      killsMedia: media(kills),
      muertesMedia: media(muertes),
      asistenciasMedia: media(lista.map((f) => f.asistencias).filter((n): n is number => n !== null)),
      datosMedios,
      forma: ordenadas
        .slice(0, LONGITUD_FORMA)
        .map((f) => (f.gano === true ? "V" : f.gano === false ? "D" : "?"))
        .join(""),
      ultimaPartida: ordenadas[0]?.jugadaEn ?? null,
    });
  }
  return resumenes;
}

export interface FilaComparacion {
  metrica: string;
  a: number | null;
  b: number | null;
  /** Hacia dónde gana cada métrica: más alto es mejor, o más bajo (muertes). */
  mejor: "alto" | "bajo" | "neutral";
  /** Quién va por delante en esa métrica. Null si no hay datos de ambos o empatan. */
  ventaja: "a" | "b" | null;
}

/** Compara dos resúmenes del mismo juego, métrica a métrica. */
export function compararResumenes(a: ResumenJuego, b: ResumenJuego): FilaComparacion[] {
  const filas: Array<Omit<FilaComparacion, "ventaja">> = [
    { metrica: "Partidas", a: a.partidas, b: b.partidas, mejor: "neutral" },
    { metrica: "Winrate (%)", a: a.winrate, b: b.winrate, mejor: "alto" },
    { metrica: "K/D", a: a.kd, b: b.kd, mejor: "alto" },
    { metrica: "Kills por partida", a: a.killsMedia, b: b.killsMedia, mejor: "alto" },
    { metrica: "Muertes por partida", a: a.muertesMedia, b: b.muertesMedia, mejor: "bajo" },
    { metrica: "Asistencias por partida", a: a.asistenciasMedia, b: b.asistenciasMedia, mejor: "alto" },
  ];
  const claves = new Set([...Object.keys(a.datosMedios), ...Object.keys(b.datosMedios)]);
  for (const clave of claves) {
    filas.push({
      metrica: clave,
      a: a.datosMedios[clave] ?? null,
      b: b.datosMedios[clave] ?? null,
      mejor: "alto",
    });
  }

  return filas.map((f) => {
    let ventaja: "a" | "b" | null = null;
    if (f.a !== null && f.b !== null && f.a !== f.b && f.mejor !== "neutral") {
      const ganaA = f.mejor === "alto" ? f.a > f.b : f.a < f.b;
      ventaja = ganaA ? "a" : "b";
    }
    return { ...f, ventaja };
  });
}
