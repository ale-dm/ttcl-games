import { DOCUMENT } from '@angular/common';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { FormatoValor, Juego } from './modelos';
import { guardarPreferencias } from './preferencias';
import { Clave, EN, ES } from './textos';

export type Idioma = 'es' | 'en';

export const NOMBRE_JUEGO: Record<Juego, string> = { cs2: 'Counter-Strike 2', smite2: 'SMITE 2' };
export const JUEGO_CORTO: Record<Juego, string> = { cs2: 'CS2', smite2: 'SMITE 2' };

/**
 * Idioma de la interfaz y formato de números y fechas. Todo lee la señal `idioma`, así que cualquier plantilla que
 * llame a t(), num(), pct()... se repinta sola al cambiar de idioma.
 */
@Injectable({ providedIn: 'root' })
export class I18n {
  private readonly doc = inject(DOCUMENT);
  readonly idioma = signal<Idioma>(this.doc.documentElement.lang === 'en' ? 'en' : 'es');
  private readonly locale = computed(() => (this.idioma() === 'es' ? 'es-ES' : 'en-GB'));

  constructor() {
    effect(() => {
      this.doc.documentElement.lang = this.idioma();
    });
  }

  cambiar(idioma: Idioma): void {
    this.idioma.set(idioma);
    guardarPreferencias({ lang: idioma });
  }

  /** Texto traducido. Los {marcadores} se sustituyen por los parámetros. */
  readonly t = (clave: Clave, params?: Record<string, string | number>): string => {
    const texto: string = (this.idioma() === 'es' ? ES : EN)[clave] ?? clave;
    if (!params) return texto;
    return texto.replace(/\{(\w+)\}/g, (_, k: string) => String(params[k] ?? `{${k}}`));
  };

  // ─── Números ──────────────────────────────────────────────────────────────

  /** Número con 1-2 decimales (1,24 · 0,9 · 18,4). */
  readonly num = (v: number | null | undefined, maxDecimales = 2, minDecimales = 0): string => {
    if (v === null || v === undefined || !Number.isFinite(v)) return '—';
    return new Intl.NumberFormat(this.locale(), {
      minimumFractionDigits: minDecimales,
      maximumFractionDigits: maxDecimales,
    }).format(v);
  };

  /** Porcentaje a partir de 0-100: 37,1 % (es) · 37.1% (en). */
  readonly pct = (v: number | null | undefined, decimales = 1): string => {
    if (v === null || v === undefined || !Number.isFinite(v)) return '—';
    return new Intl.NumberFormat(this.locale(), {
      style: 'percent',
      minimumFractionDigits: decimales,
      maximumFractionDigits: decimales,
    }).format(v / 100);
  };

  readonly entero = (v: number | null | undefined): string => this.num(v, 0);

  /** Valor según el formato de su métrica. */
  readonly valor = (v: number | null | undefined, formato: FormatoValor | null | undefined): string => {
    if (formato === 'pct') return this.pct(v);
    if (formato === 'int') return this.entero(v);
    return this.num(v, 2, 2);
  };

  /** Diferencia con signo: +0,12 · −3,4 pp (puntos porcentuales en los porcentajes). */
  readonly delta = (v: number, formato: FormatoValor): string => {
    const signo = v > 0 ? '+' : v < 0 ? '−' : '±';
    const abs = Math.abs(v);
    return signo + (formato === 'pct' ? `${this.num(abs, 1, 1)} pp` : this.valor(abs, formato));
  };

  // ─── Fechas ───────────────────────────────────────────────────────────────

  readonly fecha = (iso: string | null | undefined): string => {
    if (!iso) return '—';
    return new Intl.DateTimeFormat(this.locale(), { day: '2-digit', month: '2-digit', year: 'numeric' }).format(
      new Date(iso),
    );
  };

  readonly fechaCorta = (iso: string | null | undefined): string => {
    if (!iso) return '—';
    return new Intl.DateTimeFormat(this.locale(), { day: 'numeric', month: 'short' }).format(new Date(iso));
  };

  readonly fechaHora = (iso: string | null | undefined): string => {
    if (!iso) return '—';
    return new Intl.DateTimeFormat(this.locale(), {
      day: 'numeric',
      month: 'short',
      hour: '2-digit',
      minute: '2-digit',
    }).format(new Date(iso));
  };

  /** "hace 12 min" · "12 min ago". */
  readonly relativo = (iso: string | null | undefined, ahora = Date.now()): string => {
    if (!iso) return '—';
    const seg = Math.round((new Date(iso).getTime() - ahora) / 1000);
    const rtf = new Intl.RelativeTimeFormat(this.locale(), { numeric: 'auto', style: 'short' });
    const abs = Math.abs(seg);
    if (abs < 60) return rtf.format(seg, 'second');
    if (abs < 3600) return rtf.format(Math.round(seg / 60), 'minute');
    if (abs < 86400) return rtf.format(Math.round(seg / 3600), 'hour');
    if (abs < 86400 * 30) return rtf.format(Math.round(seg / 86400), 'day');
    return this.fecha(iso);
  };

  /** V/D en español, W/L en inglés. */
  readonly letraForma = (c: string): string => (c === 'V' ? this.t('comun.formaV') : c === 'D' ? this.t('comun.formaD') : '?');
}
