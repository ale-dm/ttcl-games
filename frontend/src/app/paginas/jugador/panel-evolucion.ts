import { Component, HostListener, OnDestroy, computed, inject, input, signal } from '@angular/core';
import { Api } from '../../core/api';
import { cargaReactiva } from '../../core/carga';
import { I18n } from '../../core/i18n';
import { CampoCaptura, CapturaVista, FormatoValor, MapaLeido, TramoCapturas } from '../../core/modelos';
import { Clave } from '../../core/textos';

const TIPOS = ['image/png', 'image/jpeg', 'image/webp'];
const MAX_BYTES = 8 * 1024 * 1024;
const GRUPOS = ['general', 'totales', 'clutch', 'entradas', 'rango'] as const;
/** La gráfica: ancho, alto y márgenes del SVG. */
const ANCHO = 600;
const ALTO = 180;
const M = { izq: 44, der: 14, arr: 14, aba: 26 };

/** Una captura nueva, mientras se lee y se revisa. Los números van como texto: es lo que hay en cada casilla. */
interface Nueva {
  imagen: Blob;
  vista: string;
  leyendo: boolean;
  origen: 'gemini' | 'ninguno' | null;
  motivo: string | null;
  valores: Record<string, string>;
  mapas: { mapa: string; partidas: string; winrate: string }[];
  guardando: boolean;
  error: Clave | null;
}

/** Una cifra del tramo: su texto y su valor con formato. */
interface CifraTramo {
  texto: string;
  valor: string;
}

/**
 * Capturas de csstats de un jugador (P13): subir una (el Duende la lee con Gemini y se revisa antes de guardar), la
 * evolución de cada número en una gráfica, dos capturas comparadas (con lo que jugó entre medias) y la lista.
 */
