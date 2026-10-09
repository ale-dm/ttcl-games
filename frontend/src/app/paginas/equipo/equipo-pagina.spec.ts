import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { I18n } from '../../core/i18n';
import { Grupo, GruposJuego } from '../../core/modelos';
import { EquipoPagina } from './equipo-pagina';

const grupo = (nombres: string[], partidas: number, victorias: number): Grupo => ({
  jugadores: nombres.map((n) => ({ slug: n.toLowerCase().replace(' ', ''), nombre: n })),
  partidas,
  victorias,
  winrate: Math.round((1000 * victorias) / partidas) / 10,
});

describe('EquipoPagina · los que mejor se entienden', () => {
  let http: HttpTestingController;

  /** Abre la página del equipo (sin tarjetas) y contesta los grupos con lo que se le pase. */
  async function abrir(grupos: GruposJuego[]): Promise<HTMLElement> {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18n).cambiar('es');
    http = TestBed.inject(HttpTestingController);
    const pagina = TestBed.createComponent(EquipoPagina);
    await pagina.whenStable();
    http.expectOne((r) => r.url === '/api/equipo').flush([]);
    http.expectOne('/api/estado').flush({ ultimaSync: null, demo: true, fuentes: {}, duende: {} });
    const peticion = http.expectOne((r) => r.url === '/api/equipo/grupos');
    expect(peticion.request.params.has('juego')).toBe(false); // sin filtro, todos los juegos
    peticion.flush(grupos);
    await pagina.whenStable();
    return pagina.nativeElement as HTMLElement;
  }

  afterEach(() => http.verify());

  const texto = (el: Element) => el.textContent!.replace(/\s+/g, ' ').trim();

  it('enseña el mejor dúo y el mejor trío de cada juego', async () => {
    const el = await abrir([
      {
        juego: 'cs2',
        duos: [grupo(['Jugador 2', 'Jugador 3'], 32, 19), grupo(['Jugador 1', 'Jugador 2'], 33, 13)],
        trios: [grupo(['Jugador 1', 'Jugador 2', 'Jugador 3'], 23, 12)],
      },
      { juego: 'smite2', duos: [grupo(['Jugador 1', 'Jugador 4'], 32, 16)], trios: [] },
    ]);

    const tarjetas = [...el.querySelectorAll('.grupos .grupo')].map((g) => ({
      tipo: texto(g.querySelector('.eyebrow')!),
      quienes: texto(g.querySelector('.grupo-n')!),
      winrate: texto(g.querySelector('.grupo-v b')!),
      partidas: texto(g.querySelector('.grupo-v span')!),
    }));
    expect(tarjetas).toEqual([
      { tipo: 'CS2 · Dúo', quienes: 'Jugador 2 + Jugador 3', winrate: '59 %', partidas: '19 de 32 juntos' },
      { tipo: 'CS2 · Trío', quienes: 'Jugador 1 + Jugador 2 + Jugador 3', winrate: '52 %', partidas: '12 de 23 juntos' },
      { tipo: 'SMITE 2 · Dúo', quienes: 'Jugador 1 + Jugador 4', winrate: '50 %', partidas: '16 de 32 juntos' },
    ]);
    const enlace = el.querySelector('.grupos .grupo-n a')!;
    expect(enlace.getAttribute('href')).toBe('/jugador/jugador2?juego=cs2');
  });

  it('sin dúos ni tríos con partidas suficientes, no enseña la sección', async () => {
    const el = await abrir([{ juego: 'cs2', duos: [], trios: [] }]);
    expect(el.querySelector('.grupos')).toBeNull();
  });
});
