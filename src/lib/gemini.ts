// Cliente de Gemini para el Duende (SDK oficial @google/genai). Mismo planteamiento que el bot de Discord:
// una sola instancia, timeout que cancela la petición HTTP, detección de cuota agotada y modelos de respaldo.
import {
  GoogleGenAI,
  HarmBlockThreshold,
  HarmCategory,
  type GenerateContentParameters,
  type GenerateContentResponse,
} from "@google/genai";
import { getConfig } from "./config";

let cliente: GoogleGenAI | null = null;
let claveUsada: string | null = null;

function getGenAI(): GoogleGenAI {
  const apiKey = getConfig().GOOGLE_API_KEY;
  if (!apiKey) throw new GeminiError("sin_clave", "Falta GOOGLE_API_KEY en el entorno");
  if (!cliente || claveUsada !== apiKey) {
    cliente = new GoogleGenAI({ apiKey });
    claveUsada = apiKey;
  }
  return cliente;
}

export type CodigoGemini =
  "sin_clave" | "cuota" | "bloqueado" | "modelo_no_existe" | "timeout" | "vacio" | "error";

export class GeminiError extends Error {
  constructor(
    readonly codigo: CodigoGemini,
    mensaje: string,
  ) {
    super(mensaje);
    this.name = "GeminiError";
  }
}

// Consumo acumulado desde el arranque del proceso (útil para ver en logs cuánto se usa).
export const uso = { llamadas: 0, errores: 0, cuotaAgotada: 0, tokensEntrada: 0, tokensSalida: 0 };

export function isQuotaError(err: unknown): boolean {
  const e = err as { status?: number; message?: string } | null;
  return e?.status === 429 || /RESOURCE_EXHAUSTED|quota|rate limit/i.test(String(e?.message ?? ""));
}

function esModeloInexistente(err: unknown): boolean {
  const e = err as { status?: number; message?: string } | null;
  return (
    e?.status === 404 || /not found|no longer available|is not supported/i.test(String(e?.message ?? ""))
  );
}

/** generateContent con un timeout que cancela la petición de verdad (AbortSignal), no solo deja de esperarla. */
export async function generarContenido(
  params: Omit<GenerateContentParameters, "config"> & { config?: GenerateContentParameters["config"] },
): Promise<GenerateContentResponse> {
  const { GEMINI_TIMEOUT_MS } = getConfig();
  const config = { ...(params.config ?? {}), abortSignal: AbortSignal.timeout(GEMINI_TIMEOUT_MS) };
  uso.llamadas++;
  try {
    const respuesta = await getGenAI().models.generateContent({ ...params, config });
    uso.tokensEntrada += Number(respuesta.usageMetadata?.promptTokenCount) || 0;
    uso.tokensSalida += Number(respuesta.usageMetadata?.candidatesTokenCount) || 0;
    return respuesta;
  } catch (err) {
    uso.errores++;
    if (err instanceof GeminiError) throw err;
    if (config.abortSignal.aborted) {
      throw new GeminiError("timeout", `Gemini no respondió en ${GEMINI_TIMEOUT_MS} ms`);
    }
    if (isQuotaError(err)) {
      uso.cuotaAgotada++;
      throw new GeminiError("cuota", "Gemini sin cuota o con límite de peticiones; prueba más tarde");
    }
    if (esModeloInexistente(err)) {
      throw new GeminiError(
        "modelo_no_existe",
        `El modelo ${params.model} no existe o ya no está disponible`,
      );
    }
    throw new GeminiError("error", String((err as Error)?.message ?? err).split("\n")[0]);
  }
}

// Motivos por los que Gemini corta la respuesta por filtros de contenido.
const MOTIVOS_BLOQUEO = new Set([
  "SAFETY",
  "PROHIBITED_CONTENT",
  "BLOCKLIST",
  "SPII",
  "RECITATION",
  "IMAGE_SAFETY",
]);

/** Los modelos que se prueban en orden: el principal y los de respaldo, sin repetir. */
export function candidatosDeModelo(principal = getConfig().GEMINI_MODEL): string[] {
  const respaldo = getConfig()
    .GEMINI_FALLBACK_MODELS.split(",")
    .map((m) => m.trim())
    .filter(Boolean);
  return [...new Set([principal, ...respaldo])];
}

/**
 * Genera texto con el modelo principal. Solo si ese modelo ya no existe (404, retirado) prueba los de respaldo.
 * Un error de cuota o de red no cambia de modelo: con la misma clave daría el mismo fallo.
 */
export async function generarTexto(opciones: {
  systemInstruction: string;
  prompt: string;
  maxOutputTokens: number;
  temperature?: number;
}): Promise<{ texto: string; modelo: string }> {
  let ultimo: unknown = null;
  for (const modelo of candidatosDeModelo()) {
    try {
      const respuesta = await generarContenido({
        model: modelo,
        contents: [{ role: "user", parts: [{ text: opciones.prompt }] }],
        config: {
          systemInstruction: opciones.systemInstruction,
          maxOutputTokens: opciones.maxOutputTokens,
          temperature: opciones.temperature ?? 0.8,
          // Se bloquea solo lo muy fuerte: el Duende pica, pero no debe generar nada que cruce de verdad.
          safetySettings: [
            {
              category: HarmCategory.HARM_CATEGORY_HARASSMENT,
              threshold: HarmBlockThreshold.BLOCK_ONLY_HIGH,
            },
            {
              category: HarmCategory.HARM_CATEGORY_HATE_SPEECH,
              threshold: HarmBlockThreshold.BLOCK_ONLY_HIGH,
            },
            {
              category: HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT,
              threshold: HarmBlockThreshold.BLOCK_ONLY_HIGH,
            },
            {
              category: HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT,
              threshold: HarmBlockThreshold.BLOCK_ONLY_HIGH,
            },
          ],
        },
      });
      const bloqueo = respuesta.promptFeedback?.blockReason ?? respuesta.candidates?.[0]?.finishReason;
      if (bloqueo && MOTIVOS_BLOQUEO.has(String(bloqueo))) {
        throw new GeminiError("bloqueado", `Respuesta bloqueada por Gemini (${bloqueo})`);
      }
      const texto = respuesta.text?.trim();
      if (!texto) throw new GeminiError("vacio", "Gemini devolvió una respuesta vacía");
      return { texto, modelo };
    } catch (err) {
      ultimo = err;
      if (err instanceof GeminiError && err.codigo === "modelo_no_existe") {
        console.warn(`[gemini] ${modelo} no disponible, pruebo el siguiente modelo de respaldo`);
        continue;
      }
      throw err;
    }
  }
  throw ultimo;
}
