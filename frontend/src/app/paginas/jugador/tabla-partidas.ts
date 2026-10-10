import { Component, computed, inject, input, signal } from '@angular/core';
import { MarcaDuende } from '../../compartido/marca-duende';
import { Api } from '../../core/api';
import { I18n } from '../../core/i18n';
import { Juego, PartidaVista, RondasPartida } from '../../core/modelos';
import { RondasPartidaVista } from './rondas-partida';

/** Las rondas desplegadas de una partida: cargando, con error o ya cargadas. */
interface Desplegada {
  datos: RondasPartida | null;
  error: boolean;
}

/**
 * Historial de partidas. Las columnas dependen del juego (mapa y ADR en CS2; dios y daño en SMITE 2). Debajo de cada
 * partida con algo especial, lo que dice el Duende. En CS2, con el jugador (`slug`), las partidas con la demo
 * analizada se pueden desplegar para ver sus rondas (P12).
 */
@Component({
  selector: 'app-tabla-partidas',
  imports: [MarcaDuende, RondasPartidaVista],
  template: `
    <div class="tbl-wrap">
      <table class="tbl">
        <thead>
          <tr>
            <th>{{ t('tabla.resultado') }}</th>
            <th>{{ juego() === 'cs2' ? t('tabla.mapa') : t('tabla.dios') }}</th>
            <th class="hide-sm">{{ juego() === 'cs2' ? t('tabla.marcador') : t('tabla.cola') }}</th>
            <th class="r">{{ t('tabla.kda') }}</th>
            <th class="r">{{ juego() === 'cs2' ? 'ADR' : t('metrica.dano') }}</th>
            <th class="r hide-sm">{{ juego() === 'cs2' ? t('metrica.hs_pct') : t('metrica.oro_min') }}</th>
            <th class="hide-md">{{ t('tabla.con') }}</th>
            <th class="r">{{ t('tabla.fecha') }}</th>
            @if (conRondas()) {
              <th class="r"><span class="sr-only">{{ t('tabla.rondas') }}</span></th>
            }
          </tr>
        </thead>
        <tbody>
          @for (p of partidas(); track p.partidaId) {
            @let abierta = desplegadas().get(p.partidaId);
            <tr [class.con-coment]="p.comentario || abierta">
              <td>
                @if (p.gano === null) {
                  <span class="muted">—</span>
                } @else {
                  <span class="res" [class.w]="p.gano" [class.l]="!p.gano">
                    {{ p.gano ? t('comun.victoria') : t('comun.derrota') }}
                  </span>
                }
              </td>
              <td class="mono">{{ juego() === 'cs2' ? (p.datos['mapa'] ?? p.modo) : p.datos['dios'] }}</td>
              <td class="hide-sm num muted">{{ juego() === 'cs2' ? (p.datos['marcador'] ?? '—') : (p.modo ?? '—') }}</td>
              <td class="r num">{{ p.kills ?? '—' }} / {{ p.muertes ?? '—' }} / {{ p.asistencias ?? '—' }}</td>
              <td class="r num">{{ juego() === 'cs2' ? i18n.num(numero(p.datos['adr']), 1) : i18n.entero(numero(p.datos['dano'])) }}</td>
              <td class="r num hide-sm">
                {{ juego() === 'cs2' ? i18n.pct(numero(p.datos['hs_pct']), 0) : i18n.entero(oroMin(p)) }}
              </td>
              <td class="hide-md muted">{{ p.companeros.length ? p.companeros.join(', ') : t('comun.solo') }}</td>
              <td class="r muted" [attr.title]="i18n.fechaHora(p.jugadaEn)">{{ i18n.relativo(p.jugadaEn) }}</td>
              @if (conRondas()) {
                <td class="r">
                  @if (p.analizada) {
                    <button
                      class="btn ghost sm rp-btn"
                      type="button"
                      [class.on]="abierta"
                      [attr.aria-expanded]="!!abierta"
                      [attr.aria-label]="abierta ? t('tabla.ocultarRondas') : t('tabla.verRondas')"
                      (click)="alternar(p.partidaId)"
                    >
                      {{ t('tabla.rondas') }}
                    </button>
                  }
                </td>
              }
            </tr>
            @if (p.comentario) {
              <tr class="coment" [class.con-coment]="abierta">
                <td [attr.colspan]="columnas()">
                  <app-marca-duende [tam]="16" />
                  <span class="sr-only">{{ t('tabla.duende') }}:</span>
                  {{ p.comentario }}
                </td>
              </tr>
            }
            @if (abierta) {
              <tr class="rondas-fila">
                <td [attr.colspan]="columnas()">
                  @if (abierta.datos) {
                    <app-rondas-partida [datos]="abierta.datos" />
                  } @else if (abierta.error) {
                    <p class="small muted">{{ t('rondas.error') }}</p>
                  } @else {
                    <p class="small muted">{{ t('rondas.cargando') }}</p>
                  }
                </td>
              </tr>
            }
          }
        </tbody>
      </table>
    </div>
  `,
})
export class TablaPartidas {
  protected readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  private readonly api = inject(Api);
  readonly partidas = input.required<PartidaVista[]>();
  readonly juego = input.required<Juego>();
  /** El jugador del historial: con él (y en CS2), las partidas analizadas enseñan sus rondas. */
  readonly slug = input<string | null>(null);

  protected readonly conRondas = computed(() => this.juego() === 'cs2' && !!this.slug());
  protected readonly columnas = computed(() => (this.conRondas() ? 9 : 8));
  protected readonly desplegadas = signal(new Map<number, Desplegada>());

  protected alternar(partidaId: number): void {
    const slug = this.slug();
    if (!slug) return;
    if (this.desplegadas().has(partidaId)) {
      this.cambiar(partidaId, null);
      return;
    }
    this.cambiar(partidaId, { datos: null, error: false });
    this.api.rondas(slug, partidaId).subscribe({
      next: (datos) => this.desplegadas().has(partidaId) && this.cambiar(partidaId, { datos, error: false }),
      error: () => this.desplegadas().has(partidaId) && this.cambiar(partidaId, { datos: null, error: true }),
    });
  }

  private cambiar(partidaId: number, valor: Desplegada | null): void {
    this.desplegadas.update((m) => {
      const nuevo = new Map(m);
      if (valor) nuevo.set(partidaId, valor);
      else nuevo.delete(partidaId);
      return nuevo;
    });
  }

  protected numero(v: number | string | null | undefined): number | null {
    return typeof v === 'number' ? v : null;
  }

  protected oroMin(p: PartidaVista): number | null {
    const oro = this.numero(p.datos['oro']);
    const min = this.numero(p.datos['minutos']);
    return oro !== null && min ? oro / min : null;
  }
}
