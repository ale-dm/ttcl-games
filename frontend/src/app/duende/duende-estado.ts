import { Injectable, computed, inject, signal } from '@angular/core';
import { Api } from '../core/api';
import { I18n, JUEGO_CORTO } from '../core/i18n';
import { Juego, MensajeChat, Periodo, Voto } from '../core/modelos';
import { Valoraciones } from './valoraciones';

/**
 * De quién va la conversación: nadie (todo el equipo), un jugador o dos (comparación). Con el juego y el periodo que
 * se ven en la página: el Duende los usa si la pregunta no dice otros.
 */
export interface ContextoDuende {
  foco: { slug: string; nombre: string }[];
  juego: Juego | null;
  periodo?: Periodo;
}

export interface MensajeVista extends MensajeChat {
  origen?: 'gemini' | 'reglas';
  modelo?: string | null;
  intencion?: string | null;
  /** El 👍 (1) o 👎 (-1) que le ha dado el visitante, si se lo ha dado. */
  voto?: 1 | -1 | null;
  error?: boolean;
}

const SIN_CONTEXTO: ContextoDuende = { foco: [], juego: null };

/**
 * Estado del chat del Duende, compartido por toda la web. Cada página dice de quién se está hablando
 * (fijarContexto); si cambia, la conversación empieza de cero.
 */
@Injectable({ providedIn: 'root' })
export class DuendeEstado {
  private readonly api = inject(Api);
  private readonly i18n = inject(I18n);
  private readonly valoraciones = inject(Valoraciones);

  readonly abierto = signal(false);
  readonly contexto = signal<ContextoDuende>(SIN_CONTEXTO);
  readonly mensajes = signal<MensajeVista[]>([]);
  readonly pensando = signal(false);
  private readonly sugerenciasApi = signal<string[] | null>(null);
  /** Para descartar respuestas que llegan después de cambiar de contexto. */
  private turno = 0;
  /** Cambia con cada conversación nueva: si un voto falla tarde, no toca los mensajes de otra. */
  private conversacion = 0;

  /** Saludo inicial, traducido al vuelo (no se guarda como mensaje para que cambie con el idioma). */
  readonly saludo = computed(() => {
    const { foco } = this.contexto();
    const t = this.i18n.t;
    if (foco.length === 2) return t('duende.saludoComparar', { a: foco[0].nombre, b: foco[1].nombre });
    if (foco.length === 1) return t('duende.saludoJugador', { nombre: foco[0].nombre });
    return t('duende.saludoEquipo');
  });

  /** Línea bajo el título del panel. */
  readonly etiqueta = computed(() => {
    const { foco, juego, periodo } = this.contexto();
    const t = this.i18n.t;
    const sufijo =
      (juego ? ` · ${JUEGO_CORTO[juego]}` : '') + (periodo && periodo !== 'todo' ? ` · ${t(`periodo.${periodo}`)}` : '');
    if (foco.length === 2) return t('duende.ctxComparar', { a: foco[0].nombre, b: foco[1].nombre }) + sufijo;
    if (foco.length === 1) return t('duende.ctxJugador', { nombre: foco[0].nombre }) + sufijo;
    return t('duende.ctxEquipo') + sufijo;
  });

  /** Preguntas rápidas: las que devuelve el Duende o, antes de la primera respuesta, unas por defecto. */
  readonly sugerencias = computed(() => {
    const api = this.sugerenciasApi();
    if (api) return api;
    const { foco } = this.contexto();
    const t = this.i18n.t;
    if (foco.length === 2) {
      return [t('comparar.preguntaQuien'), t('comparar.preguntaMejorar', { nombre: foco[1].nombre })];
    }
    if (foco.length === 1) {
      return [
        t('duende.sugMejorar'),
        t('duende.sugBien'),
        t('duende.sugRacha'),
        t('duende.sugConQuien'),
        t('duende.sugCuando'),
      ];
    }
    return [t('duende.sugMejorEquipo'), t('duende.sugCadaUno'), t('duende.sugMejorDuo')];
  });

