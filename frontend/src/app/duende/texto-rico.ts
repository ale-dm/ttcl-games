import { Component, computed, input } from '@angular/core';

/** Un trozo de línea: texto normal o en **negrita**. */
export interface Trozo {
  texto: string;
  negrita: boolean;
}

export type Bloque = { tipo: 'p'; trozos: Trozo[] } | { tipo: 'ul'; items: Trozo[][] };

/** Separa **negritas** sin interpretar HTML: el texto del Duende nunca se inserta como HTML. */
export function trozos(linea: string): Trozo[] {
  return linea
    .split(/(\*\*[^*]+\*\*)/g)
    .filter(Boolean)
    .map((p) =>
      p.startsWith('**') && p.endsWith('**') ? { texto: p.slice(2, -2), negrita: true } : { texto: p, negrita: false },
    );
}

/** Párrafos separados por línea en blanco; las líneas que empiezan por "- " o "* " forman listas. */
export function bloques(texto: string): Bloque[] {
  const resultado: Bloque[] = [];
  for (const parrafo of texto.replace(/\r/g, '').split(/\n{2,}/)) {
    const lineas = parrafo.split('\n').filter((l) => l.trim());
    let lista: Trozo[][] | null = null;
    let normal: string[] = [];
    const cerrarNormal = () => {
      if (normal.length) resultado.push({ tipo: 'p', trozos: trozos(normal.join(' ')) });
      normal = [];
    };
    for (const linea of lineas) {
      const item = /^\s*[-*•]\s+(.*)$/.exec(linea);
      if (item) {
        cerrarNormal();
        if (!lista) {
          lista = [];
          resultado.push({ tipo: 'ul', items: lista });
        }
        lista.push(trozos(item[1]));
      } else {
        lista = null;
        normal.push(linea.trim());
      }
    }
    cerrarNormal();
  }
  return resultado;
}

@Component({
  selector: 'app-texto-rico',
  template: `
    @for (b of bloques(); track $index) {
      @if (b.tipo === 'p') {
        <p>
          @for (t of b.trozos; track $index) {
            @if (t.negrita) {
              <b>{{ t.texto }}</b>
            } @else {
              {{ t.texto }}
            }
          }
        </p>
      } @else {
        <ul>
          @for (item of b.items; track $index) {
            <li>
              @for (t of item; track $index) {
                @if (t.negrita) {
                  <b>{{ t.texto }}</b>
                } @else {
                  {{ t.texto }}
                }
              }
            </li>
          }
        </ul>
      }
    }
  `,
  styles: `
    :host {
      display: block;
    }
  `,
})
export class TextoRico {
  readonly texto = input.required<string>();
  protected readonly bloques = computed(() => bloques(this.texto()));
}
