import { NgTemplateOutlet } from '@angular/common';
import { Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { Avatar } from '../compartido/avatar';
import { MarcaDuende } from '../compartido/marca-duende';
import { Api } from '../core/api';
import { I18n, JUEGO_CORTO } from '../core/i18n';
import { BusquedaVista } from '../core/modelos';
import { TemaService } from '../core/tema';
import { DuendeEstado } from '../duende/duende-estado';

/** Barra superior: marca, secciones, buscador de jugadores, Duende, idioma y tema. */
@Component({
  selector: 'app-cabecera',
  imports: [NgTemplateOutlet, RouterLink, RouterLinkActive, Avatar, MarcaDuende],
  host: { '(document:click)': 'clicFuera($event)' },
  template: `
    <header class="nav">
      <div class="wrap nav-in">
        <a class="brand" routerLink="/" [attr.aria-label]="t('nav.inicio')">
          <span class="brand-mark">TT</span>
          <span class="hide-xs">TTCL <span class="muted">Games</span></span>
        </a>
        <nav class="nav-links">
          <ng-container *ngTemplateOutlet="secciones" />
        </nav>

        <div class="nav-r">
          <div class="buscar" #buscador role="search">
            <svg width="14" height="14" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <circle cx="7" cy="7" r="5" stroke="currentColor" stroke-width="1.6" />
              <path d="m11 11 3.5 3.5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
            </svg>
            <input
              #caja
              type="search"
              [value]="consulta()"
              (input)="consulta.set(caja.value); abierto.set(true)"
              (focus)="abierto.set(true)"
              (keydown)="teclado($event)"
              [placeholder]="t('nav.buscar')"
              [attr.aria-label]="t('nav.buscar')"
              autocomplete="off"
            />
            @if (abierto() && consulta().trim()) {
              <div class="buscar-res" role="listbox">
                @for (r of resultados(); track r.slug; let i = $index) {
                  <button type="button" role="option" [class.on]="i === activo()" (click)="ir(r)">
                    <app-avatar [slug]="r.slug" [nombre]="r.nombre" [tam]="28" />
                    <span>
                      <b>{{ r.nombre }}</b>
                      <span class="small muted"> · {{ nicks(r) }}</span>
                    </span>
                  </button>
                } @empty {
                  <div class="vacio">{{ t('nav.sinResultados') }}</div>
                }
              </div>
            }
          </div>

          <button class="btn ghost duende-btn" type="button" (click)="duende.abrir()">
            <app-marca-duende [tam]="24" />
            <span class="txt-duende">{{ t('nav.duende') }}</span>
          </button>
          <div class="seg sm" role="group" [attr.aria-label]="t('nav.idioma')">
            <button type="button" [class.on]="i18n.idioma() === 'es'" (click)="i18n.cambiar('es')" lang="es">ES</button>
            <button type="button" [class.on]="i18n.idioma() === 'en'" (click)="i18n.cambiar('en')" lang="en">EN</button>
          </div>
          <button
            class="icon-btn"
            type="button"
            (click)="tema.alternar()"
            [attr.aria-label]="tema.tema() === 'dark' ? t('nav.temaClaro') : t('nav.temaOscuro')"
            [attr.title]="tema.tema() === 'dark' ? t('nav.temaClaro') : t('nav.temaOscuro')"
          >
            <span class="theme-glyph" [class.light]="tema.tema() === 'light'"></span>
          </button>
        </div>
      </div>
      <nav class="wrap nav-movil">
        <ng-container *ngTemplateOutlet="secciones" />
      </nav>
    </header>

    <ng-template #secciones>
      <a class="nav-link" routerLink="/" routerLinkActive="on" [routerLinkActiveOptions]="{ exact: true }">{{ t('nav.equipo') }}</a>
      <a class="nav-link" routerLink="/comparar" routerLinkActive="on">{{ t('nav.comparar') }}</a>
      <a class="nav-link" routerLink="/ranking" routerLinkActive="on">{{ t('nav.ranking') }}</a>
    </ng-template>
  `,
})
export class Cabecera {
  protected readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  protected readonly tema = inject(TemaService);
  protected readonly duende = inject(DuendeEstado);
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly buscador = viewChild.required<ElementRef<HTMLElement>>('buscador');

  protected readonly consulta = signal('');
  protected readonly abierto = signal(false);
  protected readonly resultados = signal<BusquedaVista[]>([]);
  protected readonly activo = signal(0);

  constructor() {
    toObservable(this.consulta)
      .pipe(
        debounceTime(150),
        distinctUntilChanged(),
        switchMap((q) => (q.trim() ? this.api.buscar(q.trim()).pipe(catchError(() => of([]))) : of([]))),
        takeUntilDestroyed(),
      )
      .subscribe((r) => {
        this.resultados.set(r);
        this.activo.set(0);
      });
  }

  protected nicks(r: BusquedaVista): string {
    return r.cuentas.map((c) => `${JUEGO_CORTO[c.juego]} ${c.nick}`).join(' · ');
  }

  protected teclado(e: KeyboardEvent): void {
    const n = this.resultados().length;
    if (e.key === 'ArrowDown' && n) {
      e.preventDefault();
      this.activo.set((this.activo() + 1) % n);
    } else if (e.key === 'ArrowUp' && n) {
      e.preventDefault();
      this.activo.set((this.activo() - 1 + n) % n);
    } else if (e.key === 'Enter' && n) {
      e.preventDefault();
      this.ir(this.resultados()[this.activo()]);
    } else if (e.key === 'Escape') {
      this.abierto.set(false);
    }
  }

  protected ir(r: BusquedaVista): void {
    this.abierto.set(false);
    this.consulta.set('');
    this.router.navigate(['/jugador', r.slug]);
  }

  protected clicFuera(e: MouseEvent): void {
    if (!this.buscador().nativeElement.contains(e.target as Node)) this.abierto.set(false);
  }
}
