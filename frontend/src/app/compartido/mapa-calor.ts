import { Component, computed, inject, input } from '@angular/core';
import { I18n } from '../core/i18n';
import { CalorMapa, Punto } from '../core/modelos';

const ANCHO = 400;
const MARGEN = 14;
/** Casillas del mapa de calor a lo ancho. */
const CASILLAS = 26;

interface Casilla {
  x: number;
  y: number;
  lado: number;
  intensidad: number;
}

/**
 * Mapa de calor de dónde muere un jugador en un mapa (P12). No hay imagen del mapa: lo dibujan, en gris, las muertes
 * de todos en las partidas analizadas, y encima van sus muertes (en rojo las que nadie vengó) y el calor por casillas.
 * Las coordenadas son las del juego: la y crece hacia arriba.
 */
@Component({
  selector: 'app-mapa-calor',
  template: `
    @let v = vista();
    <svg class="mc" [attr.viewBox]="'0 0 ' + ancho + ' ' + v.alto" role="img" [attr.aria-label]="etiqueta()">
      @for (p of v.fondo; track $index) {
        <circle class="mc-fondo" [attr.cx]="p.x" [attr.cy]="p.y" r="1.6" />
      }
      @for (c of v.casillas; track $index) {
        <rect
          class="mc-calor"
          [attr.x]="c.x"
          [attr.y]="c.y"
          [attr.width]="c.lado"
          [attr.height]="c.lado"
          rx="2"
          [attr.fill-opacity]="0.12 + c.intensidad * 0.55"
        />
      }
      @for (m of v.muertes; track $index) {
        <circle class="mc-muerte" [class.tradeada]="m.tradeado" [attr.cx]="m.x" [attr.cy]="m.y" r="2.6" />
      }
      @for (z of v.zonas; track z.zona) {
        <text class="mc-zona" [attr.x]="z.x" [attr.y]="z.y" text-anchor="middle">{{ z.zona }}</text>
      }
    </svg>
    <div class="legend mc-leyenda">
      <span><i class="dot d"></i>{{ t('demos.sinTrade') }}</span>
      <span><i class="dot mc-dot-trade"></i>{{ t('demos.conTrade') }}</span>
    </div>
  `,
})
export class MapaCalor {
  protected readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  protected readonly ancho = ANCHO;

  readonly calor = input.required<CalorMapa>();

  protected readonly etiqueta = computed(() =>
    this.t('demos.mapaAria', { mapa: this.i18n.mapa(this.calor().mapa), muertes: this.calor().muertes.length }),
  );

  /** Todo pasado a coordenadas del dibujo, con la misma escala en los dos ejes. */
  protected readonly vista = computed(() => {
    const c = this.calor();
    const todos: Punto[] = [...c.fondo, ...c.muertes];
    if (!todos.length) return { alto: 120, fondo: [], muertes: [], zonas: [], casillas: [] as Casilla[] };
    const xs = todos.map((p) => p.x);
    const ys = todos.map((p) => p.y);
    const minX = Math.min(...xs);
    const maxY = Math.max(...ys);
    const anchoMundo = Math.max(1, Math.max(...xs) - minX);
    const altoMundo = Math.max(1, maxY - Math.min(...ys));
    const escala = (ANCHO - 2 * MARGEN) / Math.max(anchoMundo, altoMundo * 0.75);
    const alto = Math.round(altoMundo * escala + 2 * MARGEN);
    const a = (p: Punto) => ({ x: MARGEN + (p.x - minX) * escala, y: MARGEN + (maxY - p.y) * escala });

    const muertes = c.muertes.map((m) => ({ ...a(m), tradeado: m.tradeado }));
    // Calor: cuántas de sus muertes caen en cada casilla.
    const lado = (ANCHO - 2 * MARGEN) / CASILLAS;
    const cuenta = new Map<string, { x: number; y: number; n: number }>();
    for (const m of muertes) {
      const i = Math.floor((m.x - MARGEN) / lado);
      const j = Math.floor((m.y - MARGEN) / lado);
      const clave = `${i}:${j}`;
      const actual = cuenta.get(clave) ?? { x: MARGEN + i * lado, y: MARGEN + j * lado, n: 0 };
      actual.n++;
      cuenta.set(clave, actual);
    }
    const max = Math.max(1, ...[...cuenta.values()].map((x) => x.n));
    const casillas = [...cuenta.values()].map((x) => ({ x: x.x, y: x.y, lado, intensidad: x.n / max }));
    return {
      alto,
      fondo: c.fondo.map(a),
      muertes,
      zonas: c.zonas.map((z) => ({ ...a(z), zona: z.zona })),
      casillas,
    };
  });
}
