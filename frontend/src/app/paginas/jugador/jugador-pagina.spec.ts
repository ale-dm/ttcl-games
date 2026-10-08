import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { I18n } from '../../core/i18n';
import { JugadorVista } from '../../core/modelos';
import { JugadorPagina } from './jugador-pagina';

// Sin resúmenes, la página no pide detalle, consejos ni partidas: solo el perfil.
const BEA: JugadorVista = {
  slug: 'j2',
  nombre: 'Bea',
  demo: false,
  cuentas: [
    { juego: 'cs2', nick: 'bea_faceit', rol: 'soporte', ultimaSync: null },
    { juego: 'smite2', nick: 'BeaSmite', rol: null, ultimaSync: null },
  ],
  resumenes: [],
};

describe('JugadorPagina', () => {
  let http: HttpTestingController;
  let i18n: I18n;
  let pagina: ComponentFixture<JugadorPagina>;

  beforeEach(async () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    i18n = TestBed.inject(I18n);
    i18n.cambiar('es');
    http = TestBed.inject(HttpTestingController);

    pagina = TestBed.createComponent(JugadorPagina);
    pagina.componentRef.setInput('slug', 'j2');
    await pagina.whenStable();
    http.expectOne('/api/jugadores/j2').flush(BEA);
    await pagina.whenStable();
  });

  afterEach(() => http.verify());

  const texto = (el: Element | null) => el?.textContent!.replace(/\s+/g, ' ').trim() ?? null;
  const cuentas = () =>
    [...(pagina.nativeElement as HTMLElement).querySelectorAll('.acct')].map((c) => ({
      nick: texto(c.querySelector('b')),
      rol: texto(c.querySelector('.acct-rol')),
    }));

  it('enseña el rol junto al nick de cada juego, si lo ha dicho', () => {
    // El lector de pantalla oye "Rol en Counter-Strike 2: Soporte"; a la vista queda "Soporte".
    expect(cuentas()).toEqual([
      { nick: 'bea_faceit', rol: 'Rol en Counter-Strike 2: Soporte' },
      { nick: 'BeaSmite', rol: null },
    ]);
    const rol = (pagina.nativeElement as HTMLElement).querySelector('.acct-rol')!;
    expect(rol.getAttribute('title')).toBe('Rol en Counter-Strike 2');
  });

  it('traduce el rol al cambiar de idioma', async () => {
    i18n.cambiar('en');
    await pagina.whenStable();
    expect(cuentas()[0]).toEqual({ nick: 'bea_faceit', rol: 'Role in Counter-Strike 2: Support' });
  });
});
