import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { I18n } from '../../core/i18n';
import { CampoCaptura, CapturaVista, EvolucionCapturas } from '../../core/modelos';
import { PanelEvolucion } from './panel-evolucion';

/** Lo que falta de P13 en la web: la lista con el borrado, pegar con Ctrl+V, la métrica de la gráfica y el comparador. */

const CAMPOS: CampoCaptura[] = [
  { clave: 'kd', grupo: 'general', tipo: 'valor', formato: 'dec', mejor: 'alto' },
  { clave: 'adr', grupo: 'general', tipo: 'valor', formato: 'int', mejor: 'alto' },
  { clave: 'partidas', grupo: 'totales', tipo: 'total', formato: 'int', mejor: 'neutral' },
];
const captura = (id: number, cargadaEn: string, datos: Record<string, number>): CapturaVista => ({
  id,
  juego: 'cs2',
  fuente: 'csstats',
  cargadaEn,
  leidaPor: 'gemini',
  datos,
  mapas: [],
  imagen: true,
});
const DOS = [
  captura(1, '2026-09-01T20:00:00Z', { kd: 0.6, adr: 57, partidas: 344 }),
  captura(2, '2026-09-15T20:00:00Z', { kd: 0.7, adr: 55, partidas: 364 }),
];
const COMPARACION: EvolucionCapturas = {
  desdeId: 1,
  desde: DOS[0].cargadaEn,
  hastaId: 2,
  hasta: DOS[1].cargadaEn,
  dias: 14,
  filas: [],
  tramo: null,
};

describe('PanelEvolucion · lista, borrado, pegar, métrica y comparador (P13)', () => {
  let http: HttpTestingController;
  let panel: ComponentFixture<PanelEvolucion>;
  let el: HTMLElement;

  afterEach(() => http.verify());

  /** Contesta las comparaciones que el panel pide al recargar la lista (con la misma respuesta). */
  const contestarComparaciones = () =>
    http.match((r) => r.url === '/api/jugadores/j1/capturas/comparar').forEach((p) => p.flush(COMPARACION));

  /** El panel de Ana con esas capturas; se contestan las peticiones de los campos, la lista y la comparación. */
  async function abrir(capturas: CapturaVista[]): Promise<void> {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    TestBed.inject(I18n).cambiar('es');
    http = TestBed.inject(HttpTestingController);
    URL.createObjectURL = () => 'blob:vista';
    URL.revokeObjectURL = () => {};
    panel = TestBed.createComponent(PanelEvolucion);
    panel.componentRef.setInput('slug', 'j1');
    panel.componentRef.setInput('nombre', 'Ana');
    await panel.whenStable();
    http.expectOne('/api/capturas/campos').flush(CAMPOS);
    http.expectOne('/api/jugadores/j1/capturas').flush(capturas);
    await panel.whenStable();
    if (capturas.length >= 2) {
      http.expectOne((r) => r.url === '/api/jugadores/j1/capturas/comparar').flush(COMPARACION);
      await panel.whenStable();
    }
    el = panel.nativeElement as HTMLElement;
  }

  const boton = (t: string) => [...el.querySelectorAll('button')].find((b) => b.textContent!.trim() === t)!;

  it('la lista enseña la más reciente primero, con el enlace a su imagen', async () => {
    await abrir(DOS);
    const filas = [...el.querySelectorAll('.evo-item')];
    expect(filas.length).toBe(2);
    expect(filas[0].querySelector('.evo-item-a a')!.getAttribute('href')).toBe('/api/jugadores/j1/capturas/2/imagen');
    expect(filas[1].querySelector('.evo-item-a a')!.getAttribute('href')).toBe('/api/jugadores/j1/capturas/1/imagen');
  });

  it('borrar pide confirmación; si se acepta, borra y vuelve a pedir la lista', async () => {
    await abrir(DOS);
    const borrar = [...el.querySelectorAll<HTMLButtonElement>('.evo-item button')].find(
      (b) => b.textContent!.trim() === 'Borrar',
    )!;

    vi.spyOn(window, 'confirm').mockReturnValue(false);
    borrar.click();
    http.expectNone((r) => r.method === 'DELETE');

    vi.mocked(window.confirm).mockReturnValue(true);
    borrar.click();
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/jugadores/j1/capturas/2').flush(null, { status: 204, statusText: 'No Content' });
    contestarComparaciones();
    await panel.whenStable();
    http.expectOne('/api/jugadores/j1/capturas').flush([DOS[0]]);
    contestarComparaciones();
    await panel.whenStable();
    expect(el.querySelectorAll('.evo-item').length).toBe(1);
  });

  it('pegar con Ctrl+V una imagen abre la revisión; pegar texto no hace nada', async () => {
    await abrir([]);

    const texto = new Event('paste');
    Object.defineProperty(texto, 'clipboardData', { value: { files: [] } });
    window.dispatchEvent(texto);
    await panel.whenStable();
    expect(el.querySelector('.evo-nueva')).toBeNull();

    const pegado = new Event('paste');
    const imagen = new File(['png'], 'portapapeles.png', { type: 'image/png' });
    Object.defineProperty(pegado, 'clipboardData', { value: { files: [imagen] } });
    window.dispatchEvent(pegado);
    await panel.whenStable();
    const leer = http.expectOne('/api/jugadores/j1/capturas/leer');
    expect((leer.request.body as FormData).get('imagen')).toBeInstanceOf(Blob);
    leer.flush({ origen: 'ninguno', modelo: null, motivo: 'sin_clave', datos: {}, mapas: [] });
    await panel.whenStable();
    expect(el.querySelector('.evo-nueva')).not.toBeNull();
  });

  it('cambiar la métrica de la gráfica dibuja otro número', async () => {
    await abrir(DOS);
    const grafica = el.querySelector('svg.evo-grafica')!;
    const antes = grafica.getAttribute('aria-label');
    const selector = el.querySelector<HTMLSelectElement>('#evo-metrica')!;
    selector.value = 'adr';
    selector.dispatchEvent(new Event('change'));
    await panel.whenStable();
    expect(grafica.getAttribute('aria-label')).not.toBe(antes);
    expect(el.querySelectorAll('.evo-pt').length).toBe(2);
  });

  it('el comparador pide las dos fechas elegidas', async () => {
    await abrir(DOS);
    const hasta = el.querySelector<HTMLSelectElement>('#evo-hasta')!;
    hasta.value = '1';
    hasta.dispatchEvent(new Event('change'));
    await panel.whenStable();
    const peticion = http.expectOne((r) => r.url === '/api/jugadores/j1/capturas/comparar');
    expect(peticion.request.params.get('desde')).toBe('1');
    expect(peticion.request.params.get('hasta')).toBe('1');
    peticion.flush({ ...COMPARACION, hastaId: 1, hasta: DOS[0].cargadaEn });
    await panel.whenStable();
  });
});
