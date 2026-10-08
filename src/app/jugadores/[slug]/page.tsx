import Link from "next/link";
import { notFound } from "next/navigation";
import { historialDe, jugadorPorSlug } from "@/lib/datos";
import { NOMBRE_JUEGO, type Juego } from "@/lib/juegos";
import { fecha, fechaCorta, num, pct } from "@/lib/formato";
import { Forma } from "@/components/Forma";
import { GraficaPartidas } from "@/components/GraficaPartidas";
import { PanelDuende } from "@/components/PanelDuende";

export const dynamic = "force-dynamic";

export default async function PaginaJugador({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params;
  const jugador = await jugadorPorSlug(slug);
  if (!jugador) notFound();

  const historial = await historialDe(jugador.id, 60);
  const juegosConDatos = jugador.resumenes.map((r) => r.juego);

  const opcionesDuende = [
    { etiqueta: "Comentar su rendimiento", cuerpo: { tipo: "perfil", slug: jugador.slug, juego: "todos" } },
    ...juegosConDatos.map((juego: Juego) => ({
      etiqueta: `Consejos de ${NOMBRE_JUEGO[juego]}`,
      cuerpo: { tipo: "consejos", slug: jugador.slug, juego },
    })),
  ];

  return (
    <>
      <p className="muted">
        <Link href="/">← Equipo</Link>
      </p>
      <h1>{jugador.nombre}</h1>
      <p className="muted">
        {jugador.cuentas
          .map((c) => `${NOMBRE_JUEGO[c.juego]}: ${c.nombreExterno} (última sync ${fecha(c.ultimaSync)})`)
          .join(" · ")}
      </p>

      {jugador.resumenes.length === 0 && (
        <p className="card">Aún no hay partidas guardadas para este jugador.</p>
      )}

      {jugador.resumenes.map((r) => {
        const partidasJuego = historial.filter((h) => h.juego === r.juego).reverse();
        return (
          <section key={r.juego}>
            <h2>{NOMBRE_JUEGO[r.juego]}</h2>
            <div className="card">
              <div className="stats">
                <div className="stat">
                  <div className="valor">{r.partidas}</div>
                  <div className="etiqueta">partidas</div>
                </div>
                <div className="stat">
                  <div className="valor">
                    {r.victorias} / {r.derrotas}
                  </div>
                  <div className="etiqueta">victorias / derrotas</div>
                </div>
                <div className="stat">
                  <div className="valor">{pct(r.winrate)}</div>
                  <div className="etiqueta">winrate</div>
                </div>
                <div className="stat">
                  <div className="valor">{num(r.kd)}</div>
                  <div className="etiqueta">K/D</div>
                </div>
                <div className="stat">
                  <div className="valor">{num(r.killsMedia, 1)}</div>
                  <div className="etiqueta">kills / partida</div>
                </div>
                <div className="stat">
                  <div className="valor">{num(r.muertesMedia, 1)}</div>
                  <div className="etiqueta">muertes / partida</div>
                </div>
                <div className="stat">
                  <div className="valor">{num(r.asistenciasMedia, 1)}</div>
                  <div className="etiqueta">asistencias / partida</div>
                </div>
              </div>
              {Object.keys(r.datosMedios).length > 0 && (
                <p className="muted" style={{ marginTop: 12 }}>
                  Medias específicas:{" "}
                  {Object.entries(r.datosMedios)
                    .map(([clave, valor]) => `${clave}: ${num(valor, 1)}`)
                    .join(" · ")}
                </p>
              )}
              <p style={{ marginTop: 12 }}>
                Forma reciente: <Forma forma={r.forma} />
              </p>
            </div>

            <h2>Kills y muertes por partida</h2>
            <div className="card">
              <GraficaPartidas
                datos={partidasJuego.map((p) => ({
                  fecha: fechaCorta(p.jugadaEn),
                  kills: p.kills,
                  muertes: p.muertes,
                }))}
              />
            </div>

            <h2>Últimas partidas</h2>
            <div className="card tabla-scroll">
              <table>
                <thead>
                  <tr>
                    <th>Fecha</th>
                    <th>Modo</th>
                    <th>Resultado</th>
                    <th className="num">K</th>
                    <th className="num">D</th>
                    <th className="num">A</th>
                    <th>Con</th>
                  </tr>
                </thead>
                <tbody>
                  {partidasJuego
                    .slice()
                    .reverse()
                    .slice(0, 20)
                    .map((p) => (
                      <tr key={p.partidaId}>
                        <td>{fecha(p.jugadaEn)}</td>
                        <td>{p.modo ?? "—"}</td>
                        <td className={p.gano === true ? "gana" : p.gano === false ? "pierde" : ""}>
                          {p.gano === true ? "Victoria" : p.gano === false ? "Derrota" : "—"}
                        </td>
                        <td className="num">{p.kills ?? "—"}</td>
                        <td className="num">{p.muertes ?? "—"}</td>
                        <td className="num">{p.asistencias ?? "—"}</td>
                        <td>{p.companeros.length ? p.companeros.join(", ") : "—"}</td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
          </section>
        );
      })}

      <h2>Duende</h2>
      <PanelDuende titulo={`El Duende sobre ${jugador.nombre}`} opciones={opcionesDuende} />

      <p className="muted" style={{ marginTop: 24 }}>
        <Link href={`/comparar?a=${jugador.slug}`}>Comparar con otro miembro →</Link>
      </p>
    </>
  );
}
