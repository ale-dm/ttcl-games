import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  BusquedaVista,
  CalorMapa,
  CampoCaptura,
  CapturaVista,
  Comparacion,
  ConsejosVista,
  DetalleJuego,
  Estado,
  EvolucionCapturas,
  GruposJuego,
  Juego,
  JugadorVista,
  LecturaCaptura,
  MapaLeido,
  MensajeChat,
  PaginaPartidas,
  Periodo,
  Ranking,
  ResumenDemos,
  RespuestaChat,
  RondasPartida,
  Sesiones,
  Sinergias,
  TarjetaJugador,
  ValoracionConsejo,
  ValoracionRespuesta,
} from './modelos';
import { Idioma } from './i18n';

/** Añade `?periodo=` solo si recorta: con todas las partidas, la URL queda como siempre. */
function conPeriodo(params: HttpParams, periodo: Periodo): HttpParams {
  return periodo === 'todo' ? params : params.set('periodo', periodo);
}

/**
 * Cliente de la API Java. En desarrollo, /api va por el proxy de Angular a localhost:8080. Lo que admite `periodo`
 * cuenta solo las partidas de esos días (por defecto, todas).
 */
@Injectable({ providedIn: 'root' })
export class Api {
  private readonly http = inject(HttpClient);

  estado(): Observable<Estado> {
    return this.http.get<Estado>('/api/estado');
  }

  equipo(juego: Juego | null, lang: Idioma): Observable<TarjetaJugador[]> {
    let params = new HttpParams().set('lang', lang);
    if (juego) params = params.set('juego', juego);
    return this.http.get<TarjetaJugador[]>('/api/equipo', { params });
  }

  buscar(q: string): Observable<BusquedaVista[]> {
    return this.http.get<BusquedaVista[]>('/api/buscar', { params: { q } });
  }

  jugador(slug: string): Observable<JugadorVista> {
    return this.http.get<JugadorVista>(`/api/jugadores/${encodeURIComponent(slug)}`);
  }

  detalle(slug: string, juego: Juego, periodo: Periodo = 'todo'): Observable<DetalleJuego> {
    return this.http.get<DetalleJuego>(`/api/jugadores/${encodeURIComponent(slug)}/juegos/${juego}`, {
      params: conPeriodo(new HttpParams(), periodo),
    });
  }

  /** Historial, con lo que dice el Duende de cada partida en el idioma pedido. */
  partidas(
    slug: string,
    juego: Juego | null,
    limite: number,
    offset: number,
    periodo: Periodo = 'todo',
    lang: Idioma = 'es',
  ): Observable<PaginaPartidas> {
    let params = new HttpParams().set('limite', limite).set('offset', offset).set('lang', lang);
    if (juego) params = params.set('juego', juego);
    return this.http.get<PaginaPartidas>(`/api/jugadores/${encodeURIComponent(slug)}/partidas`, {
      params: conPeriodo(params, periodo),
    });
  }

  sinergias(slug: string, juego: Juego, periodo: Periodo = 'todo'): Observable<Sinergias> {
    return this.http.get<Sinergias>(`/api/jugadores/${encodeURIComponent(slug)}/sinergias`, {
      params: conPeriodo(new HttpParams().set('juego', juego), periodo),
    });
  }

  sesiones(slug: string, juego: Juego, periodo: Periodo = 'todo'): Observable<Sesiones> {
    return this.http.get<Sesiones>(`/api/jugadores/${encodeURIComponent(slug)}/sesiones`, {
      params: conPeriodo(new HttpParams().set('juego', juego), periodo),
    });
  }

  /** Lo que dicen las rondas de sus demos de CS2 analizadas (P12). */
  demos(slug: string, periodo: Periodo = 'todo'): Observable<ResumenDemos> {
    return this.http.get<ResumenDemos>(`/api/jugadores/${encodeURIComponent(slug)}/demos`, {
      params: conPeriodo(new HttpParams(), periodo),
    });
  }

  /** Mapa de calor de dónde muere en un mapa (sin mapa, en el que más rondas tiene analizadas). */
  calor(slug: string, mapa: string | null, periodo: Periodo = 'todo'): Observable<CalorMapa> {
    let params = conPeriodo(new HttpParams(), periodo);
    if (mapa) params = params.set('mapa', mapa);
    return this.http.get<CalorMapa>(`/api/jugadores/${encodeURIComponent(slug)}/demos/calor`, { params });
  }

