// Lógica del worker: para cada cuenta del equipo, pide las partidas recientes a su fuente y guarda las que
// tienen a algún miembro del equipo. Una partida jugada por dos del equipo se guarda una vez, con dos participaciones.
import { and, eq } from "drizzle-orm";
import type { Db } from "@/db";
import { schema } from "@/db";
import type { Juego } from "@/lib/juegos";
import type { FuenteJuego, PartidaExterna } from "@/lib/sources/types";

const { cuentas, jugadores, participaciones, partidas, syncs } = schema;

export interface ResultadoCuenta {
  cuenta: string;
  juego: Juego;
  ok: boolean;
  partidasNuevas: number;
  error?: string;
}

export interface OpcionesSync {
  db: Db;
  fuentes: Partial<Record<Juego, FuenteJuego>>;
  /** Cuántas partidas recientes mirar por cuenta en cada pasada. */
  limite?: number;
  log?: (mensaje: string) => void;
}

/** Guarda las partidas que tengan a algún miembro del equipo. Devuelve cuántas partidas eran nuevas. */
async function guardarPartidas(
  db: Db,
  juego: Juego,
  lote: PartidaExterna[],
  jugadorPorExternalId: Map<string, number>,
): Promise<number> {
  let nuevas = 0;
  for (const partida of lote) {
    const delEquipo = partida.participaciones.filter((p) => jugadorPorExternalId.has(p.externalPlayerId));
    if (delEquipo.length === 0) continue;

    await db.transaction(async (tx) => {
      const insertada = await tx
        .insert(partidas)
        .values({
          juego,
          externalId: partida.externalId,
          jugadaEn: partida.jugadaEn,
          duracionSeg: partida.duracionSeg,
          modo: partida.modo,
        })
        .onConflictDoNothing({ target: [partidas.juego, partidas.externalId] })
        .returning({ id: partidas.id });

      let partidaId = insertada[0]?.id;
      if (partidaId === undefined) {
        // Ya estaba (la guardó otra cuenta del equipo en esta misma pasada, o una pasada anterior).
        const existente = await tx
          .select({ id: partidas.id })
          .from(partidas)
          .where(and(eq(partidas.juego, juego), eq(partidas.externalId, partida.externalId)));
        partidaId = existente[0]!.id;
      } else {
        nuevas++;
      }

      for (const p of delEquipo) {
        await tx
          .insert(participaciones)
          .values({
            partidaId,
            jugadorId: jugadorPorExternalId.get(p.externalPlayerId)!,
            juego,
            gano: p.gano,
            kills: p.kills,
            muertes: p.muertes,
            asistencias: p.asistencias,
            datos: p.datos,
          })
          .onConflictDoUpdate({
            target: [participaciones.partidaId, participaciones.jugadorId],
            set: {
              gano: p.gano,
              kills: p.kills,
              muertes: p.muertes,
              asistencias: p.asistencias,
              datos: p.datos,
            },
          });
      }
    });
  }
  return nuevas;
}

/** Sincroniza todas las cuentas del equipo cuyo juego tiene fuente configurada. Un fallo en una cuenta no para las demás. */
export async function sincronizarTodo(opciones: OpcionesSync): Promise<ResultadoCuenta[]> {
  const { db, fuentes, limite = 20, log = () => {} } = opciones;

  const filas = await db
    .select({ cuenta: cuentas, jugadorNombre: jugadores.nombre })
    .from(cuentas)
    .innerJoin(jugadores, eq(jugadores.id, cuentas.jugadorId));

  const resultados: ResultadoCuenta[] = [];

  for (const juego of Object.keys(fuentes) as Juego[]) {
    const fuente = fuentes[juego]!;
    const delJuego = filas.filter((f) => f.cuenta.juego === juego);
    if (delJuego.length === 0) continue;

    // Pasada 1: resolver los IDs que falten y construir quién es quién (ID de la fuente -> jugador del equipo).
    // Va antes de mirar ninguna partida: si no, una partida con dos del equipo se guardaría solo con uno y
    // luego quedaría marcada como conocida, y el otro nunca tendría su participación.
    const jugadorPorExternalId = new Map<string, number>();
    const pendientes: typeof delJuego = [];
    for (const fila of delJuego) {
      const { cuenta, jugadorNombre } = fila;
      try {
        if (!cuenta.externalId) {
          const resuelta = await fuente.resolverCuenta(cuenta.nombreExterno);
          if (!resuelta) throw new Error(`no existe ninguna cuenta con el nick "${cuenta.nombreExterno}"`);
          await db
            .update(cuentas)
            .set({ externalId: resuelta.externalId, nombreExterno: resuelta.nombre })
            .where(eq(cuentas.id, cuenta.id));
          cuenta.externalId = resuelta.externalId;
          log(`${jugadorNombre} (${juego}): cuenta resuelta (${resuelta.nombre})`);
        }
        jugadorPorExternalId.set(cuenta.externalId, cuenta.jugadorId);
        pendientes.push(fila);
      } catch (err) {
        const mensaje = String((err as Error)?.message ?? err).slice(0, 500);
        await db
          .insert(syncs)
          .values({ cuentaId: cuenta.id, terminadaEn: new Date(), estado: "error", error: mensaje });
        log(`${jugadorNombre} (${juego}): error: ${mensaje}`);
        resultados.push({ cuenta: jugadorNombre, juego, ok: false, partidasNuevas: 0, error: mensaje });
      }
    }

    // Pasada 2: partidas recientes de cada cuenta, sin pedir de nuevo las que ya están guardadas.
    const conocidas = new Set(
      (
        await db.select({ externalId: partidas.externalId }).from(partidas).where(eq(partidas.juego, juego))
      ).map((r) => r.externalId),
    );

    for (const { cuenta, jugadorNombre } of pendientes) {
      const etiqueta = `${jugadorNombre} (${juego})`;
      try {
        const lote = await fuente.partidasRecientes(cuenta.externalId!, limite, conocidas);
        const nuevas = await guardarPartidas(db, juego, lote, jugadorPorExternalId);
        for (const p of lote) conocidas.add(p.externalId);

        await db
          .insert(syncs)
          .values({ cuentaId: cuenta.id, terminadaEn: new Date(), estado: "ok", partidasNuevas: nuevas });
        await db.update(cuentas).set({ ultimaSync: new Date() }).where(eq(cuentas.id, cuenta.id));
        log(`${etiqueta}: ${lote.length} partidas nuevas revisadas, ${nuevas} guardadas`);
        resultados.push({ cuenta: jugadorNombre, juego, ok: true, partidasNuevas: nuevas });
      } catch (err) {
        const mensaje = String((err as Error)?.message ?? err).slice(0, 500);
        await db
          .insert(syncs)
          .values({ cuentaId: cuenta.id, terminadaEn: new Date(), estado: "error", error: mensaje });
        log(`${etiqueta}: error: ${mensaje}`);
        resultados.push({ cuenta: jugadorNombre, juego, ok: false, partidasNuevas: 0, error: mensaje });
      }
    }
  }

  return resultados;
}
