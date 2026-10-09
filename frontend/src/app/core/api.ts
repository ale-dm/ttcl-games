import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  BusquedaVista,
  Comparacion,
  ConsejosVista,
  DetalleJuego,
  Estado,
  GruposJuego,
  Juego,
  JugadorVista,
  MensajeChat,
  PaginaPartidas,
  Ranking,
  RespuestaChat,
  Sinergias,
  TarjetaJugador,
} from './modelos';
import { Idioma } from './i18n';

/** Cliente de la API Java. En desarrollo, /api va por el proxy de Angular a localhost:8080. */
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

  detalle(slug: string, juego: Juego): Observable<DetalleJuego> {
    return this.http.get<DetalleJuego>(`/api/jugadores/${encodeURIComponent(slug)}/juegos/${juego}`);
  }

  partidas(slug: string, juego: Juego | null, limite: number, offset: number): Observable<PaginaPartidas> {
    let params = new HttpParams().set('limite', limite).set('offset', offset);
    if (juego) params = params.set('juego', juego);
    return this.http.get<PaginaPartidas>(`/api/jugadores/${encodeURIComponent(slug)}/partidas`, { params });
  }

  sinergias(slug: string, juego: Juego): Observable<Sinergias> {
    return this.http.get<Sinergias>(`/api/jugadores/${encodeURIComponent(slug)}/sinergias`, { params: { juego } });
  }

  grupos(juego: Juego | null): Observable<GruposJuego[]> {
    return this.http.get<GruposJuego[]>('/api/equipo/grupos', { params: juego ? { juego } : {} });
  }

  consejos(slug: string, juego: Juego, lang: Idioma): Observable<ConsejosVista> {
    return this.http.get<ConsejosVista>(`/api/jugadores/${encodeURIComponent(slug)}/consejos`, {
      params: { juego, lang },
    });
  }

  comparar(a: string, b: string, juego: Juego): Observable<Comparacion> {
    return this.http.get<Comparacion>('/api/comparar', { params: { a, b, juego } });
  }

  ranking(juego: Juego): Observable<Ranking> {
    return this.http.get<Ranking>('/api/ranking', { params: { juego } });
  }

  chat(peticion: {
    lang: Idioma;
    mensajes: MensajeChat[];
    foco: string[];
    juego: Juego | null;
  }): Observable<RespuestaChat> {
    return this.http.post<RespuestaChat>('/api/duende/chat', peticion);
  }
}
