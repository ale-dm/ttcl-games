import { Component, computed, effect, inject, input } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { Avatar } from '../../compartido/avatar';
import { Forma } from '../../compartido/forma';
import { MarcaDuende } from '../../compartido/marca-duende';
import { Api } from '../../core/api';
import { cargaReactiva } from '../../core/carga';
import { I18n, JUEGO_CORTO, NOMBRE_JUEGO } from '../../core/i18n';
import { JUEGOS, Juego, TarjetaJugador } from '../../core/modelos';
import { DuendeEstado } from '../../duende/duende-estado';

@Component({
  selector: 'app-equipo-pagina',
  imports: [RouterLink, Avatar, Forma, MarcaDuende],
  templateUrl: './equipo-pagina.html',
})
export class EquipoPagina {
  protected readonly i18n = inject(I18n);
  protected readonly t = this.i18n.t;
  private readonly api = inject(Api);
  private readonly router = inject(Router);
  private readonly duende = inject(DuendeEstado);
  protected readonly juegos = JUEGOS;
  protected readonly nombreJuego = NOMBRE_JUEGO;
  protected readonly juegoCorto = JUEGO_CORTO;

  /** ?juego=cs2 en la URL (enlazado por withComponentInputBinding). */
  readonly juego = input<string | undefined>();
  protected readonly filtro = computed<Juego | null>(() => {
    const j = this.juego();
    return j === 'cs2' || j === 'smite2' ? j : null;
  });

  protected readonly equipo = cargaReactiva(
    () => ({ juego: this.filtro(), lang: this.i18n.idioma() }),
    (p) => this.api.equipo(p.juego, p.lang),
  );
  protected readonly estado = cargaReactiva(
    () => true,
    () => this.api.estado(),
  );
  protected readonly grupos = cargaReactiva(
    () => ({ juego: this.filtro() }),
    (p) => this.api.grupos(p.juego),
  );
  /** El mejor dúo y el mejor trío de cada juego, los que haya. */
  protected readonly mejoresGrupos = computed(() =>
    (this.grupos.estado().datos ?? []).flatMap((g) => [
      ...(g.duos.length ? [{ juego: g.juego, tipo: 'equipo.duo' as const, grupo: g.duos[0] }] : []),
      ...(g.trios.length ? [{ juego: g.juego, tipo: 'equipo.trio' as const, grupo: g.trios[0] }] : []),
    ]),
  );

  constructor() {
    effect(() => this.duende.fijarContexto({ foco: [], juego: this.filtro() }));
  }

  protected filtrar(juego: Juego | null): void {
    this.router.navigate([], { queryParams: { juego }, replaceUrl: true });
  }

  protected nick(t: TarjetaJugador, juego: Juego): string {
    return t.cuentas.find((c) => c.juego === juego)?.nick ?? '';
  }

  protected anchoWinrate(wr: number | null): number {
    return Math.max(0, Math.min(100, wr ?? 0));
  }
}
