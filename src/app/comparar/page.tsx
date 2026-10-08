import { listarEquipo } from "@/lib/datos";
import { compararDatos } from "@/lib/duende/servicio";
import { esJuego, JUEGOS, NOMBRE_JUEGO, type Juego } from "@/lib/juegos";
import { num } from "@/lib/formato";
import { PanelDuende } from "@/components/PanelDuende";

export const dynamic = "force-dynamic";

interface Props {
  searchParams: Promise<{ a?: string; b?: string; juego?: string }>;
}

export default async function PaginaComparar({ searchParams }: Props) {
  const { a, b, juego: juegoParam } = await searchParams;
  const equipo = await listarEquipo();
  const juego: Juego = esJuego(juegoParam) ? juegoParam : "cs2";

  const slugA = equipo.find((j) => j.slug === a) ? a! : equipo[0]?.slug;
  const slugB = equipo.find((j) => j.slug === b) ? b! : (equipo[1]?.slug ?? equipo[0]?.slug);

  const comparacion = slugA && slugB ? await compararDatos(slugA, slugB, juego) : null;
  const mismoJugador = slugA === slugB;

  return (
    <>
      <h1>Comparar</h1>
      <p className="muted">Dos miembros del equipo en el mismo juego.</p>

      <form className="filtros" method="GET">
        <label>
          Jugador A
          <select name="a" defaultValue={slugA}>
            {equipo.map((j) => (
              <option key={j.slug} value={j.slug}>
                {j.nombre}
              </option>
            ))}
          </select>
        </label>
        <label>
          Jugador B
          <select name="b" defaultValue={slugB}>
            {equipo.map((j) => (
              <option key={j.slug} value={j.slug}>
                {j.nombre}
              </option>
            ))}
          </select>
        </label>
        <label>
          Juego
          <select name="juego" defaultValue={juego}>
            {JUEGOS.map((j) => (
              <option key={j} value={j}>
                {NOMBRE_JUEGO[j]}
              </option>
            ))}
          </select>
        </label>
        <button type="submit">Comparar</button>
      </form>

      {mismoJugador && <p className="card">Elige dos jugadores distintos.</p>}

      {!mismoJugador && comparacion && !comparacion.filas && (
        <p className="card">
          Para comparar en {NOMBRE_JUEGO[juego]} los dos necesitan partidas guardadas en ese juego.
        </p>
      )}

      {!mismoJugador && comparacion?.filas && (
        <>
          <div className="card tabla-scroll">
            <table>
              <thead>
                <tr>
                  <th>Métrica</th>
                  <th className="num">{comparacion.a.nombre}</th>
                  <th className="num">{comparacion.b.nombre}</th>
                </tr>
              </thead>
              <tbody>
                {comparacion.filas.map((f) => (
                  <tr key={f.metrica}>
                    <td>{f.metrica}</td>
                    <td className={`num ${f.ventaja === "a" ? "gana" : ""}`}>{num(f.a)}</td>
                    <td className={`num ${f.ventaja === "b" ? "gana" : ""}`}>{num(f.b)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="muted" style={{ marginTop: 8 }}>
            En verde, quien va por delante en cada métrica (en muertes gana el que menos tiene).
          </p>

          <h2>Duende</h2>
          <PanelDuende
            titulo={`El Duende compara a ${comparacion.a.nombre} y ${comparacion.b.nombre}`}
            opciones={[
              {
                etiqueta: "Comparar en " + NOMBRE_JUEGO[juego],
                cuerpo: { tipo: "comparacion", slugA: slugA!, slugB: slugB!, juego },
              },
            ]}
          />
        </>
      )}
    </>
  );
}
