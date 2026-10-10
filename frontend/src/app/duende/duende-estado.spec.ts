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

  it('manda el periodo de la página y lo enseña en la etiqueta; cambiarlo empieza otra conversación', () => {
    expect(duende.etiqueta()).toBe('Hablando de Ana · CS2');
    duende.enviar('hola');
    expect(http.expectOne('/api/duende/chat').request.body.periodo).toBeNull(); // todas las partidas
    expect(duende.mensajes().length).toBe(1);

    duende.fijarContexto({ foco: [{ slug: 'j1', nombre: 'Ana' }], juego: 'cs2', periodo: '7d' });
    expect(duende.mensajes()).toEqual([]);
    expect(duende.etiqueta()).toBe('Hablando de Ana · CS2 · 7 días');
    duende.enviar('¿Cómo voy?');
    expect(http.expectOne('/api/duende/chat').request.body.periodo).toBe('7d');
  });

  it('un error se enseña como mensaje del Duende', () => {
    duende.enviar('hola');
    http
      .expectOne('/api/duende/chat')
      .flush({ error: 'El Duende no está disponible ahora mismo.' }, { status: 503, statusText: 'Service Unavailable' });

    expect(duende.mensajes().at(-1)).toMatchObject({ rol: 'duende', error: true });
    expect(duende.mensajes().at(-1)!.texto).toContain('no está disponible');
  });

  /** Pregunta algo y deja contestada la respuesta del Duende (el segundo mensaje). */
  function contestar(): void {
    duende.enviar('¿Qué tal el tiempo?');
    http
      .expectOne('/api/duende/chat')
      .flush({ respuesta: 'De eso no sé.', origen: 'reglas', modelo: null, intencion: 'ayuda', sugerencias: [] });
  }

  it('valora una respuesta con la pregunta que la provocó; pulsar otra vez quita el voto', () => {
    contestar();
    duende.valorar(1, -1);
    expect(duende.mensajes()[1].voto).toBe(-1);

    const voto = http.expectOne('/api/duende/valoraciones/respuesta');
    expect(voto.request.method).toBe('PUT');
    expect(voto.request.body).toMatchObject({
      voto: -1,
      pregunta: '¿Qué tal el tiempo?',
      respuesta: 'De eso no sé.',
      origen: 'reglas',
      modelo: null,
      intencion: 'ayuda',
      foco: ['j1'],
      juego: 'cs2',
      lang: 'es',
    });
    expect(voto.request.body.votante).toMatch(/^[A-Za-z0-9-]{8,40}$/);
    voto.flush(null);

    duende.valorar(1, 0);
    expect(duende.mensajes()[1].voto).toBeNull();
    expect(http.expectOne('/api/duende/valoraciones/respuesta').request.body.voto).toBe(0);
  });

  it('si el voto no se guarda, vuelve a como estaba; los mensajes sin origen no se valoran', () => {
    contestar();
    duende.valorar(1, 1);
    http
      .expectOne('/api/duende/valoraciones/respuesta')
      .flush({ error: 'Petición no válida.' }, { status: 400, statusText: 'Bad Request' });
    expect(duende.mensajes()[1].voto).toBeNull();

    duende.valorar(0, 1); // la pregunta
    http.expectNone('/api/duende/valoraciones/respuesta');
  });
});
