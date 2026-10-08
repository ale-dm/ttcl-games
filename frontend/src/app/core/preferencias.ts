// Preferencias del visitante (tema e idioma) en localStorage. Misma clave que el script de index.html.
// Puede fallar (modo privado, almacenamiento bloqueado): entonces simplemente no se recuerdan.

const CLAVE = 'ttcl.prefs';

export interface Preferencias {
  theme?: 'light' | 'dark';
  lang?: 'es' | 'en';
}

export function leerPreferencias(): Preferencias {
  try {
    return JSON.parse(localStorage.getItem(CLAVE) ?? '{}') as Preferencias;
  } catch {
    return {};
  }
}

export function guardarPreferencias(cambios: Preferencias): void {
  try {
    localStorage.setItem(CLAVE, JSON.stringify({ ...leerPreferencias(), ...cambios }));
  } catch {
    // Sin almacenamiento: la preferencia dura lo que dure la pestaña.
  }
}
