import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Avatar } from '../../compartido/avatar';
import { Forma } from '../../compartido/forma';
import { Grafica } from '../../compartido/grafica';
import { MarcaDuende } from '../../compartido/marca-duende';
import { Api } from '../../core/api';
import { cargaReactiva } from '../../core/carga';
import { I18n, JUEGO_CORTO, NOMBRE_JUEGO } from '../../core/i18n';
import { KPIS, claveTexto, metrica, valorDe } from '../../core/metricas';
import { FilaDesglose, Juego, PartidaVista } from '../../core/modelos';
import { TituloTraducido } from '../../core/titulo';
import { DuendeEstado } from '../../duende/duende-estado';
import { PanelConsejos } from '../../duende/panel-consejos';
import { TablaPartidas } from './tabla-partidas';

type Pestana = 'resumen' | 'partidas' | 'desglose';
const POR_PAGINA = 15;
/** Con menos partidas, el desglose no marca mejor ni peor. */
const MIN_DESGLOSE = 3;

@Component({
  selector: 'app-jugador-pagina',
  imports: [NgTemplateOutlet, RouterLink, Avatar, Forma, Grafica, MarcaDuende, PanelConsejos, TablaPartidas],
  templateUrl: './jugador-pagina.html',
})
export class JugadorPagina {
  protected readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly titulo = inject(TituloTraducido);
  protected readonly duende = inject(DuendeEstado);
  protected readonly nombreJuego = NOMBRE_JUEGO;
  protected readonly juegoCorto = JUEGO_CORTO;
  protected readonly claveTexto = claveTexto;

  /** /jugador/:slug?juego=cs2&tab=partidas */
  readonly slug = input.required<string>();
  readonly juego = input<string | undefined>();
  readonly tab = input<string | undefined>();

  protected readonly perfil = cargaReactiva(
    () => this.slug(),
    (slug) => this.api.jugador(slug),
  );

  protected readonly juegos = computed<Juego[]>(() => this.perfil.estado().datos?.resumenes.map((r) => r.juego) ?? []);
  protected readonly juegoActivo = computed<Juego | null>(() => {
    const disponibles = this.juegos();
    const pedido = this.juego();
    return disponibles.find((j) => j === pedido) ?? disponibles[0] ?? null;
  });
  protected readonly pestana = computed<Pestana>(() => {
    const t = this.tab();
    return t === 'partidas' || t === 'desglose' ? t : 'resumen';
  });

  private readonly clave = computed(() => {
    const juego = this.juegoActivo();
    return juego ? { slug: this.slug(), juego } : null;
  });
  protected readonly detalle = cargaReactiva(this.clave, (p) => this.api.detalle(p.slug, p.juego));
  protected readonly consejos = cargaReactiva(
    () => {
      const c = this.clave();
      return c ? { ...c, lang: this.i18n.idioma() } : null;
    },
    (p) => this.api.consejos(p.slug, p.juego, p.lang),
  );
  protected readonly paginaPartidas = cargaReactiva(this.clave, (p) =>
    this.api.partidas(p.slug, p.juego, POR_PAGINA, 0),
  );
  private readonly masPartidas = signal<PartidaVista[]>([]);
  protected readonly cargandoMas = signal(false);
  protected readonly partidas = computed(() => [
    ...(this.paginaPartidas.estado().datos?.items ?? []),
    ...this.masPartidas(),
  ]);
  protected readonly totalPartidas = computed(() => this.paginaPartidas.estado().datos?.total ?? 0);

  protected readonly asistencias = signal(false);

  /** Cifras de arriba, con la diferencia entre las últimas partidas y el total. */
  protected readonly kpis = computed(() => {
    const d = this.detalle.estado().datos;
    if (!d) return [];
    const conDelta = d.reciente.partidas >= 5 && d.resumen.partidas > d.reciente.partidas;
    return KPIS[d.juego].map((clave) => {
      const m = metrica(clave);
      const valor = valorDe(d.resumen, clave);
      const reciente = valorDe(d.reciente, clave);
      // La diferencia se redondea como se enseña: un "+0" no es ni mejora ni empeora.
      const decimales = m.formato === 'int' ? 0 : m.formato === 'pct' ? 1 : 2;
      const diff =
        conDelta && valor !== null && reciente !== null ? Number((reciente - valor).toFixed(decimales)) : null;
      const mejora = !diff ? 0 : (diff > 0) === (m.mejor !== 'bajo') ? 1 : -1;
      return { clave, formato: m.formato, valor, diff, mejora, n: d.reciente.partidas };
    });
  });

  /**
   * Desglose con el mejor y el peor marcados, entre los que tienen partidas suficientes. Si hay empate en lo más alto
   * (o en lo más bajo), no se marca a nadie: no hay un mejor de verdad.
   */
  protected readonly desglose = computed(() => {
    const filas = this.detalle.estado().datos?.desglose ?? [];
    const winrates = filas.filter((f) => f.partidas >= MIN_DESGLOSE && f.winrate !== null).map((f) => f.winrate!);
    const unico = (valor: number) => winrates.filter((w) => w === valor).length === 1;
    const max = Math.max(...winrates);
    const min = Math.min(...winrates);
    const marca = (f: FilaDesglose, valor: number) =>
      winrates.length > 1 && max !== min && f.partidas >= MIN_DESGLOSE && f.winrate === valor && unico(valor);
    return filas.map((f) => ({ ...f, mejor: marca(f, max), peor: marca(f, min) }));
  });

  constructor() {
    effect(() => {
      const p = this.perfil.estado().datos;
      if (p) {
        this.titulo.fijar(p.nombre);
        this.duende.fijarContexto({ foco: [{ slug: p.slug, nombre: p.nombre }], juego: this.juegoActivo() });
      }
    });
    // Página nueva de partidas: lo cargado con "Cargar más" ya no vale.
    effect(() => {
      this.clave();
      this.masPartidas.set([]);
    });
  }

  protected irA(cambios: { juego?: Juego; tab?: Pestana }): void {
    const params: Record<string, string | null> = {};
    if (cambios.juego) params['juego'] = cambios.juego;
    if (cambios.tab) params['tab'] = cambios.tab === 'resumen' ? null : cambios.tab;
    this.router.navigate([], { queryParams: params, queryParamsHandling: 'merge', replaceUrl: true });
  }

  protected cargarMas(): void {
    const c = this.clave();
    if (!c || this.cargandoMas()) return;
    this.cargandoMas.set(true);
    this.api.partidas(c.slug, c.juego, POR_PAGINA, this.partidas().length).subscribe({
      next: (r) => {
        this.masPartidas.update((m) => [...m, ...r.items]);
        this.cargandoMas.set(false);
      },
      error: () => this.cargandoMas.set(false),
    });
  }

  protected porcentaje(parte: number, total: number): number {
    return total ? Math.round((parte / total) * 100) : 0;
  }
}
