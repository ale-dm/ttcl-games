import { Component, input } from '@angular/core';

/** La cara del Duende: un cuadrado con dos ojos. */
@Component({
  selector: 'app-marca-duende',
  template: `<span
    class="dmark"
    aria-hidden="true"
    [style.width.px]="tam()"
    [style.height.px]="tam()"
    [style.border-radius.px]="tam() * 0.28"
    [style.gap.px]="tam() * 0.18"
  >
    <i [style.width.px]="tam() * 0.13" [style.height.px]="tam() * 0.3"></i>
    <i [style.width.px]="tam() * 0.13" [style.height.px]="tam() * 0.3"></i>
  </span>`,
  styles: `
    :host {
      display: inline-flex;
    }
  `,
})
export class MarcaDuende {
  readonly tam = input(22);
}
