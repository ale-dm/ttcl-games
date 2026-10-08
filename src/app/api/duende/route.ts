import { NextResponse } from "next/server";
import { z } from "zod";
import { JUEGOS } from "@/lib/juegos";
import { GeminiError } from "@/lib/gemini";
import { LimiteDuendeError } from "@/lib/duende/generar";
import {
  comentarJugador,
  compararJugadores,
  consejosJugador,
  NoEncontradoError,
} from "@/lib/duende/servicio";

export const dynamic = "force-dynamic";

const juego = z.enum(JUEGOS);
const slug = z.string().min(1).max(50);

const peticion = z.discriminatedUnion("tipo", [
  z.object({ tipo: z.literal("perfil"), slug, juego: z.enum([...JUEGOS, "todos"]).default("todos") }),
  z.object({ tipo: z.literal("consejos"), slug, juego }),
  z.object({ tipo: z.literal("comparacion"), slugA: slug, slugB: slug, juego }),
]);

const MENSAJES_GEMINI: Record<string, { status: number; mensaje: string }> = {
  sin_clave: { status: 503, mensaje: "El Duende no está configurado: falta GOOGLE_API_KEY." },
  cuota: { status: 503, mensaje: "Gemini no tiene cuota disponible ahora mismo. Prueba más tarde." },
  timeout: { status: 504, mensaje: "Gemini tardó demasiado en contestar. Prueba otra vez." },
  bloqueado: { status: 422, mensaje: "Gemini bloqueó la respuesta. Prueba con otra petición." },
  modelo_no_existe: {
    status: 502,
    mensaje: "El modelo de Gemini configurado no existe. Revisa GEMINI_MODEL.",
  },
};

export async function POST(request: Request) {
  const cuerpo = await request.json().catch(() => null);
  const entrada = peticion.safeParse(cuerpo);
  if (!entrada.success) {
    return NextResponse.json({ error: "Petición no válida." }, { status: 400 });
  }

  try {
    const d = entrada.data;
    const resultado =
      d.tipo === "perfil"
        ? await comentarJugador(d.slug, d.juego)
        : d.tipo === "consejos"
          ? await consejosJugador(d.slug, d.juego)
          : await compararJugadores(d.slugA, d.slugB, d.juego);
    return NextResponse.json(resultado);
  } catch (err) {
    if (err instanceof NoEncontradoError) return NextResponse.json({ error: err.message }, { status: 404 });
    if (err instanceof LimiteDuendeError) return NextResponse.json({ error: err.message }, { status: 429 });
    if (err instanceof GeminiError) {
      const conocido = MENSAJES_GEMINI[err.codigo] ?? {
        status: 502,
        mensaje: "Gemini falló al generar el texto.",
      };
      console.error(`[duende] ${err.codigo}: ${err.message}`);
      return NextResponse.json({ error: conocido.mensaje }, { status: conocido.status });
    }
    console.error("[duende] error inesperado", err);
    return NextResponse.json({ error: "Error interno del Duende." }, { status: 500 });
  }
}
