// Casos de uso del Duende: qué datos se le dan para cada comentario. La web y la API llaman solo aquí.
import type { Juego } from "../juegos";
import { NOMBRE_JUEGO } from "../juegos";
import { jugadorPorSlug } from "../datos";
import { compararResumenes } from "../stats/resumen";
import type { ResumenJuego } from "../stats/resumen";
import { promptComparacion, promptConsejos, promptPerfil, resumenParaPrompt } from "./prompts";
import { textoDuende, type TextoDuende } from "./generar";

export class NoEncontradoError extends Error {
  constructor(mensaje: string) {
    super(mensaje);
    this.name = "NoEncontradoError";
  }
}

async function jugadorOrFail(slug: string) {
  const jugador = await jugadorPorSlug(slug);
  if (!jugador) throw new NoEncontradoError(`No hay ningún jugador con el slug "${slug}".`);
  return jugador;
}

/** Comentario general de un jugador. Con juego "todos" mezcla todos sus juegos. */
export async function comentarJugador(slug: string, juego: Juego | "todos"): Promise<TextoDuende> {
  const jugador = await jugadorOrFail(slug);
  const resumenes =
    juego === "todos" ? jugador.resumenes : jugador.resumenes.filter((r) => r.juego === juego);
  if (resumenes.length === 0) {
    const alcance = juego === "todos" ? "" : ` en ${NOMBRE_JUEGO[juego]}`;
    throw new NoEncontradoError(`${jugador.nombre} no tiene partidas guardadas${alcance}.`);
  }
  return textoDuende({
    tipo: "perfil",
    clave: `${slug}:${juego}`,
    prompt: promptPerfil(jugador.nombre, juego, resumenes),
    datos: resumenes.map(resumenParaPrompt),
  });
}

/** Tres recomendaciones concretas para un jugador en un juego. */
export async function consejosJugador(slug: string, juego: Juego): Promise<TextoDuende> {
  const jugador = await jugadorOrFail(slug);
  const resumen = jugador.resumenes.find((r) => r.juego === juego);
  if (!resumen)
    throw new NoEncontradoError(`${jugador.nombre} no tiene partidas guardadas en ${NOMBRE_JUEGO[juego]}.`);
  return textoDuende({
    tipo: "consejos",
    clave: `${slug}:${juego}`,
    prompt: promptConsejos(jugador.nombre, juego, resumen),
    datos: resumenParaPrompt(resumen),
  });
}

/** Comparación de dos jugadores en un juego en el que los dos tengan partidas. */
export async function compararJugadores(slugA: string, slugB: string, juego: Juego): Promise<TextoDuende> {
  const [a, b] = await Promise.all([jugadorOrFail(slugA), jugadorOrFail(slugB)]);
  const ra = a.resumenes.find((r) => r.juego === juego);
  const rb = b.resumenes.find((r) => r.juego === juego);
  if (!ra || !rb) {
    throw new NoEncontradoError(
      `Para comparar en ${NOMBRE_JUEGO[juego]} los dos necesitan partidas guardadas.`,
    );
  }
  // Se ordenan los slugs para que A-B y B-A compartan el mismo texto guardado.
  const [primero, segundo] = [a, b].sort((x, y) => x.slug.localeCompare(y.slug));
  const [rp, rs] = primero.slug === a.slug ? [ra, rb] : [rb, ra];
  return textoDuende({
    tipo: "comparacion",
    clave: `${primero.slug}:${segundo.slug}:${juego}`,
    prompt: promptComparacion(
      { nombre: primero.nombre, resumen: rp },
      { nombre: segundo.nombre, resumen: rs },
    ),
    datos: { [primero.slug]: resumenParaPrompt(rp), [segundo.slug]: resumenParaPrompt(rs) },
  });
}

/** Para la web: resúmenes comparados de dos jugadores en un juego, o null si no hay datos de ambos. */
export async function compararDatos(slugA: string, slugB: string, juego: Juego) {
  const [a, b] = await Promise.all([jugadorOrFail(slugA), jugadorOrFail(slugB)]);
  const ra: ResumenJuego | undefined = a.resumenes.find((r) => r.juego === juego);
  const rb: ResumenJuego | undefined = b.resumenes.find((r) => r.juego === juego);
  if (!ra || !rb) return { a, b, filas: null };
  return { a, b, filas: compararResumenes(ra, rb) };
}
