import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { I18n } from '../core/i18n';
import { DuendeEstado } from './duende-estado';

describe('DuendeEstado', () => {
  let duende: DuendeEstado;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(I18n).cambiar('es');
    duende = TestBed.inject(DuendeEstado);
    http = TestBed.inject(HttpTestingController);
    duende.fijarContexto({ foco: [{ slug: 'j1', nombre: 'Ana' }], juego: 'cs2' });
  });

  afterEach(() => http.verify());

  it('pregunta con el contexto de la página y muestra la respuesta', () => {
    duende.abrir('¿En qué tengo que mejorar?');
    expect(duende.abierto()).toBe(true);
    expect(duende.pensando()).toBe(true);

    const peticion = http.expectOne('/api/duende/chat');
    expect(peticion.request.body.foco).toEqual(['j1']);
    expect(peticion.request.body.juego).toBe('cs2');
    expect(peticion.request.body.lang).toBe('es');
    // El saludo va primero y la pregunta al final.
    expect(peticion.request.body.mensajes.at(-1)).toEqual({ rol: 'usuario', texto: '¿En qué tengo que mejorar?' });
    peticion.flush({ respuesta: 'Mira a la cabeza.', origen: 'reglas', modelo: null, sugerencias: ['¿Y qué hago bien?'] });

    expect(duende.pensando()).toBe(false);
    expect(duende.mensajes().at(-1)).toMatchObject({ rol: 'duende', texto: 'Mira a la cabeza.', origen: 'reglas' });
    expect(duende.sugerencias()).toEqual(['¿Y qué hago bien?']);
  });

  it('si cambia de jugador, la conversación empieza de cero y se descarta la respuesta pendiente', () => {
    duende.enviar('hola');
    const peticion = http.expectOne('/api/duende/chat');
    duende.fijarContexto({ foco: [{ slug: 'j2', nombre: 'Bea' }], juego: 'cs2' });
    peticion.flush({ respuesta: 'tarde', origen: 'reglas', modelo: null, sugerencias: [] });

    expect(duende.mensajes()).toEqual([]);
    expect(duende.saludo()).toContain('Bea');
  });

  it('un error se enseña como mensaje del Duende', () => {
    duende.enviar('hola');
    http
      .expectOne('/api/duende/chat')
      .flush({ error: 'El Duende no está disponible ahora mismo.' }, { status: 503, statusText: 'Service Unavailable' });

    expect(duende.mensajes().at(-1)).toMatchObject({ rol: 'duende', error: true });
    expect(duende.mensajes().at(-1)!.texto).toContain('no está disponible');
  });
});
