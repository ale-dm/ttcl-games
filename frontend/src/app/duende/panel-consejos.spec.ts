import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { I18n } from '../core/i18n';
import { Insight } from '../core/modelos';
import { PanelConsejos } from './panel-consejos';

const ADR: Insight = {
  id: 'debil_adr',
  nivel: 'alto',
  metrica: 'adr',
  titulo: 'Poco daño por ronda',
  texto: 'Haces 70 de ADR.',
  consejo: 'Usa más la utilidad.',
  barras: [],
  formato: 'int',
};

describe('PanelConsejos · valoraciones', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(I18n).cambiar('es');
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function abrir(slug: string | null = 'j3'): Promise<ComponentFixture<PanelConsejos>> {
    const panel = TestBed.createComponent(PanelConsejos);
    panel.componentRef.setInput('insights', [ADR]);
    panel.componentRef.setInput('slug', slug);
    panel.componentRef.setInput('juego', 'cs2');
    await panel.whenStable();
    return panel;
  }

  const boton = (panel: ComponentFixture<PanelConsejos>, cual: 'si' | 'no') =>
    (panel.nativeElement as HTMLElement).querySelector<HTMLButtonElement>(`.ins .voto .${cual}`)!;
  const etiqueta = (panel: ComponentFixture<PanelConsejos>) =>
    (panel.nativeElement as HTMLElement).querySelector('.voto-l')!.textContent!.trim();
  const recordados = () => JSON.parse(localStorage.getItem('ttcl.votos') ?? '{}');

  it('cada recomendación se puede valorar, el voto se recuerda y pulsar otra vez lo quita', async () => {
    const panel = await abrir();
    expect(etiqueta(panel)).toBe('¿Te sirve?');
    expect(boton(panel, 'si').getAttribute('aria-label')).toBe('Me sirve');

    boton(panel, 'si').click();
    await panel.whenStable();
    const voto = http.expectOne('/api/duende/valoraciones/consejo');
    expect(voto.request.method).toBe('PUT');
    expect(voto.request.body).toMatchObject({
      voto: 1,
      jugador: 'j3',
      juego: 'cs2',
      insight: 'debil_adr',
      nivel: 'alto',
      lang: 'es',
      texto: 'Poco daño por ronda\nHaces 70 de ADR.\nUsa más la utilidad.',
    });
    voto.flush(null);
    expect(boton(panel, 'si').getAttribute('aria-pressed')).toBe('true');
    expect(boton(panel, 'no').getAttribute('aria-pressed')).toBe('false');
    expect(etiqueta(panel)).toBe('¡Gracias!');
    expect(recordados()).toEqual({ 'j3|cs2|debil_adr': 1 });

    // Al volver al perfil sigue marcado; en otro jugador, la misma recomendación no.
    expect(boton(await abrir(), 'si').getAttribute('aria-pressed')).toBe('true');
    expect(boton(await abrir('j1'), 'si').getAttribute('aria-pressed')).toBe('false');

    // Pulsar el marcado quita el voto; pulsar el otro lo cambia.
    boton(panel, 'si').click();
    await panel.whenStable();
    expect(http.expectOne('/api/duende/valoraciones/consejo').request.body.voto).toBe(0);
    expect(boton(panel, 'si').getAttribute('aria-pressed')).toBe('false');
    expect(recordados()).toEqual({});
    boton(panel, 'no').click();
    await panel.whenStable();
    expect(http.expectOne('/api/duende/valoraciones/consejo').request.body.voto).toBe(-1);
    expect(recordados()).toEqual({ 'j3|cs2|debil_adr': -1 });
  });

  it('si el voto no se guarda, vuelve a como estaba', async () => {
    const panel = await abrir();
    boton(panel, 'no').click();
    await panel.whenStable();
    expect(boton(panel, 'no').getAttribute('aria-pressed')).toBe('true');

    http
      .expectOne('/api/duende/valoraciones/consejo')
      .flush({ error: 'Petición no válida.' }, { status: 400, statusText: 'Bad Request' });
    await panel.whenStable();
    expect(boton(panel, 'no').getAttribute('aria-pressed')).toBe('false');
    expect(recordados()).toEqual({});
  });

  it('sin saber de quién son, no se pueden valorar', async () => {
    const panel = await abrir(null);
    expect((panel.nativeElement as HTMLElement).querySelector('.voto')).toBeNull();
  });
});
