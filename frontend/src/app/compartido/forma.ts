import { Component, computed, inject, input } from '@angular/core';
import { I18n } from '../core/i18n';

/** Últimos resultados como fichas de color. La más reciente, a la izquierda. */
@Component({
  selector: 'app-forma',
  template: `
    <div class="form" [class.big]="grande()" role="img" [attr.aria-label]="etiqueta()">
      @for (c of letras(); track $index) {
        <span class="chip" [class.w]="c === 'V'" [class.l]="c === 'D'" [class.q]="c === '?'">{{ i18n.letraForma(c) }}</span>
      }
    </div>
  `,
})
export class Forma {
  protected readonly i18n = inject(I18n);
  readonly forma = input.required<string>();
  readonly grande = input(false);

  protected readonly letras = computed(() => this.forma().split(''));
  protected readonly etiqueta = computed(() =>
    this.letras()
      .map((c) =>
        c === 'V' ? this.i18n.t('comun.victoria') : c === 'D' ? this.i18n.t('comun.derrota') : this.i18n.t('comun.sinResultado'),
      )
      .join(', '),
  );
}