@Component({
  selector: 'app-panel-evolucion',
  template: `
    <div class="evo">
      <section class="card evo-subir">
        <div class="card-h">
          <div>
            <div class="card-t">{{ t('capt.subirT') }}</div>
            <div class="small muted">{{ t('capt.subirSub') }}</div>
          </div>
        </div>
        <div class="card-b">
          @if (nueva(); as n) {
            <div class="evo-nueva">
              <img class="evo-vista" [src]="n.vista" alt="" />
              <div class="evo-form">
                @if (n.leyendo) {
                  <p class="muted evo-estado">{{ t('capt.leyendo') }}</p>
                } @else {
                  <p class="small evo-estado" [class.ok]="n.origen === 'gemini'">{{ t(estadoLectura(n)) }}</p>
                  @for (g of grupos(); track g.grupo) {
                    <fieldset class="evo-grupo">
                      <legend class="eyebrow">{{ t(g.texto) }}</legend>
                      <div class="evo-campos">
                        @for (c of g.campos; track c.clave) {
                          <label class="evo-campo">
                            <span>{{ etiqueta(c.clave) }}</span>
                            <input
                              type="number"
                              min="0"
                              step="any"
                              inputmode="decimal"
                              [attr.data-clave]="c.clave"
                              [value]="n.valores[c.clave] || ''"
                              (input)="cambiarValor(c.clave, $event)"
                            />
                          </label>
                        }
                      </div>
                    </fieldset>
                  }
                  <fieldset class="evo-grupo">
                    <legend class="eyebrow">{{ t('capt.mapas') }}</legend>
                    @for (m of n.mapas; track $index; let i = $index) {
                      <div class="evo-mapa">
                        <input type="text" [attr.aria-label]="t('capt.mapa')" [value]="m.mapa" (input)="cambiarMapa(i, 'mapa', $event)" />
                        <input
                          type="number"
                          min="0"
                          [attr.aria-label]="t('capt.partidasMapa')"
                          [placeholder]="t('capt.partidasMapa')"
                          [value]="m.partidas"
                          (input)="cambiarMapa(i, 'partidas', $event)"
                        />
                        <input
                          type="number"
                          min="0"
                          max="100"
                          step="any"
                          [attr.aria-label]="t('capt.winrateMapa')"
                          [placeholder]="t('capt.winrateMapa')"
                          [value]="m.winrate"
                          (input)="cambiarMapa(i, 'winrate', $event)"
                        />
                        <button class="btn ghost sm" type="button" (click)="quitarMapa(i)">{{ t('capt.quitar') }}</button>
                      </div>
                    }
                    <button class="btn ghost sm" type="button" (click)="anadirMapa()">{{ t('capt.anadirMapa') }}</button>
                  </fieldset>
                  @if (n.error) {
                    <p class="small evo-error" role="alert">{{ t(n.error) }}</p>
                  }
                  <div class="evo-botones">
                    <button class="btn primary" type="button" [disabled]="n.guardando || !hayNumeros()" (click)="guardar()">
                      {{ t('capt.guardar') }}
                    </button>
                    <button class="btn" type="button" (click)="cancelar()">{{ t('capt.cancelar') }}</button>
                  </div>
                }
              </div>
            </div>
          } @else {
            <div class="evo-elegir">
              <label class="btn primary">
                {{ t('capt.elegir') }}
                <input class="sr-only" type="file" accept="image/png,image/jpeg,image/webp" (change)="elegir($event)" />
              </label>
              <span class="small muted">{{ t('capt.pegar') }}</span>
            </div>
            @if (errorImagen()) {
              <p class="small evo-error" role="alert">{{ t('capt.errorImagen') }}</p>
            }
          }
        </div>
      </section>

      @let lista = capturas.estado().datos ?? [];
      @if (lista.length >= 2) {
        <section class="card">
          <div class="card-h">
            <div>
              <div class="card-t">{{ t('capt.graficaT') }}</div>
              <div class="small muted">{{ t('capt.graficaSub') }}</div>
            </div>
            <label class="sr-only" for="evo-metrica">{{ t('capt.metrica') }}</label>
            <select id="evo-metrica" [value]="metrica()" (change)="metrica.set($any($event.target).value)">
              @for (c of camposGrafica(); track c.clave) {
                <option [value]="c.clave" [selected]="c.clave === metrica()">{{ etiqueta(c.clave) }}</option>
              }
            </select>
          </div>
          <div class="card-b">
            @let g = grafica();
            <svg class="evo-grafica" [attr.viewBox]="'0 0 ' + ancho + ' ' + alto" role="img" [attr.aria-label]="etiqueta(metrica())">
              @for (y of g.ejeY; track $index) {
                <line class="gridl" [attr.x1]="m.izq" [attr.x2]="ancho - m.der" [attr.y1]="y.y" [attr.y2]="y.y" />
                <text class="axis" [attr.x]="m.izq - 6" [attr.y]="y.y + 3.5" text-anchor="end">{{ y.texto }}</text>
              }
              <path class="ln evo-ln" [attr.d]="g.camino" />
              @for (p of g.puntos; track p.id) {
                <circle class="evo-pt" [attr.cx]="p.x" [attr.cy]="p.y" r="4">
                  <title>{{ p.titulo }}</title>
                </circle>
                <text class="axis" [attr.x]="p.x" [attr.y]="alto - 6" [attr.text-anchor]="p.ancla">{{ p.fecha }}</text>
              }
            </svg>
          </div>
        </section>

        <section class="card evo-comparar">
          <div class="card-h">
            <div>
              <div class="card-t">{{ t('capt.compararT') }}</div>
              @if (comparacion.estado().datos; as e) {
                <div class="small muted">{{ t('capt.dias', { dias: e.dias }) }}</div>
              }
            </div>
            <div class="evo-selects">
              <label>
                <span class="small muted">{{ t('capt.desde') }}</span>
                <select id="evo-desde" (change)="desde.set(+$any($event.target).value)">
                  @for (c of lista; track c.id) {
                    <option [value]="c.id" [selected]="c.id === desdeActivo()">{{ i18n.fechaHora(c.cargadaEn) }}</option>
                  }
                </select>
              </label>
              <label>
                <span class="small muted">{{ t('capt.hasta') }}</span>
                <select id="evo-hasta" (change)="hasta.set(+$any($event.target).value)">
                  @for (c of lista; track c.id) {
                    <option [value]="c.id" [selected]="c.id === hastaActivo()">{{ i18n.fechaHora(c.cargadaEn) }}</option>
                  }
                </select>
              </label>
            </div>
          </div>
          <div class="card-b">
            @if (comparacion.estado().datos; as e) {
              <div class="eyebrow">{{ t('capt.tramoT') }}</div>
              @if (e.tramo; as tr) {
                <div class="evo-tramo">
                  <div class="stat">
                    <div class="v num">{{ tr.partidas }}</div>
                    <div class="l">
                      {{ t('capt.campo.partidas') }}
                      @if (tr.victorias !== null && tr.derrotas !== null) {
                        · {{ t('capt.tramoResultado', { v: tr.victorias, d: tr.derrotas }) }}
                      }
                    </div>
                  </div>
                  @for (c of cifrasTramo(tr); track c.texto) {
                    <div class="stat">
                      <div class="v num">{{ c.valor }}</div>
                      <div class="l">{{ c.texto }}</div>
                    </div>
                  }
                </div>
              } @else {
                <p class="muted">{{ t('capt.sinTramo') }}</p>
              }
              <div class="tbl-wrap">
                <table class="tbl evo-tabla">
                  <thead>
                    <tr>
                      <th>{{ t('capt.metricaCol') }}</th>
                      <th class="r">{{ t('capt.antes') }}</th>
                      <th class="r">{{ t('capt.despues') }}</th>
                      <th class="r">{{ t('capt.diferencia') }}</th>
                    </tr>
                  </thead>
                  <tbody>
                    @for (f of e.filas; track f.clave) {
                      <tr>
                        <td>{{ etiqueta(f.clave) }}</td>
                        <td class="r num">{{ i18n.valor(f.antes, f.formato) }}</td>
                        <td class="r num">{{ i18n.valor(f.despues, f.formato) }}</td>
                        <td class="r num" [class.sube]="f.mejora > 0" [class.baja]="f.mejora < 0">
                          {{ f.diferencia === null ? '—' : i18n.delta(f.diferencia, f.formato) }}
                        </td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
            } @else {
              <div class="skel" style="height: 200px"></div>
            }
          </div>
        </section>
      }

      <section class="card">
        <div class="card-h">
          <div class="card-t">{{ t('capt.listaT') }}</div>
        </div>
        <div class="card-b">
          @if (!lista.length) {
            <p class="muted evo-vacio">{{ t('capt.vacio', { nombre: nombre() }) }}</p>
          } @else {
            @if (lista.length === 1) {
              <p class="small muted">{{ t('capt.una') }}</p>
            }
            <ul class="evo-lista">
              @for (c of listaReciente(); track c.id) {
                <li class="evo-item">
                  <div>
                    <b>{{ i18n.fechaHora(c.cargadaEn) }}</b>
                    <span class="small muted">· {{ t(c.leidaPor === 'gemini' ? 'capt.leidaPor.gemini' : 'capt.leidaPor.mano') }}</span>
                    <div class="small muted num">{{ resumen(c) }}</div>
                  </div>
                  <div class="evo-item-a">
                    @if (c.imagen) {
                      <a class="btn ghost sm" [href]="api.imagenCaptura(slug(), c.id)" target="_blank" rel="noopener">
                        {{ t('capt.verImagen') }}
                      </a>
                    }
                    <button class="btn ghost sm" type="button" (click)="borrar(c)">{{ t('capt.borrar') }}</button>
                  </div>
                </li>
              }
            </ul>
          }
        </div>
      </section>
    </div>
  `,
})
export class PanelEvolucion implements OnDestroy {
  protected readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  protected readonly api = inject(Api);
  protected readonly ancho = ANCHO;
  protected readonly alto = ALTO;
  protected readonly m = M;

