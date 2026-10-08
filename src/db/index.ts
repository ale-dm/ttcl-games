import { drizzle, type PostgresJsDatabase } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import * as schema from "./schema";
import { getConfig } from "@/lib/config";

// En desarrollo, Next recarga los módulos y crearía una conexión nueva en cada recarga.
// Guardamos la única instancia en globalThis para no agotar las conexiones de Postgres.
const globalParaDb = globalThis as unknown as { __ttclSql?: ReturnType<typeof postgres> };

function clienteSql() {
  if (!globalParaDb.__ttclSql) {
    globalParaDb.__ttclSql = postgres(getConfig().DATABASE_URL, { max: 5 });
  }
  return globalParaDb.__ttclSql;
}

export type Db = PostgresJsDatabase<typeof schema>;

/** Conexión a la base de datos. Se crea la primera vez que se pide, no al importar el módulo. */
export function getDb(): Db {
  return drizzle(clienteSql(), { schema });
}

export { schema };
