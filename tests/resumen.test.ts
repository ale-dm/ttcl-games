import { describe, expect, it } from "vitest";
import { compararResumenes, resumirParticipaciones, type ParticipacionFila } from "@/lib/stats/resumen";

const fila = (p: Partial<ParticipacionFila> & { dia: number }): ParticipacionFila => ({
  juego: "cs2",
  jugadaEn: new Date(Date.UTC(2026, 0, p.dia)),
  gano: null,
  kills: null,
  muertes: null,
  asistencias: null,
  datos: {},
  ...p,
});

describe("resumirParticipaciones", () => {
  it("calcula winrate, K/D y medias sobre las partidas con datos", () => {
    const filas = [
      fila({ dia: 1, gano: true, kills: 20, muertes: 10, asistencias: 4 }),
      fila({ dia: 2, gano: false, kills: 10, muertes: 15, asistencias: 2 }),
      fila({ dia: 3, gano: true, kills: 25, muertes: 15, asistencias: 6 }),
    ];
    const [r] = resumirParticipaciones(filas);
    expect(r!.partidas).toBe(3);
    expect(r!.victorias).toBe(2);
    expect(r!.derrotas).toBe(1);
    expect(r!.winrate).toBe(66.7);
    expect(r!.kd).toBe(1.38); // 55 kills / 40 muertes = 1.375
    expect(r!.killsMedia).toBe(18.33);
    expect(r!.asistenciasMedia).toBe(4);
  });

  it("no inventa winrate si ninguna partida tiene resultado", () => {
    const [r] = resumirParticipaciones([fila({ dia: 1, kills: 5, muertes: 5 })]);
    expect(r!.winrate).toBeNull();
    expect(r!.forma).toBe("?");
  });

  it("ordena la forma de más reciente a más antigua y la limita a 10", () => {
    const filas = Array.from({ length: 12 }, (_, i) => fila({ dia: i + 1, gano: i % 2 === 0 }));
    const [r] = resumirParticipaciones(filas);
    expect(r!.forma).toHaveLength(10);
    expect(r!.forma.startsWith("V")).toBe(false); // el día 12 (i=11) perdió
    expect(r!.forma[0]).toBe("D");
    expect(r!.ultimaPartida?.getUTCDate()).toBe(12);
  });

  it("separa por juego y calcula medias de datos específicos ignorando los no numéricos", () => {
    const filas = [
      fila({ dia: 1, juego: "cs2", datos: { adr: 80, mapa: "de_mirage" } }),
      fila({ dia: 2, juego: "cs2", datos: { adr: 60, mapa: null } }),
      fila({ dia: 3, juego: "smite2", gano: true, kills: 8, muertes: 2 }),
    ];
    const resumenes = resumirParticipaciones(filas);
    expect(resumenes.map((r) => r.juego)).toEqual(["cs2", "smite2"]);
    expect(resumenes[0]!.datosMedios).toEqual({ adr: 70 });
  });

  it("filtra por fecha cuando se pasa `desde`", () => {
    const filas = [fila({ dia: 1, gano: false }), fila({ dia: 10, gano: true })];
    const [r] = resumirParticipaciones(filas, { desde: new Date(Date.UTC(2026, 0, 5)) });
    expect(r!.partidas).toBe(1);
    expect(r!.winrate).toBe(100);
  });
});

describe("compararResumenes", () => {
  it("marca la ventaja respetando que en muertes gana el que menos tiene", () => {
    const [a] = resumirParticipaciones([fila({ dia: 1, gano: true, kills: 20, muertes: 10 })]);
    const [b] = resumirParticipaciones([fila({ dia: 1, gano: false, kills: 12, muertes: 8 })]);
    const filas = compararResumenes(a!, b!);
    const porMetrica = Object.fromEntries(filas.map((f) => [f.metrica, f.ventaja]));
    expect(porMetrica["Winrate (%)"]).toBe("a");
    expect(porMetrica["Kills por partida"]).toBe("a");
    expect(porMetrica["Muertes por partida"]).toBe("b");
    expect(porMetrica["Partidas"]).toBeNull();
  });
});
