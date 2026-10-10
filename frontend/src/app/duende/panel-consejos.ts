import { Component, computed, inject, input, signal } from '@angular/core';
import { MarcaDuende } from '../compartido/marca-duende';
import { I18n } from '../core/i18n';
import { Insight, Juego, NivelInsight } from '../core/modelos';
import { Clave } from '../core/textos';
import { BotonesVoto } from './botones-voto';
import { DuendeEstado } from './duende-estado';
import { Valoraciones } from './valoraciones';

const NIVEL: Record<NivelInsight, Clave> = {
  alto: 'duende.nivelAlto',
  medio: 'duende.nivelMedio',
  bien: 'duende.nivelBien',
  info: 'duende.nivelInfo',
};

/**
 * Recomendaciones del Duende para un jugador: en qué mejorar, qué hace bien y una caja para preguntarle. Con el jugador
 * y el juego, cada recomendación se puede valorar (👍/👎).
 */
@Component({
  selector: 'app-panel-consejos',
  imports: [BotonesVoto, MarcaDuende],
  template: `
    <section class="card dp" [class.wide]="ancho()">
      <div class="dp-h">
        <app-marca-duende [tam]="34" />
        <div class="dp-ht">
          <div class="dp-t">{{ t('duende.nombre') }}</div>
          <div class="small muted">{{ t('duende.panelSub') }}</div>
        </div>
      </div>

      @if (cargando()) {
        <div class="dp-list">
          @for (i of [1, 2, 3]; track i) {
            <div class="ins"><div class="skel" style="height: 74px"></div></div>
          }
        </div>
      } @else if (!disponible()) {
        <div class="dp-list"><div class="ins"><p>{{ t('duende.noDisponible') }}</p></div></div>
      } @else if (!insights().length) {
        <div class="dp-list"><div class="ins"><p>{{ t('duende.nadaQueDecir') }}</p></div></div>
      } @else {
        <div class="dp-list">
          @for (i of insights(); track i.id) {
            <article class="ins" [class]="i.nivel">
              <span class="ins-lvl">{{ t(nivel[i.nivel]) }}</span>
              <h4>{{ i.titulo }}</h4>
              <p>{{ i.texto }}</p>
              @if (i.barras.length) {
                <div class="ins-bars">
                  @for (b of i.barras; track b.etiqueta) {
                    <span>{{ b.etiqueta }}</span>
                    <span class="ib"><i [class.you]="b.tuyo" [style.width.%]="anchoBarra(b.valor, i)"></i></span>
                    <span class="num">{{ i18n.valor(b.valor, i.formato) }}</span>
                  }
                </div>
              }
              @if (i.consejo) {
                <div class="ins-tip"><b>{{ t('duende.consejo') }}</b> {{ i.consejo }}</div>
              }
              @if (slug(); as s) {
                @if (juego(); as j) {
                  <app-botones-voto
                    class="ins-voto"
                    [etiqueta]="true"
                    [voto]="valoraciones.votoConsejo(s, j, i.id)"
                    (votar)="valoraciones.votarConsejo(s, j, i, $event)"
                  />
                }
              }
            </article>
          }
        </div>
      }

      <form class="dp-f" (submit)="preguntar($event)">
        <div class="ask">
          <input
            #caja
            [value]="pregunta()"
            (input)="pregunta.set(caja.value)"
            [placeholder]="t('duende.placeholder')"
            [attr.aria-label]="t('duende.placeholder')"
            maxlength="2000"
          />
          <button class="btn primary sq" type="submit" [attr.aria-label]="t('duende.enviar')">→</button>
        </div>
        <div class="sugg inline">
          @for (s of duende.sugerencias().slice(0, 3); track s) {
            <button type="button" (click)="duende.abrir(s)">{{ s }}</button>
          }
        </div>
      </form>
    </section>
  `,
})
export class PanelConsejos {
  protected readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  protected readonly duende = inject(DuendeEstado);
  protected readonly valoraciones = inject(Valoraciones);
  protected readonly nivel = NIVEL;

  readonly insights = input<Insight[]>([]);
  readonly cargando = input(false);
  readonly disponible = input(true);
  /** En ancho completo, las recomendaciones van en varias columnas. */
  readonly ancho = input(false);
  /** De quién y de qué juego son: hacen falta para valorarlas. */
  readonly slug = input<string | null>(null);
  readonly juego = input<Juego | null>(null);

  protected readonly pregunta = signal('');

  /** Escala de cada grupo de barras: la mayor llena el 100 %. */
  private readonly maximos = computed(
    () => new Map(this.insights().map((i) => [i.id, Math.max(...i.barras.map((b) => Math.abs(b.valor)), 0.0001)])),
  );

  protected anchoBarra(valor: number, insight: Insight): number {
    const max = this.maximos().get(insight.id) ?? 1;
    return Math.max(2, Math.round((Math.abs(valor) / max) * 100));
  }

  protected preguntar(evento: Event): void {
    evento.preventDefault();
    const texto = this.pregunta().trim();
    if (!texto) return;
    this.duende.abrir(texto);
    this.pregunta.set('');
  }
}
