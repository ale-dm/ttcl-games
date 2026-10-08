import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MarcaDuende } from '../compartido/marca-duende';
import { I18n } from '../core/i18n';

@Component({
  selector: 'app-no-encontrada',
  imports: [RouterLink, MarcaDuende],
  template: `
    <div class="wrap page" style="text-align: center; padding-top: 80px">
      <app-marca-duende [tam]="56" />
      <h1 style="margin-top: 20px">{{ t('noEncontrada.titulo') }}</h1>
      <p class="sub" style="margin-inline: auto">{{ t('noEncontrada.texto') }}</p>
      <p style="margin-top: 24px"><a class="btn" routerLink="/">{{ t('noEncontrada.volver') }}</a></p>
    </div>
  `,
})
export class NoEncontradaPagina {
  protected readonly t = inject(I18n).t;
}
