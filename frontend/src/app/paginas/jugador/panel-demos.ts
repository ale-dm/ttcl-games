import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { MapaCalor } from '../../compartido/mapa-calor';
import { Api } from '../../core/api';
import { cargaReactiva } from '../../core/carga';
import { I18n } from '../../core/i18n';
import { FormatoValor, MetricasRondas, Periodo, ResumenDemos } from '../../core/modelos';
import { Clave } from '../../core/textos';

/** Una cifra de las demos: dónde está en las métricas, cómo se enseña y una ayuda opcional. */
interface Cifra {
  clave: keyof MetricasRondas;
  texto: Clave;
  formato: FormatoValor;
  ayuda?: Clave;
}

const CIFRAS: Cifra[] = [
  { clave: 'rating', texto: 'demos.rating', formato: 'dec', ayuda: 'demos.ratingAyuda' },
  { clave: 'kast', texto: 'demos.kast', formato: 'pct', ayuda: 'demos.kastAyuda' },
  { clave: 'aperturaPct', texto: 'demos.aperturas', formato: 'pct' },
  { clave: 'tradesPartida', texto: 'demos.trades', formato: 'dec', ayuda: 'demos.tradesAyuda' },
  { clave: 'tradeadasPct', texto: 'demos.tradeadas', formato: 'pct', ayuda: 'demos.tradeadasAyuda' },
  { clave: 'flashPartida', texto: 'demos.flash', formato: 'dec' },
  { clave: 'utilidadRonda', texto: 'demos.utilidad', formato: 'dec' },
  { clave: 'winrateRondas', texto: 'demos.rondasGanadas', formato: 'pct' },
];

/**
 * Lo que dicen las demos de CS2 de un jugador (P12): sus cifras frente al resto del equipo, CT y T, las rondas según
 * la compra y el mapa de calor de dónde muere, con las zonas donde más muere sin que le tradeen. Las cifras las trae la
 * página (también las enseña en el resumen); el mapa de calor lo pide este panel, mapa a mapa.
 */
