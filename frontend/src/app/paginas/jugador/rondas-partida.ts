import { Component, computed, inject, input } from '@angular/core';
import { I18n } from '../../core/i18n';
import { FilaRonda, RondasPartida } from '../../core/modelos';

/**
 * Las rondas de una partida analizada (P12), una casilla por ronda: arriba el lado, el número son sus kills, una ✕ si
 * murió y el color dice si se ganó la ronda. Al pasar por encima (o con lector de pantalla), todo lo de esa ronda.
 */
@Component({
  selector: 'app-rondas-partida',
  template: `
    @let d = datos();
    <div class="rp">
      <p class="small rp-resumen num">
        {{
          t('rondas.resumen', {
            rating: i18n.num(d.metricas.rating, 2, 2),
            kast: i18n.pct(d.metricas.kast, 0),
            adr: i18n.entero(d.metricas.adr),
            ganadas: d.metricas.aperturasGanadas,
            aperturas: d.metricas.aperturas,
            trades: d.metricas.trades,
          })
        }}
      </p>
      <ol class="rp-tira">
        @for (r of d.rondas; track r.ronda) {
          <li
            class="rp-r"
            [class.w]="r.gano === true"
            [class.l]="r.gano === false"
            [class.cambio]="cambios().has(r.ronda)"
            [attr.title]="descripcion(r)"
          >
            <span class="sr-only">{{ descripcion(r) }}</span>
            <span class="rp-lado" aria-hidden="true">{{ r.lado ?? '·' }}</span>
            <b class="rp-k num" aria-hidden="true">{{ r.kills }}</b>
            <span class="rp-m" aria-hidden="true">
              @if (r.murio) {
                <span [class.tradeado]="r.tradeado">✕</span>
              }
            </span>
          </li>
        }
      </ol>
      <p class="small muted rp-leyenda">{{ t('rondas.leyenda') }}</p>
    </div>
  `,
})
export class RondasPartidaVista {
  protected readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  readonly datos = input.required<RondasPartida>();

  /** Rondas en las que se cambia de lado (la primera de cada mitad), para separarlas. */
  protected readonly cambios = computed(() => {
    const rondas = this.datos().rondas;
    return new Set(rondas.filter((r, i) => i > 0 && r.lado !== rondas[i - 1].lado).map((r) => r.ronda));
  });

  /** "Ronda 4 (T) · ganada · 2 kills · moriste en Banana, con trade · ganaste la apertura · Forzada". */
  protected descripcion(r: FilaRonda): string {
    const partes = [
      this.t('rondas.ronda', { n: r.ronda }) + (r.lado ? ` (${r.lado})` : ''),
      r.gano === null ? null : this.t(r.gano ? 'rondas.ganada' : 'rondas.perdida'),
      this.t('rondas.kills', { n: r.kills }),
      r.murio
        ? (r.muerteZona ? this.t('rondas.murio', { zona: r.muerteZona }) : this.t('rondas.murioSinZona')) +
          (r.tradeado ? `, ${this.t('rondas.tradeado')}` : '')
        : this.t('rondas.vivo'),
      r.apertura ? this.t(`rondas.apertura.${r.apertura}`) : null,
      r.compra ? this.i18n.compra(r.compra) : null,
    ];
    return partes.filter((p) => p).join(' · ');
  }
}
