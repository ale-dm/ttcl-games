// Consultas de lectura para las páginas y para el Duende. Todo lo que se muestra o se le da al modelo sale de aquí.
import { asc, desc, eq, inArray, ne, and } from "drizzle-orm";
import { getDb, schema } from "@/db";
import type { Juego } from "./juegos";
import { resumirParticipaciones, type ParticipacionFila, type ResumenJuego } from "./stats/resumen";

const { jugadores, cuentas, participaciones, partidas } = schema;

export interface CuentaVista {
  juego: Juego;
  nombreExterno: string;
  ultimaSync: Date | null;
}

export interface JugadorVista {
  id: number;
  slug: string;
  nombre: string;
  cuentas: CuentaVista[];
  resumenes: ResumenJuego[];
}

export interface PartidaHistorial {
  partidaId: number;
  juego: Juego;
  jugadaEn: Date;
  modo: string | null;
  gano: boolean | null;
  kills: number | null;
  muertes: number | null;
  asistencias: number | null;
  datos: Record<string, number | string | null>;
  /** Otros miembros del equipo que estaban en la misma partida. */
  companeros: string[];
}

/** Todas las participaciones de un jugador, con la fecha y el modo de la partida. */
export async function participacionesDe(jugadorId: number): Promise<ParticipacionFila[]> {
  const filas = await getDb()
    .select({
      juego: participaciones.juego,
      jugadaEn: partidas.jugadaEn,
      gano: participaciones.gano,
      kills: participaciones.kills,
      muertes: participaciones.muertes,
      asistencias: participaciones.asistencias,
      datos: participaciones.datos,
    })
    .from(participaciones)
    .innerJoin(partidas, eq(partidas.id, participaciones.partidaId))
    .where(eq(participaciones.jugadorId, jugadorId));
  return filas;
}

export async function cuentasDe(jugadorId: number): Promise<CuentaVista[]> {
  const filas = await getDb()
    .select()
    .from(cuentas)
    .where(eq(cuentas.jugadorId, jugadorId))
    .orderBy(asc(cuentas.juego));
  return filas.map((c) => ({ juego: c.juego, nombreExterno: c.nombreExterno, ultimaSync: c.ultimaSync }));
}

export async function listarEquipo(): Promise<JugadorVista[]> {
  const db = getDb();
  const lista = await db.select().from(jugadores).orderBy(asc(jugadores.nombre));
  const vistas: JugadorVista[] = [];
  for (const j of lista) {
    const filas = await participacionesDe(j.id);
    vistas.push({
      id: j.id,
      slug: j.slug,
      nombre: j.nombre,
      cuentas: await cuentasDe(j.id),
      resumenes: resumirParticipaciones(filas),
    });
  }
  return vistas;
}

export async function jugadorPorSlug(slug: string): Promise<JugadorVista | null> {
  const [j] = await getDb().select().from(jugadores).where(eq(jugadores.slug, slug));
  if (!j) return null;
  const filas = await participacionesDe(j.id);
  return {
    id: j.id,
    slug: j.slug,
    nombre: j.nombre,
    cuentas: await cuentasDe(j.id),
    resumenes: resumirParticipaciones(filas),
  };
}

/** Historial de partidas de un jugador, la más reciente primero, con los compañeros de equipo de cada partida. */
export async function historialDe(jugadorId: number, limite = 30): Promise<PartidaHistorial[]> {
  const db = getDb();
  const propias = await db
    .select({
      partidaId: participaciones.partidaId,
      juego: participaciones.juego,
      jugadaEn: partidas.jugadaEn,
      modo: partidas.modo,
      gano: participaciones.gano,
      kills: participaciones.kills,
      muertes: participaciones.muertes,
      asistencias: participaciones.asistencias,
      datos: participaciones.datos,
    })
    .from(participaciones)
    .innerJoin(partidas, eq(partidas.id, participaciones.partidaId))
    .where(eq(participaciones.jugadorId, jugadorId))
    .orderBy(desc(partidas.jugadaEn))
    .limit(limite);

  if (propias.length === 0) return [];

  const ids = propias.map((p) => p.partidaId);
  const otros = await db
    .select({ partidaId: participaciones.partidaId, nombre: jugadores.nombre })
    .from(participaciones)
    .innerJoin(jugadores, eq(jugadores.id, participaciones.jugadorId))
    .where(and(inArray(participaciones.partidaId, ids), ne(participaciones.jugadorId, jugadorId)));

  const companerosPorPartida = new Map<number, string[]>();
  for (const o of otros)
    companerosPorPartida.set(o.partidaId, [...(companerosPorPartida.get(o.partidaId) ?? []), o.nombre]);

  return propias.map((p) => ({ ...p, companeros: companerosPorPartida.get(p.partidaId) ?? [] }));
}
