import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { I18n } from '../../core/i18n';
import { CalorMapa, DetalleJuego, JugadorVista, MetricasRondas, ResumenDemos, ResumenJuego } from '../../core/modelos';
import { JugadorPagina } from './jugador-pagina';

const VACIAS: MetricasRondas = {
  partidas: 0,
  rondas: 0,
  rating: null,
  kast: null,
  adr: null,
  kpr: null,
  dpr: null,
  aperturas: 0,
  aperturasGanadas: 0,
  aperturaPct: null,
  trades: 0,
  tradesPartida: null,
  muertes: 0,
  muertesTradeadas: 0,
  tradeadasPct: null,
  flashPartida: null,
  utilidadRonda: null,
  winrateRondas: null,
};
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
const DEMOS: ResumenDemos = {
  metricas: {
    ...VACIAS,
    partidas: 12,
    rondas: 260,
    rating: 1.12,
    kast: 74.2,
    aperturas: 40,
    aperturasGanadas: 22,
    aperturaPct: 55,
    tradesPartida: 3.25,
    tradeadasPct: 21.4,
    flashPartida: 0.5,
    utilidadRonda: 6.1,
    winrateRondas: 52.3,
  },
  equipo: { ...VACIAS, partidas: 30, rondas: 650, rating: 1.02, kast: 71, tradeadasPct: 30 },
  lados: [
    { lado: 'CT', rondas: 130, ganadas: 75, winrate: 57.7, rating: 1.31, kast: 78, adr: 90 },
    { lado: 'T', rondas: 130, ganadas: 61, winrate: 46.9, rating: 0.93, kast: 70, adr: 70 },
  ],
  economia: [
    { compra: 'pistola', rondas: 24, ganadas: 12, winrate: 50, kpr: 0.7 },
    { compra: 'completa', rondas: 150, ganadas: 93, winrate: 62, kpr: 0.8 },
  ],
  mapas: [
    {
      mapa: 'de_inferno',
      partidas: 6,
      muertes: 80,
      sinTrade: 60,
      zonas: [
        { zona: 'Banana', muertes: 30, sinTrade: 27 },
        { zona: 'Bombsite B', muertes: 10, sinTrade: 0 },
      ],
    },
  ],
};
const CALOR: CalorMapa = {
  mapa: 'de_inferno',
  mapas: ['de_inferno', 'de_nuke'],
  muertes: [
    { x: 200, y: 1600, zona: 'Banana', tradeado: false },
    { x: 210, y: 1620, zona: 'Banana', tradeado: true },
  ],
  fondo: [
    { x: -1600, y: 400 },
    { x: 2200, y: 2600 },
    { x: 300, y: 0 },
  ],
  zonas: [{ zona: 'Banana', x: 205, y: 1610 }],
};

