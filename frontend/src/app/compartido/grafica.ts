import { Component, computed, inject, input, signal } from '@angular/core';
import { I18n } from '../core/i18n';
import { PuntoSerie } from '../core/modelos';

const ANCHO = 640;
const ALTO = 220;
const M = { izq: 30, der: 12, arr: 12, aba: 40 };

interface Punto {
  x: number;
  k: number | null;
  d: number | null;
  a: number | null;
  p: PuntoSerie;
}

/** Kills y muertes por partida (y asistencias, opcional), con el resultado de cada una debajo. SVG a mano. */
@Component({
  selector: 'app-grafica',
  template: `
    <div class="chart" (mouseleave)="activo.set(null)">
      <svg
        [attr.viewBox]="'0 0 ' + ancho + ' ' + alto"
        role="img"
        [attr.aria-label]="t('jugador.grafica')"
        (mousemove)="mover($event)"
      >
        @for (y of ejeY(); track y.valor) {
          <line class="gridl" [attr.x1]="m.izq" [attr.x2]="ancho - m.der" [attr.y1]="y.y" [attr.y2]="y.y" />
          <text class="axis" [attr.x]="m.izq - 8" [attr.y]="y.y + 3.5" text-anchor="end">{{ i18n.num(y.valor, 1) }}</text>
        }
        @for (e of ejeX(); track e.i) {
          <text class="axis" [attr.x]="e.x" [attr.y]="alto - 6" [attr.text-anchor]="e.ancla">{{ e.texto }}</text>
        }
        @for (pt of puntos(); track pt.p.partidaId) {
          <rect
            [class.rw]="pt.p.gano === true"
            [class.rl]="pt.p.gano === false"
            [attr.x]="pt.x - anchoBarra() / 2"
            [attr.y]="alto - m.aba + 12"
            [attr.width]="anchoBarra()"
            height="4"
            rx="2"
          />
        }
        @if (activo() !== null) {
          <line
            class="cursor"
            [attr.x1]="puntos()[activo()!].x"
            [attr.x2]="puntos()[activo()!].x"
            [attr.y1]="m.arr"
            [attr.y2]="alto - m.aba"
          />
        }
        @if (asistencias()) {
          <path class="ln a" [attr.d]="camino('a')" />
        }
        <path class="ln d" [class.faint]="activo() !== null" [attr.d]="camino('d')" />
        <path class="ln k" [attr.d]="camino('k')" />
        @for (pt of puntos(); track pt.p.partidaId; let i = $index) {
          @if (pt.d !== null) {
            <circle class="pt d" [attr.cx]="pt.x" [attr.cy]="y(pt.d)" [attr.r]="i === activo() ? 4.5 : 3" />
          }
          @if (pt.k !== null) {
            <circle class="pt k" [attr.cx]="pt.x" [attr.cy]="y(pt.k)" [attr.r]="i === activo() ? 4.5 : 3" />
          }
        }
      </svg>

      @if (activo() !== null) {
        @let pt = puntos()[activo()!];
        <div class="tt" [style.left.%]="(pt.x / ancho) * 100" [style.transform]="desplazamiento(pt.x)">
          <div class="tt-h">
            <span class="muted">{{ i18n.fechaHora(pt.p.fecha) }}</span>
            @if (pt.p.gano !== null) {
              <span class="res" [class.w]="pt.p.gano" [class.l]="!pt.p.gano">
                {{ pt.p.gano ? t('comun.victoria') : t('comun.derrota') }}
              </span>
            }
          </div>
          @if (pt.p.clave) {
            <div class="tt-m mono">{{ pt.p.clave }}</div>
          }
          <div class="tt-s num">
            <span><i class="dot k"></i>{{ pt.k ?? '—' }}</span>
            <span><i class="dot d"></i>{{ pt.d ?? '—' }}</span>
            <span><i class="dot a"></i>{{ pt.a ?? '—' }}</span>
          </div>
        </div>
      }
    </div>
  `,
})
export class Grafica {
  protected readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  readonly serie = input.required<PuntoSerie[]>();
  readonly asistencias = input(false);

  protected readonly ancho = ANCHO;
  protected readonly alto = ALTO;
  protected readonly m = M;
  protected readonly activo = signal<number | null>(null);

  private readonly maximo = computed(() => {
    const valores = this.serie().flatMap((p) => [p.kills ?? 0, p.muertes ?? 0, this.asistencias() ? (p.asistencias ?? 0) : 0]);
    return Math.max(5, Math.ceil(Math.max(...valores, 0) / 5) * 5);
  });

  protected readonly puntos = computed<Punto[]>(() => {
    const s = this.serie();
    const util = ANCHO - M.izq - M.der;
    const paso = s.length > 1 ? util / (s.length - 1) : 0;
    return s.map((p, i) => ({
      x: s.length > 1 ? M.izq + i * paso : M.izq + util / 2,
      k: p.kills,
      d: p.muertes,
      a: p.asistencias,
      p,
    }));
  });

  protected readonly anchoBarra = computed(() => {
    const n = Math.max(this.serie().length, 1);
    return Math.max(3, Math.min(18, ((ANCHO - M.izq - M.der) / n) * 0.55));
  });

  protected readonly ejeY = computed(() => {
    const max = this.maximo();
    return [0, max / 2, max].map((valor) => ({ valor, y: this.y(valor) }));
  });

  protected readonly ejeX = computed(() => {
    const pts = this.puntos();
    if (!pts.length) return [];
    const indices = [...new Set([0, Math.floor((pts.length - 1) / 2), pts.length - 1])];
    return indices.map((i) => ({
      i,
      x: pts[i].x,
      texto: this.i18n.fechaCorta(pts[i].p.fecha),
      ancla: i === 0 ? 'start' : i === pts.length - 1 ? 'end' : 'middle',
    }));
  });

  protected y(valor: number): number {
    const util = ALTO - M.arr - M.aba;
    return M.arr + util - (valor / this.maximo()) * util;
  }

  protected camino(campo: 'k' | 'd' | 'a'): string {
    let d = '';
    let abierto = false;
    for (const pt of this.puntos()) {
      const v = pt[campo];
      if (v === null) {
        abierto = false;
        continue;
      }
      d += `${abierto ? 'L' : 'M'}${pt.x.toFixed(1)},${this.y(v).toFixed(1)}`;
      abierto = true;
    }
    return d;
  }

  protected mover(e: MouseEvent): void {
    const svg = e.currentTarget as SVGSVGElement;
    const caja = svg.getBoundingClientRect();
    const x = ((e.clientX - caja.left) / caja.width) * ANCHO;
    const pts = this.puntos();
    if (!pts.length) return;
    let mejor = 0;
    for (let i = 1; i < pts.length; i++) if (Math.abs(pts[i].x - x) < Math.abs(pts[mejor].x - x)) mejor = i;
    this.activo.set(mejor);
  }

  /** Que el tooltip no se salga por los lados. */
  protected desplazamiento(x: number): string {
    const pct = x / ANCHO;
    if (pct < 0.18) return 'translateX(-10%)';
    if (pct > 0.82) return 'translateX(-90%)';
    return 'translateX(-50%)';
  }
}
