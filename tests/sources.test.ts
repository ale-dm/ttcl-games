import { createHash } from "crypto";
import { describe, expect, it } from "vitest";
import { formatoTimestampHirez, firmaHirez, mapearFilaHistorial } from "@/lib/sources/hirez";
import { mapearEstadisticasFaceit } from "@/lib/sources/faceit";

describe("SMITE 2 (Hi-Rez)", () => {
  it("formatea el timestamp en UTC como yyyyMMddHHmmss", () => {
    expect(formatoTimestampHirez(new Date(Date.UTC(2026, 2, 5, 7, 9, 3)))).toBe("20260305070903");
  });

  it("la firma es MD5 de devId + método + authKey + timestamp", () => {
    // MD5("devid" + "createsession" + "authkey" + "20260101000000")
    expect(firmaHirez("devid", "createsession", "authkey", "20260101000000")).toBe(
      createHash("md5").update("devidcreatesessionauthkey20260101000000").digest("hex"),
    );
  });

  it("traduce una fila del historial a una participación del jugador consultado", () => {
    const partida = mapearFilaHistorial(
      {
        Match: 123,
        Win_Status: "Winner",
        Kills: "7",
        Deaths: "2",
        Assists: "5",
        Damage: "15000",
        God: "Thor",
        Queue: "Conquest",
      },
      "999",
    );
    expect(partida.externalId).toBe("123");
    expect(partida.participaciones).toHaveLength(1);
    expect(partida.participaciones[0]).toMatchObject({
      externalPlayerId: "999",
      gano: true,
      kills: 7,
      muertes: 2,
      asistencias: 5,
    });
    expect(partida.participaciones[0]!.datos).toMatchObject({ dios: "Thor", dano: 15000 });
  });
});

// Forma de la respuesta de FACEIT supuesta a partir de la documentación; comprobar con una partida real al conectar.
describe("CS2 (FACEIT)", () => {
  it("convierte las estadísticas de una partida en participaciones de los 10 jugadores", () => {
    const { mapa, participaciones } = mapearEstadisticasFaceit({
      rounds: [
        {
          round_stats: { Map: "de_mirage" },
          teams: [
            {
              players: [
                {
                  player_id: "p1",
                  player_stats: {
                    Kills: "24",
                    Deaths: "13",
                    Assists: "4",
                    Result: "1",
                    "K/D Ratio": "1.85",
                    "Headshots %": "45",
                    MVPs: "5",
                  },
                },
              ],
            },
            {
              players: [
                {
                  player_id: "p2",
                  player_stats: { Kills: "10", Deaths: "20", Assists: "1", Result: "0" },
                },
              ],
            },
          ],
        },
      ],
    });
    expect(mapa).toBe("de_mirage");
    expect(participaciones).toHaveLength(2);
    expect(participaciones[0]).toMatchObject({
      externalPlayerId: "p1",
      gano: true,
      kills: 24,
      muertes: 13,
      asistencias: 4,
    });
    expect(participaciones[0]!.datos).toMatchObject({
      kd_ratio: 1.85,
      hs_pct: 45,
      mvps: 5,
      mapa: "de_mirage",
    });
    expect(participaciones[1]).toMatchObject({ externalPlayerId: "p2", gano: false });
    expect(participaciones[1]!.datos.adr).toBeNull();
  });
});
