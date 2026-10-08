import { TestBed } from '@angular/core/testing';
import { I18n } from './i18n';
import { valorDe } from './metricas';
import { ResumenJuego } from './modelos';

describe('I18n', () => {
  let i18n: I18n;

  beforeEach(() => {
    localStorage.clear();
    i18n = TestBed.inject(I18n);
    i18n.cambiar('es');
  });

  it('traduce y sustituye marcadores', () => {
    expect(i18n.t('equipo.duende', { juego: 'CS2' })).toBe('El Duende · CS2');
    i18n.cambiar('en');
    expect(i18n.t('equipo.duende', { juego: 'CS2' })).toBe('The Duende · CS2');
    TestBed.tick(); // el atributo lang lo pone un effect
    expect(document.documentElement.lang).toBe('en');
  });

  it('recuerda el idioma elegido', () => {
    i18n.cambiar('en');
    expect(JSON.parse(localStorage.getItem('ttcl.prefs')!).lang).toBe('en');
  });

  it('formatea números al estilo de cada idioma', () => {
    expect(i18n.pct(37.14).replace(/\s/g, ' ')).toBe('37,1 %');
    expect(i18n.valor(1.2, 'dec')).toBe('1,20');
    expect(i18n.letraForma('V')).toBe('V');
    i18n.cambiar('en');
    expect(i18n.pct(37.14)).toBe('37.1%');
    expect(i18n.valor(1.2, 'dec')).toBe('1.20');
    expect(i18n.letraForma('V')).toBe('W');
    expect(i18n.delta(-3.4, 'pct')).toBe('−3.4 pp');
    expect(i18n.valor(null, 'pct')).toBe('—');
  });

  it('traduce los roles y deja tal cual uno que no conoce', () => {
    expect(i18n.rol('soporte')).toBe('Soporte');
    expect(i18n.rol('guardian')).toBe('Guardián');
    i18n.cambiar('en');
    expect(i18n.rol('soporte')).toBe('Support');
    expect(i18n.rol('jungla')).toBe('Jungle');
    expect(i18n.rol('francotirador')).toBe('francotirador');
  });
});

describe('valorDe', () => {
  const resumen: ResumenJuego = {
    juego: 'cs2',
    partidas: 10,
    victorias: 6,
    derrotas: 4,
    winrate: 60,
    kd: 1.1,
    killsMedia: 18,
    muertesMedia: 16,
    asistenciasMedia: 4,
    datosMedios: { adr: 82 },
    forma: 'VVDVD',
    ultimaPartida: null,
  };

  it('lee las métricas comunes y las del juego por la misma clave que la API', () => {
    expect(valorDe(resumen, 'winrate')).toBe(60);
    expect(valorDe(resumen, 'muertes_media')).toBe(16);
    expect(valorDe(resumen, 'adr')).toBe(82);
    expect(valorDe(resumen, 'hs_pct')).toBeNull();
    expect(valorDe(null, 'kd')).toBeNull();
  });
});
