import { Component, computed, effect, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Avatar } from '../../compartido/avatar';
import { Forma } from '../../compartido/forma';
import { Api } from '../../core/api';
import { cargaReactiva } from '../../core/carga';
import { I18n, JUEGO_CORTO, NOMBRE_JUEGO } from '../../core/i18n';
import { COLUMNAS_RANKING, claveTexto, metrica as defMetrica, valorDe } from '../../core/metricas';
import { JUEGOS, Juego, Periodo, periodoDe } from '../../core/modelos';
import { DuendeEstado } from '../../duende/duende-estado';
import { SelectorPeriodo } from '../../compartido/selector-periodo';

@Component({
  selector: 'app-ranking-pagina',
  imports: [RouterLink, Avatar, Forma, SelectorPeriodo],
  template: `
    <div class="wrap page">
      <div class="ph">
        <div>
          <h1>{{ t('ranking.titulo') }}</h1>
          <p class="sub">{{ t('ranking.sub') }}</p>
        </div>
        <div class="ph-ctrl">
          <app-selector-periodo
            [periodo]="periodoSel()"
            [compacto]="false"
            (cambio)="cambiar({ periodo: $event === 'todo' ? null : $event })"
          />
          <div class="seg" role="group">
            @for (g of juegos; track g) {
              <button type="button" [class.on]="g === juegoSel()" (click)="cambiar({ juego: g, metrica: null })">
                {{ juegoCorto[g] }}
              </button>
            }
          </div>
        </div>
      </div>

      @let carga = ranking.estado();
      @if (carga.error !== null) {
        <div class="aviso">
          <p>{{ t('comun.errorApi') }}</p>
          <button class="btn sm" type="button" (click)="ranking.recargar()">{{ t('comun.reintentar') }}</button>
        </div>
      } @else if (!carga.datos || carga.datos.juego !== juegoSel()) {
        <div class="skel" style="height: 300px"></div>
      } @else if (!filas().length) {
        <div class="aviso">
          @if (periodoSel() === 'todo') {
            {{ t('ranking.vacio', { juego: nombreJuego[juegoSel()] }) }}
          } @else {
            {{ t('periodo.nadie', { juego: nombreJuego[juegoSel()], periodo: i18n.enPeriodo(periodoSel()) }) }}
          }
        </div>
      } @else {
        <section class="card">
          <div class="tbl-wrap">
            <table class="tbl">
              <thead>
                <tr>
                  <th>#</th>
                  <th>{{ t('tabla.jugador') }}</th>
                  @for (col of columnas(); track col) {
                    <th class="r" [class.on]="col === orden()" [attr.aria-sort]="col === orden() ? 'descending' : null">
                      <button type="button" (click)="cambiar({ metrica: col })" [attr.title]="t('ranking.ordenar', { metrica: t(claveTexto(col)) })">
                        {{ t(claveTexto(col)) }}{{ col === orden() ? ' ↓' : '' }}
                      </button>
                    </th>
                  }
                  <th class="hide-md">{{ t('jugador.forma') }}</th>
                </tr>
              </thead>
              <tbody>
                @for (f of filas(); track f.slug; let i = $index) {
                  <tr>
                    <td><span class="pos" [class.p1]="i === 0">{{ i + 1 }}</span></td>
                    <td>
                      <a
                        class="side-h"
                        [routerLink]="['/jugador', f.slug]"
                        [queryParams]="{ juego: juegoSel(), periodo: periodoSel() === 'todo' ? null : periodoSel() }"
                      >
                        <app-avatar [slug]="f.slug" [nombre]="f.nombre" [tam]="30" />
                        <span>{{ f.nombre }}</span>
                      </a>
                    </td>
                    @for (col of columnas(); track col) {
                      <td class="r num" [class.on]="col === orden()">{{ i18n.valor(valor(f.resumen, col), formato(col)) }}</td>
                    }
                    <td class="hide-md"><app-forma [forma]="f.resumen.forma.slice(0, 5)" /></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
        </section>
      }
    </div>
  `,
})
export class RankingPagina {
  protected readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly duende = inject(DuendeEstado);
  protected readonly juegos = JUEGOS;
  protected readonly juegoCorto = JUEGO_CORTO;
  protected readonly nombreJuego = NOMBRE_JUEGO;
  protected readonly claveTexto = claveTexto;
  protected readonly valor = valorDe;

  /** /ranking?juego=cs2&metrica=kd&periodo=7d */
  readonly juego = input<string | undefined>();
  readonly metrica = input<string | undefined>();
  readonly periodo = input<string | undefined>();

  protected readonly juegoSel = computed<Juego>(() => (this.juego() === 'smite2' ? 'smite2' : 'cs2'));
  protected readonly periodoSel = computed(() => periodoDe(this.periodo()));
  protected readonly columnas = computed(() => COLUMNAS_RANKING[this.juegoSel()]);
  protected readonly orden = computed(() => {
    const m = this.metrica();
    return m && this.columnas().includes(m) ? m : 'kd';
  });

  protected readonly ranking = cargaReactiva(
    () => ({ juego: this.juegoSel(), periodo: this.periodoSel() }),
    (p) => this.api.ranking(p.juego, p.periodo),
  );

  /** Ordenadas por la métrica elegida, de mejor a peor (en muertes, menos es mejor). Sin dato, al final. */
  protected readonly filas = computed(() => {
    const filas = [...(this.ranking.estado().datos?.filas ?? [])];
    const clave = this.orden();
    const signo = defMetrica(clave).mejor === 'bajo' ? 1 : -1;
    return filas.sort((x, y) => {
      const a = valorDe(x.resumen, clave);
      const b = valorDe(y.resumen, clave);
      if (a === null) return 1;
      if (b === null) return -1;
      return signo * (a - b);
    });
  });

  constructor() {
    effect(() => this.duende.fijarContexto({ foco: [], juego: this.juegoSel(), periodo: this.periodoSel() }));
  }

  protected formato(clave: string) {
    return defMetrica(clave).formato;
  }

  protected cambiar(cambios: { juego?: Juego; metrica?: string | null; periodo?: Periodo | null }): void {
    this.router.navigate([], { queryParams: cambios, queryParamsHandling: 'merge', replaceUrl: true });
  }
}
