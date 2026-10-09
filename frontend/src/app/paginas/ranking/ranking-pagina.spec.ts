import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { I18n } from '../../core/i18n';
import { Ranking, ResumenJuego } from '../../core/modelos';
import { RankingPagina } from './ranking-pagina';

describe('RankingPagina · periodo', () => {
  const resumen = (partidas: number, kd: number): ResumenJuego => ({
    juego: 'cs2',
    partidas,
    victorias: 0,
    derrotas: 0,
    winrate: 50,
    kd,
    killsMedia: null,
    muertesMedia: null,
    asistenciasMedia: null,
    datosMedios: {},
    forma: '',
    ultimaPartida: null,
  });

  let http: HttpTestingController;
  let pagina: ComponentFixture<RankingPagina>;

  /** Abre el ranking de CS2 de los últimos 30 días y contesta con las filas que se pasen. */
  async function abrir(filas: Ranking['filas']): Promise<HTMLElement> {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(I18n).cambiar('es');
    http = TestBed.inject(HttpTestingController);
    pagina = TestBed.createComponent(RankingPagina);
    pagina.componentRef.setInput('periodo', '30d');
    await pagina.whenStable();
    const peticion = http.expectOne((r) => r.url === '/api/ranking');
    expect(peticion.request.params.get('juego')).toBe('cs2');
    expect(peticion.request.params.get('periodo')).toBe('30d');
    peticion.flush({ juego: 'cs2', metricas: ['winrate', 'kd'], filas });
    await pagina.whenStable();
    return pagina.nativeElement as HTMLElement;
  }

  afterEach(() => http.verify());

  it('ordena a quien jugó en esos días y los enlaces conservan el periodo', async () => {
    const el = await abrir([
      { slug: 'j1', nombre: 'Ana', resumen: resumen(12, 0.9) },
      { slug: 'j2', nombre: 'Bea', resumen: resumen(8, 1.3) },
    ]);
    const enlaces = [...el.querySelectorAll('tbody a')].map((a) => a.getAttribute('href'));
    expect(enlaces).toEqual(['/jugador/j2?juego=cs2&periodo=30d', '/jugador/j1?juego=cs2&periodo=30d']);
    expect(el.querySelector('.periodo button.on')!.textContent!.trim()).toBe('30 días');
  });

  it('si nadie jugó en esos días, lo dice', async () => {
    const el = await abrir([]);
    expect(el.querySelector('.aviso')!.textContent!.trim()).toBe(
      'Nadie del equipo ha jugado a Counter-Strike 2 en los últimos 30 días.',
    );
  });
});
