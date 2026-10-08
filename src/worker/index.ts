// Worker de sincronización. `npm run worker` lo deja corriendo y sincroniza cada SYNC_INTERVAL_MIN minutos;
// `npm run worker:once` hace una sola pasada y sale (útil para probar o desde un cron externo).
import { getConfig } from "@/lib/config";
import { getDb } from "@/db";
import { FaceitFuente } from "@/lib/sources/faceit";
import { crearFuenteHirez } from "@/lib/sources/hirez";
import type { FuenteJuego } from "@/lib/sources/types";
import type { Juego } from "@/lib/juegos";
import { sincronizarTodo } from "./sincronizar";

const log = (mensaje: string) => console.log(`${new Date().toISOString()} [sync] ${mensaje}`);

function fuentesConfiguradas(): Partial<Record<Juego, FuenteJuego>> {
  const cfg = getConfig();
  const fuentes: Partial<Record<Juego, FuenteJuego>> = {};
  if (cfg.FACEIT_API_KEY) fuentes.cs2 = new FaceitFuente(cfg.FACEIT_API_KEY);
  const smite = crearFuenteHirez(cfg);
  if (smite) fuentes.smite2 = smite;
  return fuentes;
}

async function main() {
  const soloUna = process.argv.includes("--once");
  const cfg = getConfig();
  const fuentes = fuentesConfiguradas();

  if (Object.keys(fuentes).length === 0) {
    log("No hay ninguna fuente configurada (FACEIT_API_KEY o SMITE2_*). No hay nada que sincronizar.");
    process.exit(soloUna ? 1 : 0);
  }
  log(`Fuentes activas: ${Object.keys(fuentes).join(", ")}`);

  let enCurso = false;
  const pasada = async () => {
    if (enCurso) return log("La pasada anterior sigue en curso; esta se omite.");
    enCurso = true;
    try {
      const resultados = await sincronizarTodo({ db: getDb(), fuentes, log });
      const fallos = resultados.filter((r) => !r.ok).length;
      log(`Pasada terminada: ${resultados.length - fallos} ok, ${fallos} con error.`);
    } catch (err) {
      log(`Pasada fallida: ${(err as Error).message}`);
    } finally {
      enCurso = false;
    }
  };

  await pasada();
  if (soloUna) process.exit(0);

  setInterval(pasada, cfg.SYNC_INTERVAL_MIN * 60 * 1000);
  log(`Siguiente pasada en ${cfg.SYNC_INTERVAL_MIN} min.`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
