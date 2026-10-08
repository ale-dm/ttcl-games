import { Component, computed, effect, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Avatar } from '../../compartido/avatar';
import { Forma } from '../../compartido/forma';
import { MarcaDuende } from '../../compartido/marca-duende';
import { Api } from '../../core/api';
import { cargaReactiva } from '../../core/carga';
import { I18n, JUEGO_CORTO, NOMBRE_JUEGO } from '../../core/i18n';
import { claveTexto, metrica } from '../../core/metricas';
import { JUEGOS, Juego } from '../../core/modelos';
import { DuendeEstado } from '../../duende/duende-estado';

const MIN_PARTIDAS = 5;

@Component({
  selector: 'app-comparar-pagina',
  imports: [RouterLink, Avatar, Forma, MarcaDuende],
  templateUrl: './comparar-pagina.html',
})
export class CompararPagina {
  protected readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  protected readonly duende = inject(DuendeEstado);
  protected readonly juegos = JUEGOS;
  protected readonly juegoCorto = JUEGO_CORTO;
  protected readonly claveTexto = claveTexto;

  /** /comparar?a=j1&b=j2&juego=cs2 */
  readonly a = input<string | undefined>();
  readonly b = input<string | undefined>();
  readonly juego = input<string | undefined>();

  protected readonly lista = cargaReactiva(
    () => true,
    () => this.api.buscar(''),
  );
  protected readonly jugadores = computed(() => this.lista.estado().datos ?? []);

  protected readonly slugA = computed(() => {
    const todos = this.jugadores();
    return todos.find((j) => j.slug === this.a())?.slug ?? todos[0]?.slug ?? null;
  });
  protected readonly slugB = computed(() => {
    const todos = this.jugadores();
    const a = this.slugA();
    const pedido = todos.find((j) => j.slug === this.b() && j.slug !== a);
    return pedido?.slug ?? todos.find((j) => j.slug !== a)?.slug ?? null;
  });
  protected readonly juegoSel = computed<Juego>(() => {
    const j = this.juego();
    if (j === 'cs2' || j === 'smite2') return j;
    // Sin juego en la URL: el primero que tengan los dos.
    const cuentas = (slug: string | null) => this.jugadores().find((p) => p.slug === slug)?.cuentas.map((c) => c.juego) ?? [];
    const enComun = cuentas(this.slugA()).filter((g) => cuentas(this.slugB()).includes(g));
    return enComun[0] ?? 'cs2';
  });

  protected readonly comparacion = cargaReactiva(
    () => {
      const a = this.slugA();
      const b = this.slugB();
      return a && b && a !== b ? { a, b, juego: this.juegoSel() } : null;
    },
    (p) => this.api.comparar(p.a, p.b, p.juego),
  );

  /** Filas con el ancho de cada barra (la mayor de las dos llena el 100 %). */
  protected readonly filas = computed(() =>
    (this.comparacion.estado().datos?.filas ?? []).map((f) => {
      const max = Math.max(Math.abs(f.a ?? 0), Math.abs(f.b ?? 0), 1e-9);
      return {
        ...f,
        formato: metrica(f.metrica).formato,
        anchoA: Math.round((Math.abs(f.a ?? 0) / max) * 100),
        anchoB: Math.round((Math.abs(f.b ?? 0) / max) * 100),
      };
    }),
  );
  protected readonly marcador = computed(() => {
    const filas = this.comparacion.estado().datos?.filas ?? [];
    return {
      a: filas.filter((f) => f.ventaja === 'a').length,
      b: filas.filter((f) => f.ventaja === 'b').length,
    };
  });
  protected readonly pocas = computed(() => {
    const d = this.comparacion.estado().datos;
    return !!d?.resumenA && !!d?.resumenB && Math.min(d.resumenA.partidas, d.resumenB.partidas) < MIN_PARTIDAS;
  });
  protected readonly veredicto = computed(() => {
    const d = this.comparacion.estado().datos;
    const { a, b } = this.marcador();
    if (!d || a + b === 0) return null;
    if (a === b) return this.t('comparar.empate', { n: a });
    return this.t('comparar.veredicto', { lider: a > b ? d.a.nombre : d.b.nombre, n: Math.max(a, b), total: a + b });
  });

  constructor() {
    effect(() => {
      const d = this.comparacion.estado().datos;
      if (d) this.duende.fijarContexto({ foco: [d.a, d.b], juego: d.juego });
    });
  }

  protected nombreJuego(j: Juego): string {
    return NOMBRE_JUEGO[j];
  }

  protected cambiar(cambios: { a?: string; b?: string; juego?: Juego }): void {
    this.router.navigate([], {
      queryParams: { a: this.slugA(), b: this.slugB(), juego: this.juegoSel(), ...cambios },
      replaceUrl: true,
    });
  }

  protected intercambiar(): void {
    this.cambiar({ a: this.slugB() ?? undefined, b: this.slugA() ?? undefined });
  }
}
