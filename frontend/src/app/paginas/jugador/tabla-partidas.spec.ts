import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { I18n } from '../../core/i18n';
import { FilaRonda, PartidaVista, RondasPartida } from '../../core/modelos';
import { TablaPartidas } from './tabla-partidas';

const partida = (partidaId: number, comentario: string | null, analizada = false): PartidaVista => ({
  partidaId,
  juego: 'cs2',
  jugadaEn: '2026-10-09T20:00:00Z',
  modo: 'de_nuke',
  gano: false,
  kills: 12,
  muertes: 18,
  asistencias: 3,
  datos: { mapa: 'de_nuke', adr: 70.4, hs_pct: 40 },
  companeros: [],
  analizada,
  comentario,
});

const ronda = (n: number, lado: 'CT' | 'T', gano: boolean, kills: number, murio: boolean, extra: Partial<FilaRonda> = {}): FilaRonda => ({
  partidaId: 2,
  jugadaEn: '2026-10-09T20:00:00Z',
  mapa: 'de_nuke',
  ronda: n,
  lado,
  gano,
  kills,
  asistencias: 0,
  asistenciasFlash: 0,
  dano: kills * 90,
  danoUtilidad: 0,
  murio,
  muerteX: murio ? 1 : null,
  muerteY: murio ? 2 : null,
  muerteZona: murio ? 'Outside' : null,
  tradeado: false,
  trades: 0,
  apertura: null,
  equipamiento: 4200,
  compra: 'completa',
  kast: kills > 0 || !murio,
  ...extra,
});

describe('TablaPartidas', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(I18n).cambiar('es');
  });

  it('debajo de cada partida con algo especial, lo que dice el Duende', async () => {
    const tabla = TestBed.createComponent(TablaPartidas);
    tabla.componentRef.setInput('juego', 'cs2');
    tabla.componentRef.setInput('partidas', [partida(1, 'Tercera derrota seguida en Nuke.'), partida(2, null)]);
    await tabla.whenStable();

    const el = tabla.nativeElement as HTMLElement;
    const filas = [...el.querySelectorAll('tbody tr')];
    expect(filas.length).toBe(3); // dos partidas y un comentario
    expect(filas[0].classList).toContain('con-coment');
    expect(filas[1].classList).toContain('coment');
    expect(filas[1].textContent!.replace(/\s+/g, ' ').trim()).toBe(
      'El Duende dice: Tercera derrota seguida en Nuke.',
    );
    expect(filas[2].classList).not.toContain('con-coment');
    // Sin jugador, no hay columna de rondas.
    expect(el.querySelectorAll('thead th').length).toBe(8);
  });

  it('las partidas con la demo analizada despliegan sus rondas (P12)', async () => {
    const http = TestBed.inject(HttpTestingController);
    const tabla = TestBed.createComponent(TablaPartidas);
    tabla.componentRef.setInput('juego', 'cs2');
    tabla.componentRef.setInput('slug', 'j3');
    tabla.componentRef.setInput('partidas', [partida(1, null), partida(2, 'Quinta victoria seguida.', true)]);
    await tabla.whenStable();
    const el = tabla.nativeElement as HTMLElement;
    expect(el.querySelectorAll('thead th').length).toBe(9);
    const botones = el.querySelectorAll<HTMLButtonElement>('.rp-btn');
    expect(botones.length).toBe(1); // solo la analizada
    expect(botones[0].getAttribute('aria-label')).toBe('Ver las rondas de la partida');

    botones[0].click();
    await tabla.whenStable();
    expect(el.querySelector('.rondas-fila')!.textContent).toContain('Cargando las rondas');
    const datos: RondasPartida = {
      partidaId: 2,
      mapa: 'de_nuke',
      metricas: {
        partidas: 1,
        rondas: 3,
        rating: 1.21,
        kast: 66.7,
        adr: 90,
        kpr: 1,
        dpr: 0.67,
        aperturas: 2,
        aperturasGanadas: 1,
        aperturaPct: 50,
        trades: 1,
        tradesPartida: 1,
        muertes: 2,
        muertesTradeadas: 1,
        tradeadasPct: 50,
        flashPartida: 0,
        utilidadRonda: 0,
        winrateRondas: 66.7,
      },
      rondas: [
        ronda(1, 'CT', true, 2, false, { apertura: 'ganada', compra: 'pistola' }),
        ronda(12, 'CT', false, 0, true, { apertura: 'perdida', tradeado: true }),
        ronda(13, 'T', true, 1, true),
      ],
    };
    http.expectOne('/api/jugadores/j3/partidas/2/rondas').flush(datos);
    await tabla.whenStable();

    const fila = el.querySelector('.rondas-fila')!;
    expect(fila.querySelector('.rp-resumen')!.textContent!.replace(/\s+/g, ' ').trim()).toBe(
      'Rating 1,21 · KAST 67 % · ADR 90 · aperturas 1/2 · trades 1',
    );
    const casillas = [...fila.querySelectorAll('.rp-r')];
    expect(casillas.map((c) => c.querySelector('.rp-k')!.textContent)).toEqual(['2', '0', '1']);
    expect(casillas.map((c) => c.classList.contains('w'))).toEqual([true, false, true]);
    // La 13 es la primera de T: va separada.
    expect(casillas.map((c) => c.classList.contains('cambio'))).toEqual([false, false, true]);
    expect(casillas[0].getAttribute('title')).toBe(
      'Ronda 1 (CT) · ganada · 2 kills · sobreviviste · ganaste la apertura · Pistola',
    );
    expect(casillas[1].querySelector('.sr-only')!.textContent).toBe(
      'Ronda 12 (CT) · perdida · 0 kills · moriste en Outside, con trade · perdiste la apertura · Completa',
    );

    // Otra vez: se pliega.
    el.querySelector<HTMLButtonElement>('.rp-btn')!.click();
    await tabla.whenStable();
    expect(el.querySelector('.rondas-fila')).toBeNull();
    http.verify();
  });
});
