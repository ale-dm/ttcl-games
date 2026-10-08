import { bloques, trozos } from './texto-rico';

describe('texto del Duende', () => {
  it('separa párrafos y listas', () => {
    const b = bloques('Esto es lo que trabajaría:\n\n- **Pocos headshots.** Mira a la cabeza.\n- Juega con el equipo.\n\nY punto.');
    expect(b.map((x) => x.tipo)).toEqual(['p', 'ul', 'p']);
    const lista = b[1];
    expect(lista.tipo === 'ul' && lista.items.length).toBe(2);
  });

  it('marca las negritas sin interpretar HTML', () => {
    expect(trozos('Tienes **1,25** de K/D <script>')).toEqual([
      { texto: 'Tienes ', negrita: false },
      { texto: '1,25', negrita: true },
      { texto: ' de K/D <script>', negrita: false },
    ]);
  });

  it('une las líneas sueltas de un mismo párrafo', () => {
    expect(bloques('Una línea\notra línea')).toEqual([
      { tipo: 'p', trozos: [{ texto: 'Una línea otra línea', negrita: false }] },
    ]);
  });
});
