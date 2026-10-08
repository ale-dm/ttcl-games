// Petición JSON con timeout y reintento ante 429 / 5xx. Las APIs de juego tienen límites de peticiones, así que
// se espera un poco y se vuelve a intentar antes de dar la sincronización por fallida.
export class HttpError extends Error {
  constructor(
    readonly status: number,
    readonly url: string,
    mensaje: string,
  ) {
    super(mensaje);
    this.name = "HttpError";
  }
}

export async function getJson<T>(
  url: string,
  opciones: { headers?: Record<string, string>; timeoutMs?: number; reintentos?: number } = {},
): Promise<T> {
  const { headers = {}, timeoutMs = 15000, reintentos = 2 } = opciones;
  for (let intento = 0; ; intento++) {
    const respuesta = await fetch(url, {
      headers: { Accept: "application/json", ...headers },
      signal: AbortSignal.timeout(timeoutMs),
    });
    if (respuesta.ok) return (await respuesta.json()) as T;

    const reintentable = respuesta.status === 429 || respuesta.status >= 500;
    if (reintentable && intento < reintentos) {
      const esperaSeg = Number(respuesta.headers.get("retry-after")) || 2 ** intento;
      await new Promise((r) => setTimeout(r, esperaSeg * 1000));
      continue;
    }
    // Sin el cuerpo: puede llevar la clave o datos internos de la API.
    throw new HttpError(
      respuesta.status,
      url.replace(/([?&](api_?key|key)=)[^&]+/i, "$1***"),
      `HTTP ${respuesta.status} en la API de juego`,
    );
  }
}
