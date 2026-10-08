CREATE TYPE "public"."estado_sync" AS ENUM('ok', 'error');--> statement-breakpoint
CREATE TYPE "public"."juego" AS ENUM('cs2', 'smite2');--> statement-breakpoint
CREATE TABLE "cuentas" (
	"id" serial PRIMARY KEY NOT NULL,
	"jugador_id" integer NOT NULL,
	"juego" "juego" NOT NULL,
	"external_id" text,
	"nombre_externo" text NOT NULL,
	"ultima_sync" timestamp with time zone
);
--> statement-breakpoint
CREATE TABLE "jugadores" (
	"id" serial PRIMARY KEY NOT NULL,
	"slug" text NOT NULL,
	"nombre" text NOT NULL,
	"creado_en" timestamp with time zone DEFAULT now() NOT NULL,
	CONSTRAINT "jugadores_slug_unique" UNIQUE("slug")
);
--> statement-breakpoint
CREATE TABLE "participaciones" (
	"id" serial PRIMARY KEY NOT NULL,
	"partida_id" integer NOT NULL,
	"jugador_id" integer NOT NULL,
	"juego" "juego" NOT NULL,
	"gano" boolean,
	"kills" integer,
	"muertes" integer,
	"asistencias" integer,
	"datos" jsonb DEFAULT '{}'::jsonb NOT NULL
);
--> statement-breakpoint
CREATE TABLE "partidas" (
	"id" serial PRIMARY KEY NOT NULL,
	"juego" "juego" NOT NULL,
	"external_id" text NOT NULL,
	"jugada_en" timestamp with time zone NOT NULL,
	"duracion_seg" integer,
	"modo" text
);
--> statement-breakpoint
CREATE TABLE "syncs" (
	"id" serial PRIMARY KEY NOT NULL,
	"cuenta_id" integer NOT NULL,
	"empezada_en" timestamp with time zone DEFAULT now() NOT NULL,
	"terminada_en" timestamp with time zone,
	"estado" "estado_sync" NOT NULL,
	"partidas_nuevas" integer DEFAULT 0 NOT NULL,
	"error" text
);
--> statement-breakpoint
CREATE TABLE "textos_duende" (
	"id" serial PRIMARY KEY NOT NULL,
	"tipo" text NOT NULL,
	"clave" text NOT NULL,
	"hash_datos" text NOT NULL,
	"texto" text NOT NULL,
	"modelo" text NOT NULL,
	"creado_en" timestamp with time zone DEFAULT now() NOT NULL
);
--> statement-breakpoint
ALTER TABLE "cuentas" ADD CONSTRAINT "cuentas_jugador_id_jugadores_id_fk" FOREIGN KEY ("jugador_id") REFERENCES "public"."jugadores"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "participaciones" ADD CONSTRAINT "participaciones_partida_id_partidas_id_fk" FOREIGN KEY ("partida_id") REFERENCES "public"."partidas"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "participaciones" ADD CONSTRAINT "participaciones_jugador_id_jugadores_id_fk" FOREIGN KEY ("jugador_id") REFERENCES "public"."jugadores"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
ALTER TABLE "syncs" ADD CONSTRAINT "syncs_cuenta_id_cuentas_id_fk" FOREIGN KEY ("cuenta_id") REFERENCES "public"."cuentas"("id") ON DELETE cascade ON UPDATE no action;--> statement-breakpoint
CREATE UNIQUE INDEX "cuentas_juego_external_uq" ON "cuentas" USING btree ("juego","external_id");--> statement-breakpoint
CREATE UNIQUE INDEX "cuentas_jugador_juego_uq" ON "cuentas" USING btree ("jugador_id","juego");--> statement-breakpoint
CREATE UNIQUE INDEX "participaciones_partida_jugador_uq" ON "participaciones" USING btree ("partida_id","jugador_id");--> statement-breakpoint
CREATE INDEX "participaciones_jugador_juego_idx" ON "participaciones" USING btree ("jugador_id","juego");--> statement-breakpoint
CREATE UNIQUE INDEX "partidas_juego_external_uq" ON "partidas" USING btree ("juego","external_id");--> statement-breakpoint
CREATE INDEX "partidas_fecha_idx" ON "partidas" USING btree ("jugada_en");--> statement-breakpoint
CREATE INDEX "syncs_cuenta_idx" ON "syncs" USING btree ("cuenta_id","empezada_en");--> statement-breakpoint
CREATE INDEX "textos_duende_clave_idx" ON "textos_duende" USING btree ("tipo","clave","creado_en");