@Component({
  selector: 'app-panel-demos',
  imports: [MapaCalor],
  template: `
    @let d = datos();
    <div class="demos">
      <section class="card demos-cifras">
        <div class="card-h">
          <div>
            <div class="card-t">{{ t('demos.titulo') }}</div>
            <div class="small muted">{{ t('demos.sub', { partidas: d.metricas.partidas, rondas: d.metricas.rondas }) }}</div>
          </div>
        </div>
        <div class="kpis kpis-4">
          @for (c of cifras; track c.clave) {
            <div class="kpi" [attr.title]="c.ayuda ? t(c.ayuda) : null">
              <div class="v num">
                @if (c.clave === 'aperturaPct') {
                  {{ t('demos.aperturasValor', { ganadas: d.metricas.aperturasGanadas, total: d.metricas.aperturas }) }}
                } @else {
                  {{ valor(d.metricas, c) }}
                }
              </div>
              <div class="l">
                {{ t(c.texto) }}
                @if (c.clave === 'aperturaPct') {
                  <span class="muted">· {{ i18n.pct(d.metricas.aperturaPct, 0) }}</span>
                }
              </div>
              @if (d.equipo) {
                <span class="delta even">{{ t('demos.equipo', { valor: valor(d.equipo, c) }) }}</span>
              }
            </div>
          }
        </div>
      </section>

      <section class="card demos-mapa">
        <div class="card-h">
          <div>
            <div class="card-t">{{ t('demos.donde') }}</div>
            <div class="small muted">{{ t('demos.dondeSub') }}</div>
          </div>
          @if (mapas().length > 1) {
            <label class="sr-only" for="demos-mapa">{{ t('demos.mapa') }}</label>
            <select id="demos-mapa" [value]="mapaActivo() ?? ''" (change)="elegirMapa($event)">
              @for (m of mapas(); track m) {
                <option [value]="m" [selected]="m === mapaActivo()">{{ i18n.mapa(m) }}</option>
              }
            </select>
          }
        </div>
        <div class="card-b demos-donde">
          @if (calor.estado().datos; as c) {
            @if (c.muertes.length) {
              <app-mapa-calor [calor]="c" />
            } @else {
              <p class="muted">{{ t('demos.sinMuertes') }}</p>
            }
          } @else {
            <div class="skel" style="height: 260px"></div>
          }
          @if (zonas().length) {
            <div class="demos-zonas">
              <div class="eyebrow">{{ t('demos.zonas') }}</div>
              <div class="mb">
                @for (z of zonas(); track z.zona) {
                  <div class="mb-row">
                    <div class="mb-name">{{ z.zona }}</div>
                    <div class="mb-track">
                      <div class="mb-bar" [attr.aria-label]="t('demos.zonaDetalle', { sin: z.sinTrade, muertes: z.muertes })">
                        <i class="l" [style.width.%]="porcentaje(z.sinTrade, zonaMax())"></i>
                      </div>
                    </div>
                    <div class="mb-v num">
                      <b>{{ z.sinTrade }}</b>
                      <small>{{ t('demos.zonaDetalle', { sin: z.sinTrade, muertes: z.muertes }) }}</small>
                    </div>
                  </div>
                }
              </div>
            </div>
          }
        </div>
      </section>

      <div class="demos-col">
        @if (d.lados.length) {
          <section class="card">
            <div class="card-h">
              <div>
                <div class="card-t">{{ t('demos.lados') }}</div>
                <div class="small muted">{{ t('demos.ladosSub') }}</div>
              </div>
            </div>
            <div class="card-b">
              <div class="mb">
                @for (l of d.lados; track l.lado) {
                  <div class="mb-row">
                    <div class="mb-name"><b>{{ l.lado }}</b></div>
                    <div class="mb-track">
                      <div class="mb-bar" [attr.aria-label]="i18n.pct(l.winrate)">
                        <i class="w" [style.width.%]="porcentaje(l.ganadas, l.rondas)"></i>
                        <i class="l" [style.width.%]="100 - porcentaje(l.ganadas, l.rondas)"></i>
                      </div>
                    </div>
                    <div class="mb-v num">
                      <b>{{ i18n.pct(l.winrate, 0) }}</b>
                      <small>{{ t('demos.rondasDe', { rondas: l.rondas }) }}</small>
                      <small>
                        {{
                          t('demos.ladoDetalle', {
                            rating: i18n.num(l.rating, 2, 2),
                            kast: i18n.pct(l.kast, 0),
                            adr: i18n.entero(l.adr),
                          })
                        }}
                      </small>
                    </div>
                  </div>
                }
              </div>
            </div>
          </section>
        }

        @if (d.economia.length) {
          <section class="card">
            <div class="card-h">
              <div>
                <div class="card-t">{{ t('demos.economia') }}</div>
                <div class="small muted">{{ t('demos.economiaSub') }}</div>
              </div>
            </div>
            <div class="card-b">
              <div class="mb">
                @for (c of d.economia; track c.compra) {
                  <div class="mb-row">
                    <div class="mb-name">{{ i18n.compra(c.compra) }}</div>
                    <div class="mb-track">
                      <div class="mb-bar" [attr.aria-label]="i18n.pct(c.winrate)">
                        <i class="w" [style.width.%]="porcentaje(c.ganadas, c.rondas)"></i>
                        <i class="l" [style.width.%]="100 - porcentaje(c.ganadas, c.rondas)"></i>
                      </div>
                    </div>
                    <div class="mb-v num">
                      <b>{{ i18n.pct(c.winrate, 0) }}</b>
                      <small>{{ t('demos.compraDetalle', { rondas: c.rondas, kpr: i18n.num(c.kpr, 2, 2) }) }}</small>
                    </div>
                  </div>
                }
              </div>
            </div>
          </section>
        }
      </div>
    </div>
  `,
})
export class PanelDemos {
  protected readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  private readonly api = inject(Api);
  protected readonly cifras = CIFRAS;

  readonly slug = input.required<string>();
  readonly periodo = input<Periodo>('todo');
  readonly datos = input.required<ResumenDemos>();

  /** El mapa elegido; null: el que más rondas tiene (lo elige la API). */
  private readonly mapa = signal<string | null>(null);

  protected readonly calor = cargaReactiva(
    () => ({ slug: this.slug(), periodo: this.periodo(), mapa: this.mapa() }),
    (p) => this.api.calor(p.slug, p.mapa, p.periodo),
  );

  protected readonly mapas = computed(() => this.calor.estado().datos?.mapas ?? []);
  protected readonly mapaActivo = computed(() => this.calor.estado().datos?.mapa ?? this.mapa());
  /** Las zonas del mapa que se ve, de las que más muere sin trade. */
  protected readonly zonas = computed(() => {
    const mapa = this.mapaActivo();
    return (this.datos().mapas.find((m) => m.mapa === mapa)?.zonas ?? []).filter((z) => z.sinTrade);
  });
  protected readonly zonaMax = computed(() => Math.max(1, ...this.zonas().map((z) => z.sinTrade)));

  constructor() {
    // Otro jugador u otro periodo: vuelve a su mapa más jugado.
    effect(() => {
      this.slug();
      this.periodo();
      this.mapa.set(null);
    });
  }

  protected elegirMapa(evento: Event): void {
    this.mapa.set((evento.target as HTMLSelectElement).value || null);
  }

  protected valor(m: MetricasRondas, c: Cifra): string {
    const v = m[c.clave];
    return this.i18n.valor(typeof v === 'number' ? v : null, c.formato);
  }

  protected porcentaje(parte: number, total: number): number {
    return total ? Math.round((parte / total) * 100) : 0;
  }
}
