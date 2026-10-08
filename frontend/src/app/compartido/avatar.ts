import { Component, computed, input } from '@angular/core';

/** Iniciales sobre un color estable por jugador (el tono sale del slug). */
@Component({
  selector: 'app-avatar',
  template: `<span
    class="avatar"
    aria-hidden="true"
    [style.width.px]="tam()"
    [style.height.px]="tam()"
    [style.border-radius.px]="tam() * 0.28"
    [style.font-size.px]="tam() * 0.36"
    [style.--ah]="tono()"
    >{{ iniciales() }}</span
  >`,
})
export class Avatar {
  readonly slug = input.required<string>();
  readonly nombre = input.required<string>();
  readonly tam = input(44);

  protected readonly iniciales = computed(() => {
    const partes = this.nombre().trim().split(/\s+/);
    if (partes.length > 1) return (partes[0][0] + partes[partes.length - 1][0]).toUpperCase();
    return partes[0].slice(0, 2).toUpperCase();
  });

  /** Ángulo áureo: slugs parecidos ("j1", "j2") caen en tonos bien distintos. */
  protected readonly tono = computed(() => {
    let h = 0;
    for (const c of this.slug()) h = (h * 31 + c.charCodeAt(0)) % 100003;
    return Math.round((h * 137.508) % 360);
  });
}