describe('JugadorPagina · demos (P12)', () => {
  let http: HttpTestingController;
  afterEach(() => http.verify());

  /** El perfil de Ana en CS2, en la pestaña que se diga, con las demos que se le pasen. */
  async function abrir(
    tab: string | undefined,
    demos: ResumenDemos,
  ): Promise<{ el: HTMLElement; pagina: ComponentFixture<JugadorPagina> }> {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18n).cambiar('es');
    http = TestBed.inject(HttpTestingController);
    const pagina = TestBed.createComponent(JugadorPagina);
    pagina.componentRef.setInput('slug', 'j1');
    if (tab) pagina.componentRef.setInput('tab', tab);
    await pagina.whenStable();
    http.expectOne('/api/jugadores/j1').flush(ANA);
    await pagina.whenStable();
    const detalle: DetalleJuego = { juego: 'cs2', resumen: RESUMEN, reciente: RESUMEN, equipo: null, desglose: [], serie: [] };
    http.expectOne('/api/jugadores/j1/juegos/cs2').flush(detalle);
    http.expectOne((r) => r.url === '/api/jugadores/j1/consejos').flush({ disponible: true, insights: [] });
    http.expectOne((r) => r.url === '/api/jugadores/j1/partidas').flush({ items: [], total: 0 });
    http.expectOne((r) => r.url === '/api/jugadores/j1/sinergias').flush({ solo: null, companeros: [] });
    http
      .expectOne((r) => r.url === '/api/jugadores/j1/sesiones')
      .flush({ sesiones: 0, partidasPorSesion: null, porOrden: [], trasResultado: [], porFranja: [] });
    http.expectOne('/api/jugadores/j1/demos').flush(demos);
    await pagina.whenStable();
    return { el: pagina.nativeElement as HTMLElement, pagina };
  }

  const texto = (el: Element | null) => el?.textContent!.replace(/\s+/g, ' ').trim() ?? null;

  it('en el resumen, lo breve de sus demos y la pestaña para verlo todo', async () => {
    const { el } = await abrir(undefined, DEMOS);
    const tabs = [...el.querySelectorAll('.tab')].map((t) => t.textContent!.trim());
    expect(tabs).toEqual(['Resumen', 'Partidas', 'Mapas', 'Demos']);
    const breve = el.querySelector('.demos-breve')!;
    expect(texto(breve.querySelector('.card-t'))).toBe('Lo que dicen tus demos');
    expect([...breve.querySelectorAll('.stat .v')].map((v) => texto(v))).toEqual(['1,12', '74 %', '3,25', '21 %']);
    expect([...breve.querySelectorAll('.demos-breve-l')].map((l) => texto(l))).toEqual([
      'Rating de CT: 1,31 · de T: 0,93',
      'Donde más mueres sin trade: Banana (Inferno)',
    ]);
  });

  it('en su pestaña, las cifras frente al equipo, CT y T, la compra y el mapa de calor', async () => {
    const { el, pagina } = await abrir('demos', DEMOS);
    const calor = http.expectOne((r) => r.url === '/api/jugadores/j1/demos/calor');
    expect(calor.request.params.has('mapa')).toBe(false);
    calor.flush(CALOR);
    await pagina.whenStable();

    const kpis = [...el.querySelectorAll('.demos-cifras .kpi')].map((k) => ({
      v: texto(k.querySelector('.v')),
      l: texto(k.querySelector('.l')),
      eq: texto(k.querySelector('.delta')),
    }));
    expect(kpis[0]).toEqual({ v: '1,12', l: 'Rating', eq: 'equipo: 1,02' });
    expect(kpis[2]).toEqual({ v: '22 de 40', l: 'Duelos de apertura · 55 %', eq: 'equipo: —' });
    expect(kpis[4].l).toBe('Muertes con trade');
    // El mapa de calor: el fondo, sus muertes (la que nadie vengó, en rojo) y el nombre de la zona.
    const svg = el.querySelector('svg.mc')!;
    expect(svg.getAttribute('aria-label')).toBe('Mapa de calor de tus muertes en Inferno: 2 muertes');
    expect(svg.querySelectorAll('.mc-fondo').length).toBe(3);
    expect(svg.querySelectorAll('.mc-muerte').length).toBe(2);
    expect(svg.querySelectorAll('.mc-muerte.tradeada').length).toBe(1);
    expect(texto(svg.querySelector('.mc-zona'))).toBe('Banana');
    // Solo las zonas con muertes sin trade.
    const zonas = [...el.querySelectorAll('.demos-zonas .mb-row')];
    expect(zonas.map((f) => [texto(f.querySelector('.mb-name')), texto(f.querySelector('.mb-v small'))])).toEqual([
      ['Banana', '27 de 30 sin trade'],
    ]);
    const filasDe = (titulo: string) =>
      [...el.querySelectorAll('.demos .card')]
        .find((c) => texto(c.querySelector('.card-t')) === titulo)!
        .querySelectorAll('.mb-row');
    expect([...filasDe('CT y T')].map((f) => [...f.querySelectorAll('.mb-name, .mb-v > *')].map(texto))).toEqual([
      ['CT', '58 %', '130 rondas', 'rating 1,31 · KAST 78 % · ADR 90'],
      ['T', '47 %', '130 rondas', 'rating 0,93 · KAST 70 % · ADR 70'],
    ]);
    expect([...filasDe('Según la compra')].map((f) => texto(f.querySelector('.mb-name')))).toEqual([
      'Pistola',
      'Completa',
    ]);

    // Otro mapa: lo pide a la API.
    const select = el.querySelector<HTMLSelectElement>('#demos-mapa')!;
    select.value = 'de_nuke';
    select.dispatchEvent(new Event('change'));
    await pagina.whenStable();
    const nuke = http.expectOne((r) => r.url === '/api/jugadores/j1/demos/calor');
    expect(nuke.request.params.get('mapa')).toBe('de_nuke');
    nuke.flush({ ...CALOR, mapa: 'de_nuke', muertes: [] });
    await pagina.whenStable();
    expect(texto(el.querySelector('.demos-donde p'))).toBe('Sin muertes analizadas en este mapa.');
  });

  it('sin demos analizadas, lo dice en su pestaña y en el resumen no hay nada', async () => {
    const { el } = await abrir('demos', { metricas: VACIAS, equipo: null, lados: [], economia: [], mapas: [] });
    expect(texto(el.querySelector('.demos-vacio'))).toContain('Aún no hay ninguna demo analizada de Ana.');
    expect(el.querySelector('app-panel-demos')).toBeNull();
    expect(el.querySelector('.demos-breve')).toBeNull();
  });
});
