// Tipos de la API Java (/api). Mismos nombres de campo que los records de Java.

export type Juego = 'cs2' | 'smite2';
export const JUEGOS: readonly Juego[] = ['cs2', 'smite2'];

/** Qué partidas cuentan: las de los últimos 7 días, las de los últimos 30 o todas. En la URL, `?periodo=7d`. */
export type Periodo = '7d' | '30d' | 'todo';
export const PERIODOS: readonly Periodo[] = ['7d', '30d', 'todo'];

/** El periodo de un parámetro de la URL; si no es uno conocido, todas las partidas. */
export function periodoDe(texto: string | null | undefined): Periodo {
  return PERIODOS.find((p) => p === texto) ?? 'todo';
}

export interface ResumenJuego {
  juego: Juego;
  partidas: number;
  victorias: number;
  derrotas: number;
  winrate: number | null;
  kd: number | null;
  killsMedia: number | null;
  muertesMedia: number | null;
  asistenciasMedia: number | null;
  /** adr, hs_pct, kr, entry_pct, clutch_pct... (CS2) o kda, dano_min, oro_min... (SMITE 2). */
  datosMedios: Record<string, number>;
  /** De más reciente a más antigua: V, D o ?. */
  forma: string;
  ultimaPartida: string | null;
}

export interface MediasEquipo {
  jugadores: number;
  winrate: number | null;
  kd: number | null;
  killsMedia: number | null;
  muertesMedia: number | null;
  asistenciasMedia: number | null;
  datosMedios: Record<string, number>;
}

export interface CuentaVista {
  juego: Juego;
  nick: string;
  /** Rol declarado en ese juego (entry, soporte... o solo, guardian...), o null si no lo ha dicho. */
  rol: string | null;
  /** Nivel de FACEIT (1 a 10) y ELO en la última sincronización; null en SMITE 2 o si aún no se saben. */
  nivel: number | null;
  elo: number | null;
  ultimaSync: string | null;
}

export interface ConsejoBreve {
  juego: Juego;
  nivel: NivelInsight;
  titulo: string;
}

export interface TarjetaJugador {
  slug: string;
  nombre: string;
  demo: boolean;
  cuentas: CuentaVista[];
  resumenes: ResumenJuego[];
  consejo: ConsejoBreve | null;
}

export interface JugadorVista {
  slug: string;
  nombre: string;
  demo: boolean;
  cuentas: CuentaVista[];
  resumenes: ResumenJuego[];
}

export interface BusquedaVista {
  slug: string;
  nombre: string;
  cuentas: CuentaVista[];
}

export interface FilaDesglose {
  clave: string;
  partidas: number;
  victorias: number;
  winrate: number | null;
  kd: number | null;
}

/** Cómo le va con un compañero del equipo (o solo: slug y nombre null). «Sin» son las demás partidas. */
export interface FilaSinergia {
  slug: string | null;
  nombre: string | null;
  partidas: number;
  victorias: number;
  winrate: number | null;
  kd: number | null;
  partidasSin: number;
  winrateSin: number | null;
}

/** Con quién juega mejor: solo (null si hay pocas) y un compañero por fila, el que más partidas juntos primero. */
export interface Sinergias {
  solo: FilaSinergia | null;
  companeros: FilaSinergia[];
}

/**
 * Cómo le va en un tipo de partida y en el resto. Claves: "1", "2", "3+" (orden en la sesión); "victoria", "derrota"
 * (cómo acabó la anterior de la sesión); "manana", "tarde", "noche", "madrugada" (hora del día).
 */
export interface FilaMomento {
  clave: string;
  partidas: number;
  victorias: number;
  winrate: number | null;
  kd: number | null;
  partidasResto: number;
  winrateResto: number | null;
}

/** Sesiones: partidas seguidas, con menos de 45 minutos entre una y otra. Cada lista, sin las filas vacías. */
export interface Sesiones {
  sesiones: number;
  partidasPorSesion: number | null;
  porOrden: FilaMomento[];
  trasResultado: FilaMomento[];
  porFranja: FilaMomento[];
}

export interface Miembro {
  slug: string;
  nombre: string;
}