  fijarContexto(nuevo: ContextoDuende): void {
    const actual = this.contexto();
    const clave = (c: ContextoDuende) =>
      c.foco.map((f) => f.slug).join('|') + '#' + (c.juego ?? '') + '#' + (c.periodo ?? 'todo');
    if (clave(actual) !== clave(nuevo)) {
      this.contexto.set(nuevo);
      this.reiniciar();
    } else if (actual.foco.some((f, i) => f.nombre !== nuevo.foco[i]?.nombre)) {
      this.contexto.set(nuevo);
    }
  }

  abrir(pregunta?: string): void {
    this.abierto.set(true);
    if (pregunta) this.enviar(pregunta);
  }

  cerrar(): void {
    this.abierto.set(false);
  }

  reiniciar(): void {
    this.turno++;
    this.conversacion++;
    this.mensajes.set([]);
    this.sugerenciasApi.set(null);
    this.pensando.set(false);
  }

  enviar(texto: string): void {
    const pregunta = texto.trim();
    if (!pregunta || this.pensando()) return;
    const turno = ++this.turno;
    this.mensajes.update((m) => [...m, { rol: 'usuario', texto: pregunta }]);
    this.pensando.set(true);

    const saludo: MensajeChat = { rol: 'duende', texto: this.saludo() };
    const historial: MensajeChat[] = [
      saludo,
      ...this.mensajes()
        .filter((m) => !m.error)
        .map(({ rol, texto }): MensajeChat => ({ rol, texto })),
    ].slice(-30);
    const { foco, juego, periodo } = this.contexto();

    this.api
      .chat({
        lang: this.i18n.idioma(),
        mensajes: historial,
        foco: foco.map((f) => f.slug),
        juego,
        periodo: periodo && periodo !== 'todo' ? periodo : null,
      })
      .subscribe({
        next: (r) => {
          if (turno !== this.turno) return;
          this.mensajes.update((m) => [
            ...m,
            { rol: 'duende', texto: r.respuesta, origen: r.origen, modelo: r.modelo, intencion: r.intencion },
          ]);
          if (r.sugerencias?.length) this.sugerenciasApi.set(r.sugerencias);
          this.pensando.set(false);
        },
        error: (e: { error?: { error?: string } }) => {
          if (turno !== this.turno) return;
          const motivo = e?.error?.error ?? this.i18n.t('duende.errorRed');
          this.mensajes.update((m) => [
            ...m,
            { rol: 'duende', texto: this.i18n.t('duende.error', { error: motivo }), error: true },
          ]);
          this.pensando.set(false);
        },
      });
  }

  /**
   * 👍 (1), 👎 (-1) o quitar el voto (0) a una respuesta del Duende, con la pregunta que la provocó. Se marca al
   * momento; si no se puede guardar, vuelve a como estaba.
   */
  valorar(indice: number, voto: Voto): void {
    const mensajes = this.mensajes();
    const mensaje = mensajes[indice];
    if (!mensaje?.origen) return;
    const pregunta = mensajes
      .slice(0, indice)
      .reverse()
      .find((m) => m.rol === 'usuario');
    const antes = mensaje.voto ?? 0;
    const conversacion = this.conversacion;
    this.fijarVoto(indice, voto);
    const { foco, juego } = this.contexto();
    this.api
      .valorarRespuesta({
        votante: this.valoraciones.votante,
        voto,
        lang: this.i18n.idioma(),
        foco: foco.map((f) => f.slug),
        juego,
        pregunta: pregunta?.texto ?? '',
        respuesta: mensaje.texto,
        origen: mensaje.origen,
        modelo: mensaje.modelo ?? null,
        intencion: mensaje.intencion ?? null,
      })
      .subscribe({
        error: () => {
          if (conversacion === this.conversacion) this.fijarVoto(indice, antes);
        },
      });
  }

  private fijarVoto(indice: number, voto: Voto): void {
    this.mensajes.update((m) => m.map((x, i) => (i === indice ? { ...x, voto: voto || null } : x)));
  }
}
