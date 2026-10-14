import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { I18n } from '../../core/i18n';
import { CampoCaptura, CapturaVista, EvolucionCapturas, LecturaCaptura } from '../../core/modelos';
import { PanelEvolucion } from './panel-evolucion';

const CAMPOS: CampoCaptura[] = [
  { clave: 'rating', grupo: 'general', tipo: 'valor', formato: 'dec', mejor: 'alto' },
  { clave: 'kd', grupo: 'general', tipo: 'valor', formato: 'dec', mejor: 'alto' },
  { clave: 'adr', grupo: 'general', tipo: 'valor', formato: 'int', mejor: 'alto' },
  { clave: 'partidas', grupo: 'totales', tipo: 'total', formato: 'int', mejor: 'neutral' },
  { clave: 'muertes', grupo: 'totales', tipo: 'total', formato: 'int', mejor: 'bajo' },
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
  captura(1, '2026-09-01T20:00:00Z', { kd: 0.6, adr: 57, partidas: 344, muertes: 5419 }),
  captura(2, '2026-09-15T20:00:00Z', { kd: 0.7, adr: 55, partidas: 364, muertes: 5700 }),
];
const EVOLUCION: EvolucionCapturas = {
  desdeId: 1,
  desde: DOS[0].cargadaEn,
  hastaId: 2,
  hasta: DOS[1].cargadaEn,
  dias: 14,
  filas: [
    { clave: 'kd', grupo: 'general', tipo: 'valor', formato: 'dec', antes: 0.6, despues: 0.7, diferencia: 0.1, mejora: 1 },
    { clave: 'adr', grupo: 'general', tipo: 'valor', formato: 'int', antes: 57, despues: 55, diferencia: -2, mejora: -1 },
    { clave: 'rating', grupo: 'general', tipo: 'valor', formato: 'dec', antes: 0.74, despues: null, diferencia: null, mejora: 0 },
  ],
  tramo: {
    partidas: 20,
    victorias: 11,
    derrotas: 9,
    empates: 0,
    winrate: 55,
    kd: 0.85,
    hsPct: null,
    adr: 62,
    killsPartida: 12.8,
    asistenciasPartida: 4.5,
    clutch1v1: null,
  },
};