/** Dos o tres del equipo y cómo les va juntos en el mismo bando. */
export interface Grupo {
  jugadores: Miembro[];
  partidas: number;
  victorias: number;
  winrate: number | null;
}

/** Dúos y tríos de un juego, el mejor winrate primero. */
export interface GruposJuego {
  juego: Juego;
  duos: Grupo[];
  trios: Grupo[];
}

export interface PuntoSerie {
  partidaId: number;
  fecha: string;
  gano: boolean | null;
  kills: number | null;
  muertes: number | null;
  asistencias: number | null;
  clave: string | null;
}

export interface DetalleJuego {
  juego: Juego;
  resumen: ResumenJuego;
  reciente: ResumenJuego;
  equipo: MediasEquipo | null;
  desglose: FilaDesglose[];
  serie: PuntoSerie[];
}

export interface PartidaVista {
  partidaId: number;
  juego: Juego;
  jugadaEn: string;
  modo: string | null;
  gano: boolean | null;
  kills: number | null;
  muertes: number | null;
  asistencias: number | null;
  datos: Record<string, number | string | null>;
  companeros: string[];
  /** Si su demo está analizada (P12): entonces se pueden pedir sus rondas. */
  analizada: boolean;
  /** Lo que dice el Duende de la partida (rachas, récords del mes...), o null si no tiene nada especial. */
  comentario: string | null;
}

export interface PaginaPartidas {
  items: PartidaVista[];
  total: number;
}

export interface JugadorRef {
  slug: string;
  nombre: string;
}

export interface FilaComparacion {
  metrica: string;
  a: number | null;
  b: number | null;
  mejor: 'alto' | 'bajo' | 'neutral';
  ventaja: 'a' | 'b' | null;
}

export interface Comparacion {
  a: JugadorRef;
  b: JugadorRef;
  juego: Juego;
  resumenA: ResumenJuego | null;
  resumenB: ResumenJuego | null;
  filas: FilaComparacion[];
}

export interface FilaRanking {
  slug: string;
  nombre: string;
  resumen: ResumenJuego;
}

export interface Ranking {
  juego: Juego;
  metricas: string[];
  filas: FilaRanking[];
}

export interface Estado {
  ultimaSync: string | null;
  demo: boolean;
  fuentes: Record<Juego, boolean>;
  duende: { disponible: boolean; gemini: boolean; modelo: string | null };
  /** Análisis de demos (P12): si hay trabajador, si hay token de descargas de FACEIT y cuántas demos en cada estado. */
  analisis: { activo: boolean; descargas: boolean; analizadas: number; pendientes: number; fallidas: number };
}

// ─── Demos de CS2 (P12) ────────────────────────────────────────────────────

export type Lado = 'CT' | 'T';
export type Compra = 'pistola' | 'eco' | 'forzada' | 'completa';

/** Lo que hizo en una ronda de una partida analizada. */
export interface FilaRonda {
  partidaId: number;
  jugadaEn: string;
  mapa: string | null;
  ronda: number;
  lado: Lado | null;
  /** Si su equipo ganó la ronda. */
  gano: boolean | null;
  kills: number;
  asistencias: number;
  asistenciasFlash: number;
  dano: number;
  danoUtilidad: number;
  murio: boolean;
  muerteX: number | null;
  muerteY: number | null;
  /** Dónde murió, para leer ("Top of Mid", "Bombsite A"). */
  muerteZona: string | null;
  /** Murió y un compañero vengó su muerte en menos de 5 s. */
  tradeado: boolean;
  /** Veces que vengó a un compañero en menos de 5 s. */
  trades: number;
  /** El primer duelo de la ronda, si fue suyo. */
  apertura: 'ganada' | 'perdida' | null;
  equipamiento: number | null;
  compra: Compra | null;
  kast: boolean;
}

