// Personalidad del Duende y prompts para cada tipo de comentario. El modelo solo recibe resúmenes ya calculados
// (ResumenJuego), nunca partidas en bruto: así no inventa números y el coste por llamada es pequeño.
import { createHash } from "crypto";
import type { Juego } from "../juegos";
import { NOMBRE_JUEGO } from "../juegos";
import type { ResumenJuego } from "../stats/resumen";

export type TipoDuende = "perfil" | "comparacion" | "consejos";

export const DUENDE_NOMBRE = "Duende";

/** Voz del Duende. Pique de amigos, sin cruzar a lo personal ni a lo hiriente de verdad. */
export const PERSONALIDAD_DUENDE = [
  "Eres el Duende, el personaje que comenta las estadísticas de un grupo de amigos que juegan juntos a CS2 y SMITE 2.",
  "Hablas en español coloquial, con ironía y con pique cariñoso, como un colega que se mete con los demás en el grupo de WhatsApp.",
  "Te metes con los números: muertes de más, winrate bajo, rachas malas, partidas que se han ido sin aportar. Eso sí, con gracia.",
  "Reglas que no rompes nunca: nada de insultos de odio, nada sobre aspecto físico, familia, orientación sexual, origen, religión, salud ni dinero personal. Solo se pica por lo que hacen en el juego.",
  "Usa únicamente los datos que te doy. Si un dato no está, no lo inventes ni lo supongas.",
  "No pongas títulos, ni listas salvo que te lo pidan, ni te presentes. Texto corrido.",
].join(" ");

/** Qué hacer si hay pocas partidas: las conclusiones con 2 partidas no valen nada. */
const AVISO_MUESTRA_PEQUENA =
  "Hay muy pocas partidas: di que aún es pronto para sacar conclusiones y usa el humor, no las tendencias.";

const UMBRAL_MUESTRA = 5;

/** Los números en el formato que se le da al modelo: fechas en ISO y sin campos vacíos. */
export function resumenParaPrompt(resumen: ResumenJuego) {
  return {
    juego: NOMBRE_JUEGO[resumen.juego],
    partidas: resumen.partidas,
    victorias: resumen.victorias,
    derrotas: resumen.derrotas,
    winrate_pct: resumen.winrate,
    kd: resumen.kd,
    kills_por_partida: resumen.killsMedia,
    muertes_por_partida: resumen.muertesMedia,
    asistencias_por_partida: resumen.asistenciasMedia,
    medias_especificas: resumen.datosMedios,
    forma_reciente_V_D: resumen.forma,
    ultima_partida: resumen.ultimaPartida?.toISOString().slice(0, 10) ?? null,
  };
}

/** Hash estable de los datos que se mandan al modelo. Si no cambia, el texto guardado sigue valiendo. */
export function hashDatos(datos: unknown): string {
  return createHash("sha256").update(JSON.stringify(datos)).digest("hex").slice(0, 32);
}

function avisoMuestra(partidas: number): string {
  return partidas < UMBRAL_MUESTRA ? ` ${AVISO_MUESTRA_PEQUENA}` : "";
}

export function promptPerfil(nombre: string, juego: Juego | "todos", resumenes: ResumenJuego[]): string {
  const partidas = resumenes.reduce((n, r) => n + r.partidas, 0);
  const alcance = juego === "todos" ? "en todos sus juegos" : `en ${NOMBRE_JUEGO[juego]}`;
  return [
    `Escribe un comentario sobre ${nombre} ${alcance}, de 4 a 6 frases.`,
    "Di qué hace bien, de qué se le puede picar y una frase final de cierre.",
    avisoMuestra(partidas),
    "",
    "Datos:",
    JSON.stringify({ jugador: nombre, resumenes: resumenes.map(resumenParaPrompt) }, null, 2),
  ].join("\n");
}

export function promptComparacion(
  a: { nombre: string; resumen: ResumenJuego },
  b: { nombre: string; resumen: ResumenJuego },
): string {
  const juego = NOMBRE_JUEGO[a.resumen.juego];
  const partidas = Math.min(a.resumen.partidas, b.resumen.partidas);
  return [
    `Compara a ${a.nombre} y a ${b.nombre} en ${juego}, de 5 a 7 frases.`,
    "Di en qué va cada uno por delante, suelta un palo a cada uno y cierra con quién parece el peor del grupo y por qué (solo según los datos).",
    avisoMuestra(partidas),
    "",
    "Datos:",
    JSON.stringify(
      {
        [a.nombre]: resumenParaPrompt(a.resumen),
        [b.nombre]: resumenParaPrompt(b.resumen),
      },
      null,
      2,
    ),
  ].join("\n");
}

export function promptConsejos(nombre: string, juego: Juego, resumen: ResumenJuego): string {
  return [
    `Dale a ${nombre} exactamente 3 recomendaciones para mejorar en ${NOMBRE_JUEGO[juego]}.`,
    "Cada una en una línea corta, empezando por un guion. Basa cada recomendación en un dato concreto de los números.",
    avisoMuestra(resumen.partidas),
    "",
    "Datos:",
    JSON.stringify({ jugador: nombre, resumen: resumenParaPrompt(resumen) }, null, 2),
  ].join("\n");
}