describe('PanelEvolucion (P13)', () => {
  let http: HttpTestingController;
  let panel: ComponentFixture<PanelEvolucion>;
  let el: HTMLElement;
  afterEach(() => http.verify());

  /** El panel de Ana con las capturas que se le pasen. jsdom no tiene URL.createObjectURL: se pone a mano. */
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
    el = panel.nativeElement as HTMLElement;
  }

  const texto = (e: Element | null) => e?.textContent!.replace(/\s+/g, ' ').trim() ?? null;
  const casilla = (clave: string) => el.querySelector<HTMLInputElement>(`input[data-clave="${clave}"]`)!;
  const boton = (t: string) => [...el.querySelectorAll('button')].find((b) => texto(b) === t)!;

  /** Elige un archivo como si se hubiera escogido en el selector. */
  async function elegir(archivo: File): Promise<void> {
    const entrada = el.querySelector<HTMLInputElement>('input[type="file"]')!;
    Object.defineProperty(entrada, 'files', { value: [archivo], configurable: true });
    entrada.dispatchEvent(new Event('change'));
    await panel.whenStable();
  }

  it('sube una captura: el Duende la lee, se revisa y se guarda con su imagen', async () => {
    await abrir([]);
    expect(texto(el.querySelector('.evo-vacio'))).toBe(
      'Aún no hay ninguna captura de Ana. Sube la primera y, dentro de unos días, otra para comparar.',
    );

    const imagen = new File(['png'], 'csstats.png', { type: 'image/png' });
    await elegir(imagen);
    const leer = http.expectOne('/api/jugadores/j1/capturas/leer');
    expect(leer.request.method).toBe('POST');
    expect((leer.request.body as FormData).get('imagen')).toBeInstanceOf(Blob);
    const lectura: LecturaCaptura = {
      origen: 'gemini',
      modelo: 'gemini-2.5-flash-lite',
      motivo: null,
      datos: { kd: 0.6, partidas: 344 },
      mapas: [{ mapa: 'de_inferno', partidas: 109, winrate: 48 }],
    };
    leer.flush(lectura);
    await panel.whenStable();

    expect(texto(el.querySelector('.evo-estado'))).toBe('Leída por Gemini: revisa los números antes de guardar.');
    expect(casilla('kd').value).toBe('0.6');
    expect(casilla('partidas').value).toBe('344');
    expect(casilla('adr').value).toBe('');
    // Lo que se corrige a mano es lo que se guarda.
    casilla('adr').value = '57';
    casilla('adr').dispatchEvent(new Event('input'));
    await panel.whenStable();

    boton('Guardar captura').click();
    const guardar = http.expectOne((r) => r.method === 'POST' && r.url === '/api/jugadores/j1/capturas');
    const cuerpo = guardar.request.body as FormData;
    expect(JSON.parse(await (cuerpo.get('datos') as Blob).text())).toEqual({
      datos: { kd: 0.6, partidas: 344, adr: 57 },
      mapas: [{ mapa: 'de_inferno', partidas: 109, winrate: 48 }],
      leidaPor: 'gemini',
    });
    expect(cuerpo.get('imagen')).toBeInstanceOf(Blob);
    guardar.flush(captura(1, '2026-09-01T20:00:00Z', { kd: 0.6, partidas: 344, adr: 57 }));
    await panel.whenStable();

    // Se vuelve a pedir la lista y el formulario se cierra.
    http.expectOne('/api/jugadores/j1/capturas').flush([captura(1, '2026-09-01T20:00:00Z', { kd: 0.6, partidas: 344 })]);
    await panel.whenStable();
    expect(el.querySelector('.evo-nueva')).toBeNull();
    expect(texto(el.querySelector('.evo-item .num'))).toBe('Partidas 344 · K/D 0,60');
    expect(el.querySelector('.evo-item a')!.getAttribute('href')).toBe('/api/jugadores/j1/capturas/1/imagen');
  });

  it('sin clave de Gemini, se rellena a mano: sin números no se puede guardar', async () => {
    await abrir([]);
    await elegir(new File(['webp'], 'csstats.webp', { type: 'image/webp' }));
    http
      .expectOne('/api/jugadores/j1/capturas/leer')
      .flush({ origen: 'ninguno', modelo: null, motivo: 'sin_clave', datos: {}, mapas: [] });
    await panel.whenStable();

    expect(texto(el.querySelector('.evo-estado'))).toBe('Sin clave de Gemini: escribe los números a mano.');
    expect(boton('Guardar captura').disabled).toBe(true);
    casilla('kd').value = '0.6';
    casilla('kd').dispatchEvent(new Event('input'));
    await panel.whenStable();
    expect(boton('Guardar captura').disabled).toBe(false);

    boton('Cancelar').click();
    await panel.whenStable();
    expect(el.querySelector('.evo-nueva')).toBeNull();
  });

  it('lo que no es una imagen válida ni se manda', async () => {
    await abrir([]);
    await elegir(new File(['gif'], 'csstats.gif', { type: 'image/gif' }));
    http.expectNone('/api/jugadores/j1/capturas/leer');
    expect(texto(el.querySelector('.evo-error'))).toBe('Tiene que ser una imagen PNG, JPEG o WebP de 8 MB como mucho.');
  });

  it('con dos capturas, la gráfica, lo jugado entre medias y la tabla con lo que sube y lo que baja', async () => {
    await abrir(DOS);
    // Sin elegir nada, compara la primera con la última.
    const comparar = http.expectOne((r) => r.url === '/api/jugadores/j1/capturas/comparar');
    expect(comparar.request.params.get('desde')).toBe('1');
    expect(comparar.request.params.get('hasta')).toBe('2');
    comparar.flush(EVOLUCION);
    await panel.whenStable();

    expect(el.querySelectorAll('.evo-grafica .evo-pt').length).toBe(2);
    expect([...el.querySelectorAll<HTMLOptionElement>('#evo-metrica option')].map((o) => o.value)).toEqual([
      'kd',
      'adr',
      'partidas',
      'muertes',
    ]);
    expect(texto(el.querySelector('.evo-comparar .card-h .small'))).toBe('14 días entre una y otra');
    expect(
      [...el.querySelectorAll('.evo-tramo .stat')].map((s) => [texto(s.querySelector('.v')), texto(s.querySelector('.l'))]),
    ).toEqual([
      ['20', 'Partidas · 11 ganadas · 9 perdidas'],
      ['55,0 %', 'Winrate'],
      ['0,85', 'K/D'],
      ['62', 'ADR'],
      ['12,80', 'Kills / partida'],
    ]);
    const filas = [...el.querySelectorAll('.evo-tabla tbody tr')];
    expect(filas.map((f) => [...f.querySelectorAll('td')].map((td) => texto(td)))).toEqual([
      ['K/D', '0,60', '0,70', '+0,10'],
      ['ADR', '57', '55', '−2'],
      ['Rating HLTV', '0,74', '—', '—'],
    ]);
    expect(filas[0].querySelector('td.sube')).not.toBeNull();
    expect(filas[1].querySelector('td.baja')).not.toBeNull();
    // La lista, la última primero.
    expect([...el.querySelectorAll('.evo-item .num')].map((n) => texto(n))).toEqual([
      'Partidas 364 · K/D 0,70 · ADR 55',
      'Partidas 344 · K/D 0,60 · ADR 57',
    ]);

    // Otra pareja: lo pide a la API.
    const desde = el.querySelector<HTMLSelectElement>('#evo-desde')!;
    desde.value = '2';
    desde.dispatchEvent(new Event('change'));
    await panel.whenStable();
    const otra = http.expectOne((r) => r.url === '/api/jugadores/j1/capturas/comparar');
    expect(otra.request.params.get('desde')).toBe('2');
    otra.flush({ ...EVOLUCION, desdeId: 2, dias: 0, tramo: null });
    await panel.whenStable();
    expect(texto(el.querySelector('.evo-comparar .card-b > p'))).toBe('Entre esas dos capturas no hay partidas nuevas.');
    expect(el.querySelector('.evo-tramo')).toBeNull();
  });
});
