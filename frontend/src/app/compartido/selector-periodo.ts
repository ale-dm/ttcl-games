import { Component, inject, input, output } from '@angular/core';
import { I18n } from '../core/i18n';
import { PERIODOS, Periodo } from '../core/modelos';
import { Clave } from '../core/textos';

/** Botones de periodo: 7 días, 30 días o todo. La página decide qué hacer al cambiar (normalmente, la URL). */
@Component({
  selector: 'app-selector-periodo',
  template: `
    <div class="seg periodo" [class.sm]="compacto()" role="group" [attr.aria-label]="i18n.t('periodo.etiqueta')">
      @for (p of periodos; track p) {
        <button type="button" [class.on]="p === periodo()" [attr.aria-pressed]="p === periodo()" (click)="cambio.emit(p)">
          {{ i18n.t(clave(p)) }}
        </button>
      }
    </div>
  `,
})
export class SelectorPeriodo {
  protected readonly i18n = inject(I18n);
  protected readonly periodos = PERIODOS;
  readonly periodo = input.required<Periodo>();
  /** Pequeño, como el selector de juego de las pestañas del perfil; en las cabeceras, a tamaño normal. */
  readonly compacto = input(true);
  readonly cambio = output<Periodo>();

  protected clave(p: Periodo): Clave {
    return `periodo.${p}`;
  }
}
