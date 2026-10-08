import { Component, ElementRef, afterRenderEffect, effect, inject, signal, viewChild } from '@angular/core';
import { MarcaDuende } from '../compartido/marca-duende';
import { I18n } from '../core/i18n';
import { DuendeEstado } from './duende-estado';
import { TextoRico } from './texto-rico';

/** Panel lateral del chat y botón flotante "Pregunta al Duende". */
@Component({
  selector: 'app-chat-duende',
  imports: [MarcaDuende, TextoRico],
  host: { '(document:keydown.escape)': 'duende.cerrar()' },
  template: `
    @if (!duende.abierto()) {
      <button class="fab" type="button" (click)="duende.abrir()">
        <app-marca-duende [tam]="26" />
        <span class="fab-txt">{{ t('duende.fab') }}</span>
      </button>
    }

    <div class="scrim" [class.open]="duende.abierto()" (click)="duende.cerrar()"></div>
    <aside
      class="drawer"
      [class.open]="duende.abierto()"
      role="dialog"
      aria-modal="true"
      [attr.aria-hidden]="!duende.abierto()"
      [attr.aria-label]="t('duende.nombre')"
    >
      <div class="dr-h">
        <app-marca-duende [tam]="30" />
        <div class="dr-ht">
          <div class="card-t">{{ t('duende.nombre') }}</div>
          <div class="ctx">{{ duende.etiqueta() }}</div>
        </div>
        @if (duende.mensajes().length) {
          <button class="btn ghost sm" type="button" (click)="duende.reiniciar()">{{ t('duende.reiniciar') }}</button>
        }
        <button class="icon-btn" type="button" (click)="duende.cerrar()" [attr.aria-label]="t('duende.cerrar')">✕</button>
      </div>

      <div class="msgs" #lista aria-live="polite">
        <div class="msg a"><app-texto-rico [texto]="duende.saludo()" /></div>
        @for (m of duende.mensajes(); track $index) {
          <div class="msg" [class.u]="m.rol === 'usuario'" [class.a]="m.rol === 'duende'" [class.err]="m.error">
            <app-texto-rico [texto]="m.texto" />
          </div>
          @if (m.origen) {
            <div class="msg-meta">
              {{ m.origen === 'gemini' ? t('duende.origenGemini', { modelo: m.modelo ?? '' }) : t('duende.origenReglas') }}
            </div>
          }
        }
        @if (duende.pensando()) {
          <div class="msg a typing" role="status">
            <span>{{ t('duende.pensando') }}</span><i></i><i></i><i></i>
          </div>
        }
      </div>

      @if (!duende.pensando()) {
        <div class="sugg">
          @for (s of duende.sugerencias(); track s) {
            <button type="button" (click)="duende.enviar(s)">{{ s }}</button>
          }
        </div>
      }

      <form class="composer" (submit)="enviar($event)">
        <input
          #entrada
          [value]="borrador()"
          (input)="borrador.set(entrada.value)"
          [placeholder]="t('duende.placeholder')"
          [attr.aria-label]="t('duende.placeholder')"
          maxlength="2000"
          autocomplete="off"
        />
        <button class="btn primary" type="submit" [disabled]="!borrador().trim() || duende.pensando()">
          {{ t('duende.enviar') }}
        </button>
      </form>
    </aside>
  `,
})
export class ChatDuende {
  protected readonly duende = inject(DuendeEstado);
  protected readonly t = inject(I18n).t;
  protected readonly borrador = signal('');
  private readonly lista = viewChild.required<ElementRef<HTMLElement>>('lista');
  private readonly entrada = viewChild.required<ElementRef<HTMLInputElement>>('entrada');

  constructor() {
    // Al abrir, el foco va a la caja de texto.
    effect(() => {
      if (this.duende.abierto()) setTimeout(() => this.entrada().nativeElement.focus(), 50);
    });
    // Con cada mensaje nuevo, baja hasta el final.
    afterRenderEffect(() => {
      this.duende.mensajes();
      this.duende.pensando();
      const el = this.lista().nativeElement;
      el.scrollTop = el.scrollHeight;
    });
  }

  protected enviar(evento: Event): void {
    evento.preventDefault();
    this.duende.enviar(this.borrador());
    this.borrador.set('');
  }
}
