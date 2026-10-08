// Genera (o reutiliza) los textos del Duende. Un texto se guarda con el hash de los datos con que se generó:
// mientras las estadísticas no cambien, se devuelve el guardado y no se gasta cuota de Gemini.
import { and, count, desc, eq, gte } from "drizzle-orm";
import { getConfig } from "../config";
import { getDb, schema } from "@/db";
import { generarTexto } from "../gemini";
import { PERSONALIDAD_DUENDE, hashDatos, type TipoDuende } from "./prompts";

const { textosDuende } = schema;

export class LimiteDuendeError extends Error {
  constructor(readonly limite: number) {
    super(`El Duende ya ha escrito ${limite} textos en las últimas 24 h. Vuelve a probar más tarde.`);
    this.name = "LimiteDuendeError";
  }
}

export interface PeticionDuende {
  tipo: TipoDuende;
  /** Qué se comenta, para agrupar los textos: "ale", "ale:amigo1", "ale:cs2"... */
  clave: string;
  /** Instrucciones concretas de esta petición (ver prompts.ts). */
  prompt: string;
  /** Los datos que se mandan. Solo sirven para decidir si el texto guardado sigue valiendo. */
  datos: unknown;
}

export interface TextoDuende {
  texto: string;
  modelo: string;
  desdeCache: boolean;
}

export async function textoDuende(peticion: PeticionDuende): Promise<TextoDuende> {
  const db = getDb();
  const hash = hashDatos({ tipo: peticion.tipo, datos: peticion.datos });

  const guardado = await db
    .select()
    .from(textosDuende)
    .where(
      and(
        eq(textosDuende.tipo, peticion.tipo),
        eq(textosDuende.clave, peticion.clave),
        eq(textosDuende.hashDatos, hash),
      ),
    )
    .orderBy(desc(textosDuende.creadoEn))
    .limit(1);
  if (guardado[0]) return { texto: guardado[0].texto, modelo: guardado[0].modelo, desdeCache: true };

  const limite = getConfig().DUENDE_DAILY_LIMIT;
  const desde = new Date(Date.now() - 24 * 60 * 60 * 1000);
  const [{ usados }] = await db
    .select({ usados: count() })
    .from(textosDuende)
    .where(gte(textosDuende.creadoEn, desde));
  if (usados >= limite) throw new LimiteDuendeError(limite);

  const { texto, modelo } = await generarTexto({
    systemInstruction: PERSONALIDAD_DUENDE,
    prompt: peticion.prompt,
    maxOutputTokens: getConfig().DUENDE_MAX_TOKENS,
  });

  await db
    .insert(textosDuende)
    .values({ tipo: peticion.tipo, clave: peticion.clave, hashDatos: hash, texto, modelo });
  return { texto, modelo, desdeCache: false };
}