  readonly slug = input.required<string>();
  readonly nombre = input.required<string>();

  protected readonly campos = cargaReactiva(
    () => true,
    () => this.api.camposCaptura(),
  );
  private readonly recarga = signal(0);
  protected readonly capturas = cargaReactiva(
    () => ({ slug: this.slug(), n: this.recarga() }),
    (p) => this.api.capturas(p.slug),
  );

  protected readonly nueva = signal<Nueva | null>(null);
  protected readonly errorImagen = signal(false);
  protected readonly metrica = signal('kd');
  protected readonly desde = signal<number | null>(null);
  protected readonly hasta = signal<number | null>(null);

  protected readonly listaReciente = computed(() => [...(this.capturas.estado().datos ?? [])].reverse());
  /** Por defecto, la primera con la última; si se ha borrado la elegida, también. */
  protected readonly desdeActivo = computed(() => this.elegida(this.desde(), 0));
  protected readonly hastaActivo = computed(() => this.elegida(this.hasta(), -1));
  protected readonly comparacion = cargaReactiva(
    () => {
      const lista = this.capturas.estado().datos ?? [];
      return lista.length >= 2
        ? { slug: this.slug(), desde: this.desdeActivo(), hasta: this.hastaActivo(), n: lista.length }
        : null;
    },
    (p) => this.api.compararCapturas(p.slug, p.desde, p.hasta),
  );

  /** Los campos por grupo, para el formulario. */
  protected readonly grupos = computed(() => {
    const campos = this.campos.estado().datos ?? [];
    return GRUPOS.map((grupo) => ({
      grupo,
      texto: `capt.grupo.${grupo}` as Clave,
      campos: campos.filter((c) => c.grupo === grupo),
    })).filter((g) => g.campos.length);
  });
  /** Lo que se puede dibujar: los números que haya en las capturas. */
  protected readonly camposGrafica = computed(() => {
    const lista = this.capturas.estado().datos ?? [];
    return (this.campos.estado().datos ?? []).filter((c) => lista.some((x) => x.datos[c.clave] !== undefined));
  });

