// Carga el equipo desde config/equipo.json (copia de config/equipo.example.json; el real no va al repo).
// Es idempotente: se puede ejecutar cuantas veces haga falta. Si cambia el nick de una cuenta, se vuelve a resolver.
import { readFileSync, existsSync } from "fs";
import { join } from "path";
import { and, eq } from "drizzle-orm";
import { getDb, schema } from "./index";
import { JUEGOS, type Juego } from "@/lib/juegos";

const { jugadores, cuentas } = schema;

interface EntradaEquipo {
  slug: string;
  nombre: string;
  cuentas?: Partial<Record<Juego, { nick: string }>>;
}

async function main() {
  const ruta = join(process.cwd(), "config", "equipo.json");
  if (!existsSync(ruta)) {
    throw new Error(
      "No existe config/equipo.json. Copia config/equipo.example.json a config/equipo.json y ponles los datos reales.",
    );
  }
  const equipo = JSON.parse(readFileSync(ruta, "utf8")) as EntradaEquipo[];
  const db = getDb();

  for (const miembro of equipo) {
    const [jugador] = await db
      .insert(jugadores)
      .values({ slug: miembro.slug, nombre: miembro.nombre })
      .onConflictDoUpdate({ target: jugadores.slug, set: { nombre: miembro.nombre } })
      .returning({ id: jugadores.id });

    for (const juego of JUEGOS) {
      const cuenta = miembro.cuentas?.[juego];
      if (!cuenta) continue;
      const [existente] = await db
        .select()
        .from(cuentas)
        .where(and(eq(cuentas.jugadorId, jugador!.id), eq(cuentas.juego, juego)));

      if (!existente) {
        await db.insert(cuentas).values({ jugadorId: jugador!.id, juego, nombreExterno: cuenta.nick });
      } else if (existente.nombreExterno !== cuenta.nick) {
        // Nick nuevo: se borra el ID viejo para que el worker lo vuelva a resolver.
        await db
          .update(cuentas)
          .set({ nombreExterno: cuenta.nick, externalId: null })
          .where(eq(cuentas.id, existente.id));
      }
    }
    console.log(`✔ ${miembro.nombre} (${miembro.slug})`);
  }
  console.log(`Equipo cargado: ${equipo.length} miembros.`);
  process.exit(0);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
