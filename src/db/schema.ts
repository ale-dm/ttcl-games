import {
  boolean,
  index,
  integer,
  jsonb,
  pgEnum,
  pgTable,
  serial,
  text,
  timestamp,
  uniqueIndex,
} from "drizzle-orm/pg-core";

// Juegos soportados. Añadir uno nuevo = añadir valor aquí, un adaptador en src/lib/sources y migrar.
export const juegoEnum = pgEnum("juego", ["cs2", "smite2"]);

export const estadoSyncEnum = pgEnum("estado_sync", ["ok", "error"]);

// Un miembro del equipo.
export const jugadores = pgTable("jugadores", {
  id: serial("id").primaryKey(),
  slug: text("slug").notNull().unique(), // identificador de URL, p. ej. "ale"
  nombre: text("nombre").notNull(),
  creadoEn: timestamp("creado_en", { withTimezone: true }).notNull().defaultNow(),
});

// Cuenta de un jugador en un juego. Un jugador puede tener una por juego.
export const cuentas = pgTable(
  "cuentas",
  {
    id: serial("id").primaryKey(),
    jugadorId: integer("jugador_id")
      .notNull()
      .references(() => jugadores.id, { onDelete: "cascade" }),
    juego: juegoEnum("juego").notNull(),
    // ID del jugador en la fuente (player_id de FACEIT, ID de Hi-Rez...). Único por juego.
    // Null hasta que el worker lo resuelve a partir de `nombreExterno` (el nick).
    externalId: text("external_id"),
    nombreExterno: text("nombre_externo").notNull(),
    ultimaSync: timestamp("ultima_sync", { withTimezone: true }),
  },
  (t) => [
    uniqueIndex("cuentas_juego_external_uq").on(t.juego, t.externalId),
    uniqueIndex("cuentas_jugador_juego_uq").on(t.jugadorId, t.juego),
  ],
);

// Partida. Es compartida: si dos miembros del equipo jugaron juntos, existe una sola fila y dos participaciones.
export const partidas = pgTable(
  "partidas",
  {
    id: serial("id").primaryKey(),
    juego: juegoEnum("juego").notNull(),
    externalId: text("external_id").notNull(),
    jugadaEn: timestamp("jugada_en", { withTimezone: true }).notNull(),
    duracionSeg: integer("duracion_seg"),
    modo: text("modo"), // cola, mapa, modo de juego...
  },
  (t) => [
    uniqueIndex("partidas_juego_external_uq").on(t.juego, t.externalId),
    index("partidas_fecha_idx").on(t.jugadaEn),
  ],
);

// Lo que hizo cada jugador del equipo en una partida. Las columnas comunes sirven para comparar entre juegos;
// lo específico de cada juego (ADR y HS% en CS2, daño o curación en SMITE 2...) va en `datos`.
export const participaciones = pgTable(
  "participaciones",
  {
    id: serial("id").primaryKey(),
    partidaId: integer("partida_id")
      .notNull()
      .references(() => partidas.id, { onDelete: "cascade" }),
    jugadorId: integer("jugador_id")
      .notNull()
      .references(() => jugadores.id, { onDelete: "cascade" }),
    juego: juegoEnum("juego").notNull(), // duplicado de partidas.juego para agregar sin join
    gano: boolean("gano"),
    kills: integer("kills"),
    muertes: integer("muertes"),
    asistencias: integer("asistencias"),
    datos: jsonb("datos").$type<Record<string, number | string | null>>().notNull().default({}),
  },
  (t) => [
    uniqueIndex("participaciones_partida_jugador_uq").on(t.partidaId, t.jugadorId),
    index("participaciones_jugador_juego_idx").on(t.jugadorId, t.juego),
  ],
);

// Historial de sincronizaciones: sirve para saber cuándo se miró cada cuenta y por qué falló si falló.
export const syncs = pgTable(
  "syncs",
  {
    id: serial("id").primaryKey(),
    cuentaId: integer("cuenta_id")
      .notNull()
      .references(() => cuentas.id, { onDelete: "cascade" }),
    empezadaEn: timestamp("empezada_en", { withTimezone: true }).notNull().defaultNow(),
    terminadaEn: timestamp("terminada_en", { withTimezone: true }),
    estado: estadoSyncEnum("estado").notNull(),
    partidasNuevas: integer("partidas_nuevas").notNull().default(0),
    error: text("error"),
  },
  (t) => [index("syncs_cuenta_idx").on(t.cuentaId, t.empezadaEn)],
);

// Textos generados por el Duende. La clave es el hash de los datos que se le dieron: si las estadísticas no
// han cambiado, se reutiliza el texto en vez de volver a llamar a Gemini.
export const textosDuende = pgTable(
  "textos_duende",
  {
    id: serial("id").primaryKey(),
    tipo: text("tipo").notNull(), // "perfil" | "comparacion" | "consejos"
    clave: text("clave").notNull(), // "<jugadorA>[:<jugadorB>]:<juego|todos>"
    hashDatos: text("hash_datos").notNull(),
    texto: text("texto").notNull(),
    modelo: text("modelo").notNull(),
    creadoEn: timestamp("creado_en", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [index("textos_duende_clave_idx").on(t.tipo, t.clave, t.creadoEn)],
);
