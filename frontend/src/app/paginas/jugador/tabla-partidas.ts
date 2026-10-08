import { Component, inject, input } from '@angular/core';
import { I18n } from '../../core/i18n';
import { Juego, PartidaVista } from '../../core/modelos';

/** Historial de partidas. Las columnas dependen del juego (mapa y ADR en CS2; dios y daño en SMITE 2). */
@Component({
  selector: 'app-tabla-partidas',
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
          </tr>
        </thead>
        <tbody>
          @for (p of partidas(); track p.partidaId) {
            <tr>
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
            </tr>
          }
        </tbody>
      </table>
    </div>
  `,
})
export class TablaPartidas {
  protected readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  readonly partidas = input.required<PartidaVista[]>();
  readonly juego = input.required<Juego>();

  protected numero(v: number | string | null | undefined): number | null {
    return typeof v === 'number' ? v : null;
  }

  protected oroMin(p: PartidaVista): number | null {
    const oro = this.numero(p.datos['oro']);
    const min = this.numero(p.datos['minutos']);
    return oro !== null && min ? oro / min : null;
  }
}
