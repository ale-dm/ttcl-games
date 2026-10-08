import { Injectable, effect, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { I18n } from './i18n';
import { Clave } from './textos';

/**
 * Título de la pestaña traducido. Las rutas ponen una clave de textos.ts como `title`; una página puede fijar uno
 * propio (el nombre del jugador). Se repinta al cambiar de idioma.
 */
@Injectable({ providedIn: 'root' })
export class TituloTraducido extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly i18n = inject(I18n);
  private readonly clave = signal<Clave | null>(null);
  private readonly propio = signal<string | null>(null);

  constructor() {
    super();
    effect(() => {
      const propio = this.propio();
      const clave = this.clave();
      const texto = propio ?? (clave ? this.i18n.t(clave) : null);
      this.title.setTitle(texto ? `${texto} · TTCL Games` : 'TTCL Games');
    });
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.propio.set(null);
    this.clave.set((this.buildTitle(snapshot) as Clave | undefined) ?? null);
  }

  /** Título propio de la página actual hasta la siguiente navegación. */
  fijar(texto: string): void {
    this.propio.set(texto);
  }
}
