import { HttpErrorResponse } from '@angular/common/http';
import { Signal, computed, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { Observable, catchError, map, of, switchMap, tap } from 'rxjs';

export interface Carga<T> {
  cargando: boolean;
  /** Código HTTP del fallo (0 si no hubo respuesta), o null si no hay error. */
  error: number | null;
  datos: T | null;
}

/**
 * Datos que se vuelven a pedir cada vez que cambian sus parámetros (ruta, juego, idioma...). Mientras recarga
 * conserva los datos anteriores, así cambiar de idioma no deja la página en blanco. Si `params` devuelve null, no
 * pide nada. Hay que crearlo en un contexto de inyección (campo o constructor de un componente).
 */
export function cargaReactiva<P, T>(
  params: () => P | null,
  cargar: (p: P) => Observable<T>,
): { estado: Signal<Carga<T>>; recargar: () => void } {
  const estado = signal<Carga<T>>({ cargando: true, error: null, datos: null });
  const reintento = signal(0);
  const fuente = computed(() => ({ p: params(), n: reintento() }));

  toObservable(fuente)
    .pipe(
      tap(({ p }) => {
        if (p !== null) estado.update((e) => ({ ...e, cargando: true, error: null }));
      }),
      switchMap(({ p }) =>
        p === null
          ? of<Carga<T>>({ cargando: false, error: null, datos: null })
          : cargar(p).pipe(
              map((datos): Carga<T> => ({ cargando: false, error: null, datos })),
              catchError((e: HttpErrorResponse) => of<Carga<T>>({ cargando: false, error: e.status ?? 0, datos: null })),
            ),
      ),
      takeUntilDestroyed(),
    )
    .subscribe((e) => estado.set(e));

  return { estado: estado.asReadonly(), recargar: () => reintento.update((n) => n + 1) };
}