  protected readonly grafica = computed(() => {
    const clave = this.metrica();
    const formato = this.formato(clave);
    const puntos = (this.capturas.estado().datos ?? []).filter((c) => c.datos[clave] !== undefined);
    const valores = puntos.map((c) => c.datos[clave]);
    let min = Math.min(...valores);
    let max = Math.max(...valores);
    if (min === max) {
      min -= Math.abs(min) * 0.1 || 1;
      max += Math.abs(max) * 0.1 || 1;
    }
    const x = (i: number) =>
      puntos.length === 1 ? (M.izq + ANCHO - M.der) / 2 : M.izq + (i * (ANCHO - M.izq - M.der)) / (puntos.length - 1);
    const y = (v: number) => M.arr + ((max - v) * (ALTO - M.arr - M.aba)) / (max - min);
    const pts = puntos.map((c, i) => ({
      id: c.id,
      x: x(i),
      y: y(c.datos[clave]),
      fecha: this.i18n.fechaCorta(c.cargadaEn),
      // La primera y la última fecha, pegadas a su borde: centradas, se saldrían de la gráfica.
      ancla: puntos.length > 1 && i === 0 ? 'start' : puntos.length > 1 && i === puntos.length - 1 ? 'end' : 'middle',
      titulo: `${this.i18n.fechaHora(c.cargadaEn)}: ${this.i18n.valor(c.datos[clave], formato)}`,
    }));
    return {
      puntos: pts,
      camino: pts.map((p, i) => `${i ? 'L' : 'M'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join(' '),
      ejeY: [max, (max + min) / 2, min].map((v) => ({ y: y(v), texto: this.i18n.valor(v, formato) })),
    };
  });

  protected readonly hayNumeros = computed(() =>
    Object.values(this.nueva()?.valores ?? {}).some((v) => v.trim() !== '' && Number.isFinite(Number(v))),
  );

  ngOnDestroy(): void {
    this.soltarVista();
  }

  /** Pegar una captura con Ctrl+V mientras se ve la pestaña. */
  @HostListener('window:paste', ['$event'])
  protected pegar(evento: ClipboardEvent): void {
    const archivo = [...(evento.clipboardData?.files ?? [])].find((f) => f.type.startsWith('image/'));
    if (archivo && !this.nueva()) {
      evento.preventDefault();
      this.empezar(archivo);
    }
  }

  protected elegir(evento: Event): void {
    const entrada = evento.target as HTMLInputElement;
    const archivo = entrada.files?.[0];
    entrada.value = '';
    if (archivo) this.empezar(archivo);
  }

  /** Enseña la imagen y se la pasa al Duende para que la lea; lo que lea rellena el formulario. */
  private empezar(archivo: Blob): void {
    if (!TIPOS.includes(archivo.type) || archivo.size > MAX_BYTES) {
      this.errorImagen.set(true);
      return;
    }
    this.errorImagen.set(false);
    this.nueva.set({
      imagen: archivo,
      vista: URL.createObjectURL(archivo),
      leyendo: true,
      origen: null,
      motivo: null,
      valores: {},
      mapas: [],
      guardando: false,
      error: null,
    });
    this.api.leerCaptura(this.slug(), archivo).subscribe({
      next: (l) =>
        this.cambiar({
          leyendo: false,
          origen: l.origen,
          motivo: l.motivo,
          valores: Object.fromEntries(Object.entries(l.datos).map(([k, v]) => [k, String(v)])),
          mapas: l.mapas.map((m) => ({
            mapa: m.mapa,
            partidas: m.partidas === null ? '' : String(m.partidas),
            winrate: m.winrate === null ? '' : String(m.winrate),
          })),
        }),
      error: (e: { status?: number }) => {
        if (e.status === 400 || e.status === 413) {
          this.cancelar();
          this.errorImagen.set(true);
        } else {
          this.cambiar({ leyendo: false, origen: 'ninguno', motivo: 'error' });
        }
      },
    });
  }

  protected cambiarValor(clave: string, evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    const n = this.nueva();
    if (n) this.cambiar({ valores: { ...n.valores, [clave]: valor }, error: null });
  }

  protected cambiarMapa(i: number, campo: 'mapa' | 'partidas' | 'winrate', evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    const n = this.nueva();
    if (n) this.cambiar({ mapas: n.mapas.map((m, j) => (j === i ? { ...m, [campo]: valor } : m)) });
  }

  protected anadirMapa(): void {
    const n = this.nueva();
    if (n) this.cambiar({ mapas: [...n.mapas, { mapa: '', partidas: '', winrate: '' }] });
  }

  protected quitarMapa(i: number): void {
    const n = this.nueva();
    if (n) this.cambiar({ mapas: n.mapas.filter((_, j) => j !== i) });
  }

  protected guardar(): void {
    const n = this.nueva();
    if (!n || n.guardando) return;
    const datos: Record<string, number> = {};
    for (const [clave, texto] of Object.entries(n.valores)) {
      const v = Number(texto);
      if (texto.trim() !== '' && Number.isFinite(v)) datos[clave] = v;
    }
    const numero = (texto: string) => (texto.trim() !== '' && Number.isFinite(Number(texto)) ? Number(texto) : null);
    const mapas: MapaLeido[] = n.mapas
      .filter((m) => m.mapa.trim())
      .map((m) => ({ mapa: m.mapa.trim(), partidas: numero(m.partidas), winrate: numero(m.winrate) }));
    this.cambiar({ guardando: true, error: null });
    this.api
      .guardarCaptura(this.slug(), { datos, mapas, leidaPor: n.origen === 'gemini' ? 'gemini' : 'mano' }, n.imagen)
      .subscribe({
        next: () => {
          this.cancelar();
          this.desde.set(null);
          this.hasta.set(null);
          this.recarga.update((x) => x + 1);
        },
        error: () => this.cambiar({ guardando: false, error: 'capt.errorGuardar' }),
      });
  }

  protected cancelar(): void {
    this.soltarVista();
    this.nueva.set(null);
  }

  protected borrar(c: CapturaVista): void {
    if (!confirm(this.t('capt.confirmarBorrar', { fecha: this.i18n.fechaHora(c.cargadaEn) }))) return;
    this.api.borrarCaptura(this.slug(), c.id).subscribe(() => this.recarga.update((x) => x + 1));
  }

  /** Qué decir de la lectura: leída por Gemini, o por qué hay que rellenarla a mano. */
  protected estadoLectura(n: Nueva): Clave {
    if (n.origen === 'gemini') return 'capt.leida';
    if (n.motivo === 'sin_clave') return 'capt.motivo.sin_clave';
    return n.motivo === 'cuota' ? 'capt.motivo.cuota' : 'capt.motivo.otro';
  }

  protected etiqueta(clave: string): string {
    const texto = `capt.campo.${clave}`;
    return this.t(texto as Clave) === texto ? clave : this.t(texto as Clave);
  }

  /** "Partidas 344 · K/D 0,60 · ADR 57 · Rating HLTV 0,74" para la lista. */
  protected resumen(c: CapturaVista): string {
    const partes: string[] = [];
    for (const clave of ['partidas', 'kd', 'adr', 'rating']) {
      const v = c.datos[clave];
      if (v !== undefined) partes.push(`${this.etiqueta(clave)} ${this.i18n.valor(v, this.formato(clave))}`);
    }
    return partes.join(' · ');
  }

  protected cifrasTramo(tr: TramoCapturas): CifraTramo[] {
    const cifras: [Clave, number | null, FormatoValor][] = [
      ['metrica.winrate', tr.winrate, 'pct'],
      ['metrica.kd', tr.kd, 'dec'],
      ['metrica.hs_pct', tr.hsPct, 'pct'],
      ['metrica.adr', tr.adr, 'int'],
      ['metrica.kills_media', tr.killsPartida, 'dec'],
      ['capt.clutch1v1', tr.clutch1v1, 'pct'],
    ];
    return cifras
      .filter(([, v]) => v !== null)
      .map(([texto, v, f]) => ({ texto: this.t(texto), valor: this.i18n.valor(v, f) }));
  }

  private formato(clave: string): FormatoValor {
    return (this.campos.estado().datos ?? []).find((c: CampoCaptura) => c.clave === clave)?.formato ?? 'dec';
  }

  /** El id elegido si sigue en la lista; si no, el de la posición por defecto (0 la primera, -1 la última). */
  private elegida(id: number | null, porDefecto: number): number | null {
    const lista = this.capturas.estado().datos ?? [];
    if (id !== null && lista.some((c) => c.id === id)) return id;
    return lista.at(porDefecto)?.id ?? null;
  }

  private cambiar(cambios: Partial<Nueva>): void {
    this.nueva.update((n) => (n ? { ...n, ...cambios } : n));
  }

  private soltarVista(): void {
    const n = this.nueva();
    if (n) URL.revokeObjectURL(n.vista);
  }
}
