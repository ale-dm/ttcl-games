import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Avatar } from '../../compartido/avatar';
import { Forma } from '../../compartido/forma';
import { Grafica } from '../../compartido/grafica';
import { MarcaDuende } from '../../compartido/marca-duende';
import { SelectorPeriodo } from '../../compartido/selector-periodo';
import { Api } from '../../core/api';
import { cargaReactiva } from '../../core/carga';
import { I18n, JUEGO_CORTO, NOMBRE_JUEGO } from '../../core/i18n';
import { KPIS, claveTexto, metrica, valorDe } from '../../core/metricas';
import { FilaDesglose, FilaMomento, Juego, PartidaVista, Periodo, periodoDe } from '../../core/modelos';
import { Clave } from '../../core/textos';
import { TituloTraducido } from '../../core/titulo';
import { DuendeEstado } from '../../duende/duende-estado';
import { PanelConsejos } from '../../duende/panel-consejos';
import { PanelDemos } from './panel-demos';
import { TablaPartidas } from './tabla-partidas';

type Pestana = 'resumen' | 'partidas' | 'desglose' | 'demos';
const POR_PAGINA = 15;
/** Con menos partidas, el desglose no marca mejor ni peor. */
const MIN_DESGLOSE = 3;

@Component({
  selector: 'app-jugador-pagina',
  imports: [
    NgTemplateOutlet,
    RouterLink,
    Avatar,
    Forma,
    Grafica,
    MarcaDuende,
    PanelConsejos,
    PanelDemos,
    SelectorPeriodo,
    TablaPartidas,
  ],
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

  /** /jugador/:slug?juego=cs2&tab=partidas&periodo=7d */
  readonly slug = input.required<string>();
  readonly juego = input<string | undefined>();
  readonly tab = input<string | undefined>();
  readonly periodo = input<string | undefined>();

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
    // Las demos solo son de CS2.
    if (t === 'demos') return this.juegoActivo() === 'cs2' ? 'demos' : 'resumen';
    return t === 'partidas' || t === 'desglose' ? t : 'resumen';
  });

  /** Todo lo de la página (cifras, consejos, partidas, con quién y cuándo) cuenta solo las partidas del periodo. */
  protected readonly periodoActivo = computed(() => periodoDe(this.periodo()));
  /** El periodo para los enlaces a otras páginas (sin parámetro si son todas las partidas). */
  protected readonly periodoUrl = computed(() => (this.periodoActivo() === 'todo' ? null : this.periodoActivo()));
  /** Última partida del juego, con todas las partidas: no depende del periodo elegido. */
  protected readonly ultimaPartida = computed(
    () => this.perfil.estado().datos?.resumenes.find((r) => r.juego === this.juegoActivo())?.ultimaPartida ?? null,
  );

  private readonly clave = computed(() => {
    const juego = this.juegoActivo();
    return juego ? { slug: this.slug(), juego, periodo: this.periodoActivo() } : null;
  });
  protected readonly detalle = cargaReactiva(this.clave, (p) => this.api.detalle(p.slug, p.juego, p.periodo));
  protected readonly consejos = cargaReactiva(
    () => {
      const c = this.clave();
      return c ? { ...c, lang: this.i18n.idioma() } : null;
    },
    (p) => this.api.consejos(p.slug, p.juego, p.lang, p.periodo),
  );
  /** Con el idioma: los comentarios del Duende de cada partida vienen traducidos. */
  protected readonly paginaPartidas = cargaReactiva(
    () => {
      const c = this.clave();
      return c ? { ...c, lang: this.i18n.idioma() } : null;
    },
    (p) => this.api.partidas(p.slug, p.juego, POR_PAGINA, 0, p.periodo, p.lang),
  );
  protected readonly sinergias = cargaReactiva(this.clave, (p) => this.api.sinergias(p.slug, p.juego, p.periodo));
  /** Filas de "Con quién": cada compañero (el que más partidas juntos primero) y, al final, solo. */
  protected readonly conQuien = computed(() => {
    const s = this.sinergias.estado().datos;
    return s ? [...s.companeros, ...(s.solo ? [s.solo] : [])] : [];
  });
  protected readonly sesiones = cargaReactiva(this.clave, (p) => this.api.sesiones(p.slug, p.juego, p.periodo));
  /** Lo que dicen sus demos (P12), solo en CS2: en el resumen, lo breve; en su pestaña, todo. */
  protected readonly demos = cargaReactiva(
    () => {
      const c = this.clave();
      return c && c.juego === 'cs2' ? c : null;
    },
    (p) => this.api.demos(p.slug, p.periodo),
  );
  /** Donde más muere sin trade, de todos los mapas: la zona con más parte de sus muertes en ese mapa. */
  protected readonly peorZona = computed(() => {
    const mapas = this.demos.estado().datos?.mapas ?? [];
    let mejor: { zona: string; mapa: string; parte: number } | null = null;
    for (const m of mapas) {
      for (const z of m.zonas) {
        const parte = m.muertes ? z.sinTrade / m.muertes : 0;
        if (z.sinTrade && (!mejor || parte > mejor.parte)) mejor = { zona: z.zona, mapa: m.mapa, parte };
      }
    }
    return mejor;
  });
  /** Bloques de "Cuándo juegas mejor": orden en la sesión, según la anterior y hora del día (los que tengan filas). */
  protected readonly bloquesSesion = computed(() => {
    const s = this.sesiones.estado().datos;
    if (!s) return [];
    const bloques: { titulo: Clave; filas: FilaMomento[] }[] = [
      { titulo: 'jugador.enLaSesion', filas: s.porOrden },
      { titulo: 'jugador.trasLaAnterior', filas: s.trasResultado },
      { titulo: 'jugador.horaDelDia', filas: s.porFranja },
    ];
    return bloques.filter((b) => b.filas.length);
  });
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
        this.duende.fijarContexto({
          foco: [{ slug: p.slug, nombre: p.nombre }],
          juego: this.juegoActivo(),
          periodo: this.periodoActivo(),
        });
      }
    });
    // Página nueva de partidas: lo cargado con "Cargar más" ya no vale.
    effect(() => {
      this.clave();
      this.masPartidas.set([]);
    });
  }

  protected irA(cambios: { juego?: Juego; tab?: Pestana; periodo?: Periodo }): void {
    const params: Record<string, string | null> = {};
    if (cambios.juego) params['juego'] = cambios.juego;
    if (cambios.tab) params['tab'] = cambios.tab === 'resumen' ? null : cambios.tab;
    if (cambios.periodo) params['periodo'] = cambios.periodo === 'todo' ? null : cambios.periodo;
    this.router.navigate([], { queryParams: params, queryParamsHandling: 'merge', replaceUrl: true });
  }

  protected cargarMas(): void {
    const c = this.clave();
    if (!c || this.cargandoMas()) return;
    this.cargandoMas.set(true);
    this.api.partidas(c.slug, c.juego, POR_PAGINA, this.partidas().length, c.periodo, this.i18n.idioma()).subscribe({
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
