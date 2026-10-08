import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { ChatDuende } from './duende/chat-duende';
import { I18n, NOMBRE_JUEGO } from './core/i18n';
import { Cabecera } from './layout/cabecera';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Cabecera, ChatDuende],
  template: `
    <app-cabecera />
    <main>
      <router-outlet />
    </main>
    <footer class="pie">
      <div class="wrap">
        <span>{{ t('comun.pie') }} · {{ t('comun.juegosDisponibles', { juegos: juegos }) }}</span>
        <span>{{ t('comun.fuentes') }}</span>
      </div>
    </footer>
    <app-chat-duende />
  `,
})
export class App {
  protected readonly t = inject(I18n).t;
  protected readonly juegos = Object.values(NOMBRE_JUEGO).join(' · ');
}