  /** Lo que hizo en cada ronda de una partida con la demo analizada. */
  rondas(slug: string, partidaId: number): Observable<RondasPartida> {
    return this.http.get<RondasPartida>(`/api/jugadores/${encodeURIComponent(slug)}/partidas/${partidaId}/rondas`);
  }

  // ─── Capturas de csstats (P13) ───────────────────────────────────────────

  /** Lo que se guarda de cada captura, en orden y con su grupo. */
  camposCaptura(): Observable<CampoCaptura[]> {
    return this.http.get<CampoCaptura[]>('/api/capturas/campos');
  }

  /** Sus capturas, la primera cargada primero. */
  capturas(slug: string): Observable<CapturaVista[]> {
    return this.http.get<CapturaVista[]>(`/api/jugadores/${encodeURIComponent(slug)}/capturas`);
  }

  /** Lee una captura (con Gemini, si hay clave) para revisarla. No guarda nada. */
  leerCaptura(slug: string, imagen: Blob): Observable<LecturaCaptura> {
    const datos = new FormData();
    datos.append('imagen', imagen, 'captura');
    return this.http.post<LecturaCaptura>(`/api/jugadores/${encodeURIComponent(slug)}/capturas/leer`, datos);
  }

  /** Guarda una captura revisada, con su imagen: la fecha la pone la API. */
  guardarCaptura(
    slug: string,
    captura: { datos: Record<string, number>; mapas: MapaLeido[]; leidaPor: 'gemini' | 'mano' },
    imagen: Blob | null,
  ): Observable<CapturaVista> {
    const datos = new FormData();
    datos.append('datos', new Blob([JSON.stringify(captura)], { type: 'application/json' }));
    if (imagen) datos.append('imagen', imagen, 'captura');
    return this.http.post<CapturaVista>(`/api/jugadores/${encodeURIComponent(slug)}/capturas`, datos);
  }

  borrarCaptura(slug: string, id: number): Observable<void> {
    return this.http.delete<void>(`/api/jugadores/${encodeURIComponent(slug)}/capturas/${id}`);
  }

  /** Dos capturas comparadas (sin decir cuáles, la primera y la última). */
  compararCapturas(slug: string, desde: number | null, hasta: number | null): Observable<EvolucionCapturas> {
    let params = new HttpParams();
    if (desde !== null) params = params.set('desde', desde);
    if (hasta !== null) params = params.set('hasta', hasta);
    return this.http.get<EvolucionCapturas>(`/api/jugadores/${encodeURIComponent(slug)}/capturas/comparar`, {
      params,
    });
  }

  /** Dónde se ve la imagen de una captura. */
  imagenCaptura(slug: string, id: number): string {
    return `/api/jugadores/${encodeURIComponent(slug)}/capturas/${id}/imagen`;
  }

  grupos(juego: Juego | null): Observable<GruposJuego[]> {
    return this.http.get<GruposJuego[]>('/api/equipo/grupos', { params: juego ? { juego } : {} });
  }

  consejos(slug: string, juego: Juego, lang: Idioma, periodo: Periodo = 'todo'): Observable<ConsejosVista> {
    return this.http.get<ConsejosVista>(`/api/jugadores/${encodeURIComponent(slug)}/consejos`, {
      params: conPeriodo(new HttpParams().set('juego', juego).set('lang', lang), periodo),
    });
  }

  comparar(a: string, b: string, juego: Juego, periodo: Periodo = 'todo'): Observable<Comparacion> {
    return this.http.get<Comparacion>('/api/comparar', {
      params: conPeriodo(new HttpParams().set('a', a).set('b', b).set('juego', juego), periodo),
    });
  }

  ranking(juego: Juego, periodo: Periodo = 'todo'): Observable<Ranking> {
    return this.http.get<Ranking>('/api/ranking', { params: conPeriodo(new HttpParams().set('juego', juego), periodo) });
  }

  chat(peticion: {
    lang: Idioma;
    mensajes: MensajeChat[];
    foco: string[];
    juego: Juego | null;
    periodo: Periodo | null;
  }): Observable<RespuestaChat> {
    return this.http.post<RespuestaChat>('/api/duende/chat', peticion);
  }

  /** 👍, 👎 o quitar el voto a una recomendación (un voto por navegador y recomendación). */
  valorarConsejo(valoracion: ValoracionConsejo): Observable<void> {
    return this.http.put<void>('/api/duende/valoraciones/consejo', valoracion);
  }

  /** 👍, 👎 o quitar el voto a una respuesta del chat. */
  valorarRespuesta(valoracion: ValoracionRespuesta): Observable<void> {
    return this.http.put<void>('/api/duende/valoraciones/respuesta', valoracion);
  }
}
