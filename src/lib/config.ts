import { z } from "zod";

// Variables de entorno. Se leen al usarlas (no al importar el módulo), así `next build` y los tests
// no necesitan tener configurado todo.
const esquema = z.object({
  DATABASE_URL: z.string().min(1, "Falta DATABASE_URL"),

  // Gemini (el Duende). Mismas variables que el bot de Discord, para poder reutilizar la clave y los modelos.
  GOOGLE_API_KEY: z.string().optional().default(""),
  GEMINI_MODEL: z.string().default("gemini-2.5-flash"),
  GEMINI_FALLBACK_MODELS: z.string().default("gemini-2.5-flash,gemini-2.5-pro"),
  GEMINI_TIMEOUT_MS: z.coerce.number().int().positive().default(20000),
  DUENDE_DAILY_LIMIT: z.coerce.number().int().nonnegative().default(50),
  DUENDE_MAX_TOKENS: z.coerce.number().int().positive().default(1024),

  // Fuentes de datos. Opcionales: sin clave, ese juego simplemente no se sincroniza.
  FACEIT_API_KEY: z.string().optional().default(""),
  SMITE2_DEV_ID: z.string().optional().default(""),
  SMITE2_AUTH_KEY: z.string().optional().default(""),
  SMITE2_API_BASE: z.string().url().optional(),

  // Worker de sincronización.
  SYNC_INTERVAL_MIN: z.coerce.number().positive().default(60),
});

export type Config = z.infer<typeof esquema>;

let cache: Config | null = null;

export function getConfig(): Config {
  if (!cache) {
    const resultado = esquema.safeParse(process.env);
    if (!resultado.success) {
      const detalle = resultado.error.issues.map((i) => `${i.path.join(".")}: ${i.message}`).join("; ");
      throw new Error(`Configuración no válida: ${detalle}`);
    }
    cache = resultado.data;
  }
  return cache;
}

/** Solo para tests: fuerza una relectura del entorno. */
export function resetConfigForTests() {
  cache = null;
}
