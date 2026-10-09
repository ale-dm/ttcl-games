import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { I18n } from '../../core/i18n';
import { DetalleJuego, JugadorVista, ResumenJuego, Sinergias } from '../../core/modelos';
import { JugadorPagina } from './jugador-pagina';

// Sin resúmenes, la página no pide detalle, consejos ni partidas: solo el perfil.
const BEA: JugadorVista = {
  slug: 'j2',
  nombre: 'Bea',
  demo: false,
  cuentas: [
    { juego: 'cs2', nick: 'bea_faceit', rol: 'soporte', ultimaSync: null },
    { juego: 'smite2', nick: 'BeaSmite', rol: null, ultimaSync: null },
  ],
  resumenes: [],
};

describe('JugadorPagina', () => {
  let http: HttpTestingController;
  let i18n: I18n;
  let pagina: ComponentFixture<JugadorPagina>;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    i18n = TestBed.inject(I18n);
    i18n.cambiar('es');
    http = TestBed.inject(HttpTestingController);

    pagina = TestBed.createComponent(JugadorPagina);
    pagina.componentRef.setInput('slug', 'j2');
    await pagina.whenStable();
    http.expectOne('/api/jugadores/j2').flush(BEA);
    await pagina.whenStable();
  });

  afterEach(() => http.verify());

  const texto = (el: Element | null) => el?.textContent!.replace(/\s+/g, ' ').trim() ?? null;
  const cuentas = () =>
    [...(pagina.nativeElement as HTMLElement).querySelectorAll('.acct')].map((c) => ({
      nick: texto(c.querySelector('b')),
      rol: texto(c.querySelector('.acct-rol')),
    }));

  it('enseña el rol junto al nick de cada juego, si lo ha dicho', () => {
    // El lector de pantalla oye "Rol en Counter-Strike 2: Soporte"; a la vista queda "Soporte".
    expect(cuentas()).toEqual([
      { nick: 'bea_faceit', rol: 'Rol en Counter-Strike 2: Soporte' },
      { nick: 'BeaSmite', rol: null },
    ]);
    const rol = (pagina.nativeElement as HTMLElement).querySelector('.acct-rol')!;
    expect(rol.getAttribute('title')).toBe('Rol en Counter-Strike 2');
  });

  it('traduce el rol al cambiar de idioma', async () => {
    i18n.cambiar('en');
    await pagina.whenStable();
    expect(cuentas()[0]).toEqual({ nick: 'bea_faceit', rol: 'Role in Counter-Strike 2: Support' });
  });
});

describe('JugadorPagina · con quién', () => {
  const RESUMEN: ResumenJuego = {
    juego: 'cs2',
    partidas: 30,
    victorias: 15,
    derrotas: 15,
    winrate: 50,
    kd: 1,
    killsMedia: 18,
    muertesMedia: 18,
    asistenciasMedia: 5,
    datosMedios: {},
    forma: 'VD',
    ultimaPartida: null,
  };
  const ANA: JugadorVista = { slug: 'j1', nombre: 'Ana', demo: false, cuentas: [], resumenes: [RESUMEN] };
  const fila = (slug: string | null, nombre: string | null, partidas: number, victorias: number, winrateSin: number) => ({
    slug,
    nombre,
    partidas,
    victorias,
    winrate: Math.round((1000 * victorias) / partidas) / 10,
    kd: 1,
    partidasSin: 30 - partidas,
    winrateSin,
  });

  let http: HttpTestingController;

  /** Abre el perfil de Ana en CS2 y contesta a todo lo que pide; las sinergias, con lo que se le pase. */
  async function abrir(sinergias: Sinergias): Promise<HTMLElement> {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18n).cambiar('es');
    http = TestBed.inject(HttpTestingController);
    const pagina = TestBed.createComponent(JugadorPagina);
    pagina.componentRef.setInput('slug', 'j1');
    await pagina.whenStable();
    http.expectOne('/api/jugadores/j1').flush(ANA);
    await pagina.whenStable();
    const detalle: DetalleJuego = { juego: 'cs2', resumen: RESUMEN, reciente: RESUMEN, equipo: null, desglose: [], serie: [] };
    http.expectOne('/api/jugadores/j1/juegos/cs2').flush(detalle);
    http.expectOne((r) => r.url === '/api/jugadores/j1/consejos').flush({ disponible: true, insights: [] });
    http.expectOne((r) => r.url === '/api/jugadores/j1/partidas').flush({ items: [], total: 0 });
    const peticion = http.expectOne((r) => r.url === '/api/jugadores/j1/sinergias');
    expect(peticion.request.params.get('juego')).toBe('cs2');
    peticion.flush(sinergias);
    await pagina.whenStable();
    return pagina.nativeElement as HTMLElement;
  }

  afterEach(() => http.verify());

  const filas = (el: HTMLElement) =>
    [...el.querySelectorAll('.con-quien .mb-row')].map((f) => ({
      nombre: f.querySelector('.mb-name')!.textContent!.trim(),
      enlace: f.querySelector('.mb-name a')?.getAttribute('href') ?? null,
      valores: [...f.querySelectorAll('.mb-v > *')].map((v) => v.textContent!.replace(/\s+/g, ' ').trim()),
    }));

  it('enseña cada compañero con su winrate juntos y sin él, y al final cómo le va solo', async () => {
    const el = await abrir({
      solo: fila(null, null, 6, 2, 58),
      companeros: [fila('j2', 'Bea', 20, 13, 38), fila('j3', 'Carla', 12, 5, 61)],
    });

    expect(el.querySelector('.con-quien .card-t')!.textContent).toBe('Con quién');
    expect(filas(el)).toEqual([
      { nombre: 'Bea', enlace: '/jugador/j2?juego=cs2', valores: ['65 %', '20 partidas', 'sin: 38 %'] },
      { nombre: 'Carla', enlace: '/jugador/j3?juego=cs2', valores: ['42 %', '12 partidas', 'sin: 61 %'] },
      { nombre: 'Solo', enlace: null, valores: ['33 %', '6 partidas', 'con el equipo: 58 %'] },
    ]);
  });

  it('sin partidas suficientes con nadie, lo dice', async () => {
    const el = await abrir({ solo: null, companeros: [] });
    expect(filas(el)).toEqual([]);
    expect(el.querySelector('.con-quien .card-b')!.textContent!.trim()).toBe(
      'Aún no hay partidas suficientes con nadie del equipo.',
    );
  });
});
