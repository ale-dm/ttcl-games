import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../core/api';
import { I18n } from '../core/i18n';
import { Insight, Juego, Voto } from '../core/modelos';

// En localStorage: el id de este navegador y sus votos a recomendaciones. Puede fallar (modo privado, almacenamiento
// bloqueado): entonces el id dura lo que la pestaña y los votos no se recuerdan al volver.
const CLAVE_VOTANTE = 'ttcl.votante';
const CLAVE_VOTOS = 'ttcl.votos';
/** Votos a recomendaciones que se recuerdan; al pasar de ahí se olvidan los más antiguos. */
const MAX_RECORDADOS = 300;

/** 32 caracteres hexadecimales al azar. getRandomValues funciona también sin HTTPS (randomUUID no). */
function idAlAzar(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('');
}

function leerVotante(): string {
  try {
    const guardado = localStorage.getItem(CLAVE_VOTANTE);
    if (guardado && /^[A-Za-z0-9-]{8,40}$/.test(guardado)) return guardado;
  } catch {
    // Sin almacenamiento: uno nuevo.
  }
  const nuevo = idAlAzar();
  try {
    localStorage.setItem(CLAVE_VOTANTE, nuevo);
  } catch {
    // Sin almacenamiento: vale para esta pestaña.
  }
  return nuevo;
}

function leerVotos(): Record<string, 1 | -1> {
  try {
    const votos: unknown = JSON.parse(localStorage.getItem(CLAVE_VOTOS) ?? '{}');
    return votos && typeof votos === 'object' ? (votos as Record<string, 1 | -1>) : {};
  } catch {
    return {};
  }
}

const claveConsejo = (slug: string, juego: Juego, insight: string) => `${slug}|${juego}|${insight}`;

/**
 * Los 👍 y 👎 de este navegador (P7). Se vota con un id al azar, sin datos personales: un voto por cosa valorada, que
 * se puede cambiar o quitar. Los votos a recomendaciones se recuerdan (siguen marcados al volver al perfil); los de
 * las respuestas del chat van en cada mensaje (DuendeEstado) y duran lo que la conversación.
 */
@Injectable({ providedIn: 'root' })
export class Valoraciones {
  private readonly api = inject(Api);
  private readonly i18n = inject(I18n);

  readonly votante = leerVotante();
  private readonly consejos = signal(leerVotos());

  votoConsejo(slug: string, juego: Juego, insight: string): 1 | -1 | null {
    return this.consejos()[claveConsejo(slug, juego, insight)] ?? null;
  }

  /** Marca el voto al momento; si no se puede guardar, vuelve a como estaba. */
  votarConsejo(slug: string, juego: Juego, insight: Insight, voto: Voto): void {
    const clave = claveConsejo(slug, juego, insight.id);
    const antes = this.consejos()[clave] ?? 0;
    this.fijar(clave, voto);
    this.api
      .valorarConsejo({
        votante: this.votante,
        voto,
        jugador: slug,
        juego,
        insight: insight.id,
        nivel: insight.nivel,
        lang: this.i18n.idioma(),
        texto: [insight.titulo, insight.texto, insight.consejo].filter(Boolean).join('\n'),
      })
      .subscribe({ error: () => this.fijar(clave, antes) });
  }

  private fijar(clave: string, voto: Voto): void {
    this.consejos.update((votos) => {
      // Se quita y se vuelve a poner: el último votado queda al final, y lo que se olvida es lo más antiguo.
      const nuevos = { ...votos };
      delete nuevos[clave];
      if (voto) nuevos[clave] = voto;
      const claves = Object.keys(nuevos);
      return claves.length <= MAX_RECORDADOS
        ? nuevos
        : Object.fromEntries(claves.slice(-MAX_RECORDADOS).map((c) => [c, nuevos[c]]));
    });
    try {
      localStorage.setItem(CLAVE_VOTOS, JSON.stringify(this.consejos()));
    } catch {
      // Sin almacenamiento: el voto se guarda en la API igual, solo que no sale marcado al volver.
    }
  }
}
