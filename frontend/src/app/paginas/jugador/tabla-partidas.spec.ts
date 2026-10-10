import { TestBed } from '@angular/core/testing';
import { I18n } from '../../core/i18n';
import { PartidaVista } from '../../core/modelos';
import { TablaPartidas } from './tabla-partidas';

const partida = (partidaId: number, comentario: string | null): PartidaVista => ({
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
  comentario,
});

describe('TablaPartidas', () => {
  it('debajo de cada partida con algo especial, lo que dice el Duende', async () => {
    TestBed.inject(I18n).cambiar('es');
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
  });
});
