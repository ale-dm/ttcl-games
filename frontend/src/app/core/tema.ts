import { DOCUMENT } from '@angular/common';
import { Injectable, effect, inject, signal } from '@angular/core';
import { guardarPreferencias } from './preferencias';

export type Tema = 'light' | 'dark';

/** Modo claro / oscuro. El valor inicial lo deja index.html en <html data-theme> antes de pintar. */
@Injectable({ providedIn: 'root' })
export class TemaService {
  private readonly doc = inject(DOCUMENT);
  readonly tema = signal<Tema>(this.doc.documentElement.dataset['theme'] === 'light' ? 'light' : 'dark');

  constructor() {
    effect(() => {
      this.doc.documentElement.dataset['theme'] = this.tema();
    });
  }

  alternar(): void {
    const nuevo: Tema = this.tema() === 'dark' ? 'light' : 'dark';
    this.tema.set(nuevo);
    guardarPreferencias({ theme: nuevo });
  }
}
