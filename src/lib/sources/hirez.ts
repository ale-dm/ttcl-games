// Adaptador de SMITE 2 con la API de Hi-Rez. Requiere dev ID y auth key, que se piden a Hi-Rez.
//
// ⚠️ SIN VERIFICAR CONTRA LA API REAL. El esquema de firma (MD5 de devId + método + authKey + timestamp), la sesión
// de 15 minutos y el formato de la fecha son los de la API pública clásica de Hi-Rez. Antes de confiar en esto hay que
// comprobar con las credenciales reales: la URL base de SMITE 2 (SMITE2_API_BASE), los nombres de método y los
// campos de getmatchhistory. Ver el README, apartado "Fuentes de datos".
import { createHash } from "crypto";
import type { CuentaResuelta, FuenteJuego, PartidaExterna } from "./types";
import { getJson } from "./http";

/** Fecha en UTC con el formato que espera Hi-Rez: yyyyMMddHHmmss. */
export function formatoTimestampHirez(fecha: Date): string {
  const p = (n: number) => String(n).padStart(2, "0");
  return (
    `${fecha.getUTCFullYear()}${p(fecha.getUTCMonth() + 1)}${p(fecha.getUTCDate())}` +
    `${p(fecha.getUTCHours())}${p(fecha.getUTCMinutes())}${p(fecha.getUTCSeconds())}`
  );
}

/** Firma de una llamada: MD5(devId + método + authKey + timestamp), en hexadecimal minúscula. */
export function firmaHirez(devId: string, metodo: string, authKey: string, timestamp: string): string {
  return createHash("md5")
    .update(devId + metodo + authKey + timestamp)
    .digest("hex");
}

interface SesionHirez {
  session_id: string;
  ret_msg?: string;
}

interface JugadorBusquedaHirez {
  Name: string;
  player_id: number | string;
}

interface FilaHistorialHirez {
  Match: number | string;
  Win_Status?: string;
  Kills?: number | string;
  Deaths?: number | string;
  Assists?: number | string;
  Damage?: number | string;
  Healing?: number | string;
  Gold?: number | string;
  God?: string;
  Queue?: string;
  Entry_Datetime?: string;
}

/** Traduce una fila del historial a una partida con la participación de ese jugador. Exportada para los tests. */
export function mapearFilaHistorial(fila: FilaHistorialHirez, playerId: string): PartidaExterna {
  const n = (v: number | string | undefined) => (v === undefined || v === "" ? null : Number(v));
  return {
    externalId: String(fila.Match),
    juego: "smite2",
    jugadaEn: new Date(fila.Entry_Datetime ?? 0),
    duracionSeg: null,
    modo: fila.Queue ?? null,
    participaciones: [
      {
        externalPlayerId: playerId,
        gano: fila.Win_Status === undefined ? null : fila.Win_Status === "Winner",
        kills: n(fila.Kills),
        muertes: n(fila.Deaths),
        asistencias: n(fila.Assists),
        datos: {
          dios: fila.God ?? null,
          dano: n(fila.Damage),
          curacion: n(fila.Healing),
          oro: n(fila.Gold),
        },
      },
    ],
  };
}

export class HirezFuente implements FuenteJuego {
  readonly juego = "smite2" as const;
  private sesion: { id: string; caduca: number } | null = null;

  constructor(
    private readonly base: string,
    private readonly devId: string,
    private readonly authKey: string,
  ) {}

  /** Sesión de la API (caduca a los 15 min; se reutiliza con margen). */
  private async obtenerSesion(): Promise<string> {
    if (this.sesion && this.sesion.caduca > Date.now()) return this.sesion.id;
    const ts = formatoTimestampHirez(new Date());
    const firma = firmaHirez(this.devId, "createsession", this.authKey, ts);
    const r = await getJson<SesionHirez>(
      `${this.base.replace(/\/$/, "")}/createsessionjson/${this.devId}/${firma}/${ts}`,
    );
    this.sesion = { id: r.session_id, caduca: Date.now() + 14 * 60 * 1000 };
    return r.session_id;
  }

  async resolverCuenta(nick: string): Promise<CuentaResuelta | null> {
    const sesion = await this.obtenerSesion();
    const ts = formatoTimestampHirez(new Date());
    const firma = firmaHirez(this.devId, "searchplayers", this.authKey, ts);
    const base = this.base.replace(/\/$/, "");
    const lista = await getJson<JugadorBusquedaHirez[]>(
      `${base}/searchplayersjson/${this.devId}/${firma}/${sesion}/${ts}/${encodeURIComponent(nick)}`,
    );
    const exacto = lista.find((j) => j.Name.toLowerCase() === nick.toLowerCase());
    return exacto ? { externalId: String(exacto.player_id), nombre: exacto.Name } : null;
  }

  async partidasRecientes(
    externalId: string,
    limite: number,
    conocidas: ReadonlySet<string> = new Set(),
  ): Promise<PartidaExterna[]> {
    const sesion = await this.obtenerSesion();
    const ts = formatoTimestampHirez(new Date());
    const firma = firmaHirez(this.devId, "getmatchhistory", this.authKey, ts);
    const base = this.base.replace(/\/$/, "");
    const filas = await getJson<FilaHistorialHirez[]>(
      `${base}/getmatchhistoryjson/${this.devId}/${firma}/${sesion}/${ts}/${encodeURIComponent(externalId)}`,
    );
    return filas
      .filter((f) => !conocidas.has(String(f.Match)))
      .slice(0, limite)
      .map((f) => mapearFilaHistorial(f, externalId));
  }
}

/** Crea el adaptador con la configuración del entorno, o null si faltan credenciales o la URL base. */
export function crearFuenteHirez(cfg: {
  SMITE2_API_BASE?: string;
  SMITE2_DEV_ID: string;
  SMITE2_AUTH_KEY: string;
}): HirezFuente | null {
  if (!cfg.SMITE2_API_BASE || !cfg.SMITE2_DEV_ID || !cfg.SMITE2_AUTH_KEY) return null;
  return new HirezFuente(cfg.SMITE2_API_BASE, cfg.SMITE2_DEV_ID, cfg.SMITE2_AUTH_KEY);
}
