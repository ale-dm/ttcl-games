"use client";

import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

export interface PuntoPartida {
  fecha: string;
  kills: number | null;
  muertes: number | null;
}

/** Kills y muertes por partida, de la más antigua a la más reciente. */
export function GraficaPartidas({ datos }: { datos: PuntoPartida[] }) {
  if (datos.length < 2)
    return <p className="muted">Hacen falta al menos dos partidas para dibujar la gráfica.</p>;
  return (
    <div className="grafica">
      <ResponsiveContainer width="100%" height="100%">
        <LineChart data={datos} margin={{ top: 8, right: 12, bottom: 0, left: -12 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="var(--borde)" />
          <XAxis dataKey="fecha" tick={{ fill: "var(--texto-suave)", fontSize: 11 }} />
          <YAxis tick={{ fill: "var(--texto-suave)", fontSize: 11 }} allowDecimals={false} />
          <Tooltip />
          <Legend />
          <Line
            type="monotone"
            dataKey="kills"
            name="Kills"
            stroke="var(--victoria)"
            strokeWidth={2}
            dot={false}
            connectNulls
          />
          <Line
            type="monotone"
            dataKey="muertes"
            name="Muertes"
            stroke="var(--derrota)"
            strokeWidth={2}
            dot={false}
            connectNulls
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}
