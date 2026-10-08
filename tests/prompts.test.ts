import { describe, expect, it } from "vitest";
import { hashDatos, promptComparacion, promptConsejos, promptPerfil } from "@/lib/duende/prompts";
import { resumirParticipaciones } from "@/lib/stats/resumen";

const resumen = (partidas: number) =>
  resumirParticipaciones(
    Array.from({ length: partidas }, (_, i) => ({
      juego: "cs2" as const,
      jugadaEn: new Date(Date.UTC(2026, 0, i + 1)),
      gano: i % 2 === 0,
      kills: 15,
      muertes: 12,
      asistencias: 3,
      datos: { adr: 70 },
    })),
  )[0]!;

describe("prompts del Duende", () => {
  it("el hash es estable y cambia cuando cambian los datos", () => {
    expect(hashDatos({ a: 1 })).toBe(hashDatos({ a: 1 }));
    expect(hashDatos({ a: 1 })).not.toBe(hashDatos({ a: 2 }));
  });

  it("incluye los datos como JSON y avisa si la muestra es pequeña", () => {
    const pocas = promptPerfil("Ale", "cs2", [resumen(2)]);
    expect(pocas).toContain('"winrate_pct"');
    expect(pocas).toContain("aún es pronto");

    const suficientes = promptPerfil("Ale", "cs2", [resumen(8)]);
    expect(suficientes).not.toContain("aún es pronto");
  });

  it("la comparación usa el aviso según la muestra más pequeña de las dos", () => {
    const texto = promptComparacion(
      { nombre: "Ale", resumen: resumen(8) },
      { nombre: "Bea", resumen: resumen(3) },
    );
    expect(texto).toContain("aún es pronto");
    expect(texto).toContain('"Ale"');
    expect(texto).toContain('"Bea"');
  });

  it("los consejos piden exactamente tres", () => {
    expect(promptConsejos("Ale", "cs2", resumen(8))).toContain("exactamente 3");
  });
});
