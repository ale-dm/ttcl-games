import { NgTemplateOutlet } from '@angular/common';
import { Component, inject, input, output } from '@angular/core';
import { I18n } from '../core/i18n';
import { Voto } from '../core/modelos';

/** 👍 y 👎 bajo una recomendación o una respuesta del Duende (P7). Pulsar el que ya está marcado quita el voto. */
@Component({
  selector: 'app-botones-voto',
  imports: [NgTemplateOutlet],
  template: `
    <div class="voto" role="group" [attr.aria-label]="t('duende.votoPregunta')">
      @if (etiqueta()) {
        <span class="voto-l" aria-live="polite">{{ voto() ? t('duende.votoGracias') : t('duende.votoPregunta') }}</span>
      }
      <button
        type="button"
        class="si"
        [class.on]="voto() === 1"
        [attr.aria-pressed]="voto() === 1"
        [attr.aria-label]="t('duende.votoSi')"
        [title]="t('duende.votoSi')"
        (click)="pulsar(1)"
      >
        <ng-container *ngTemplateOutlet="pulgar" />
      </button>
      <button
        type="button"
        class="no"
        [class.on]="voto() === -1"
        [attr.aria-pressed]="voto() === -1"
        [attr.aria-label]="t('duende.votoNo')"
        [title]="t('duende.votoNo')"
        (click)="pulsar(-1)"
      >
        <ng-container *ngTemplateOutlet="pulgar" />
      </button>
    </div>

    <!-- El de "no me sirve" es el mismo, girado (styles.css). -->
    <ng-template #pulgar>
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M7 10v12" />
        <path
          d="M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z"
        />
      </svg>
    </ng-template>
  `,
})
export class BotonesVoto {
  protected readonly t = inject(I18n).t;

  readonly voto = input<1 | -1 | null>(null);
  /** Con "¿Te sirve?" delante, que pasa a "¡Gracias!" al votar. */
  readonly etiqueta = input(false);
  /** El voto nuevo: 1, -1 o 0 si se ha quitado. */
  readonly votar = output<Voto>();

  protected pulsar(voto: 1 | -1): void {
    this.votar.emit(this.voto() === voto ? 0 : voto);
  }
}