/** Lo que dicen las rondas de unas partidas analizadas. Porcentajes de 0 a 100. */
export interface MetricasRondas {
  partidas: number;
  rondas: number;
  /** Rating propio al estilo del de HLTV: 1,00 es lo normal. */
  rating: number | null;
  kast: number | null;
  adr: number | null;
  kpr: number | null;
  dpr: number | null;
  aperturas: number;
  aperturasGanadas: number;
  aperturaPct: number | null;
  trades: number;
  tradesPartida: number | null;
  muertes: number;
  muertesTradeadas: number;
  tradeadasPct: number | null;
  flashPartida: number | null;
  utilidadRonda: number | null;
  winrateRondas: number | null;
}

export interface FilaLado {
  lado: Lado;
  rondas: number;
  ganadas: number;
  winrate: number | null;
  rating: number | null;
  kast: number | null;
  adr: number | null;
}

export interface FilaCompra {
  compra: Compra;
  rondas: number;
  ganadas: number;
  winrate: number | null;
  kpr: number | null;
}

export interface ZonaMuerte {
  zona: string;
  muertes: number;
  sinTrade: number;
}

/** Sus muertes en un mapa ("de_inferno"), en total, sin trade y por zona (las de más muertes sin trade primero). */
export interface MapaMuertes {
  mapa: string;
  partidas: number;
  muertes: number;
  sinTrade: number;
  zonas: ZonaMuerte[];
}

/** Todo lo de sus demos, con la media del resto del equipo (null si nadie más tiene demos). */
export interface ResumenDemos {
  metricas: MetricasRondas;
  equipo: MetricasRondas | null;
  lados: FilaLado[];
  economia: FilaCompra[];
  mapas: MapaMuertes[];
}

export interface Punto {
  x: number;
  y: number;
}

export interface PuntoMuerte extends Punto {
  zona: string | null;
  tradeado: boolean;
}

export interface EtiquetaZona extends Punto {
  zona: string;
}

/** Mapa de calor de sus muertes en un mapa. `fondo`: dónde muere la gente en él (sin decir quién). */
export interface CalorMapa {
  mapa: string | null;
  mapas: string[];
  muertes: PuntoMuerte[];
  fondo: Punto[];
  zonas: EtiquetaZona[];
}

/** Las rondas de una partida analizada, con su resumen. */
export interface RondasPartida {
  partidaId: number;
  mapa: string | null;
  metricas: MetricasRondas;
  rondas: FilaRonda[];
}

// ─── Duende ────────────────────────────────────────────────────────────────

export type NivelInsight = 'alto' | 'medio' | 'bien' | 'info';
export type FormatoValor = 'pct' | 'dec' | 'int';

export interface Barra {
  etiqueta: string;
  valor: number;
  tuyo: boolean;
}

export interface Insight {
  id: string;
  nivel: NivelInsight;
  metrica: string | null;
  titulo: string;
  texto: string;
  consejo: string | null;
  barras: Barra[];
  formato: FormatoValor | null;
}

export interface ConsejosVista {
  disponible: boolean;
  insights: Insight[];
}

export interface MensajeChat {
  rol: 'usuario' | 'duende';
  texto: string;
}

export interface RespuestaChat {
  respuesta: string;
  origen: 'gemini' | 'reglas';
  modelo: string | null;
  /** De qué iba la pregunta según las reglas (mejorar, companeros... o ayuda si no la entienden). */
  intencion: string | null;
  sugerencias: string[];
}

// ─── Valoraciones (P7) ─────────────────────────────────────────────────────

/** 1 me sirve, -1 no me sirve, 0 quita el voto. */
export type Voto = 1 | -1 | 0;

/** Voto a una recomendación del panel. `votante` es el id al azar de este navegador. */
export interface ValoracionConsejo {
  votante: string;
  voto: Voto;
  jugador: string;
  juego: Juego;
  insight: string;
  nivel: NivelInsight;
  lang: string;
  /** Lo que se vio: título, texto y consejo. */
  texto: string;
}

/** Voto a una respuesta del chat, con la pregunta y de dónde salió la respuesta. */
export interface ValoracionRespuesta {
  votante: string;
  voto: Voto;
  lang: string;
  foco: string[];
  juego: Juego | null;
  pregunta: string;
  respuesta: string;
  origen: 'gemini' | 'reglas';
  modelo: string | null;
  intencion: string | null;
}
