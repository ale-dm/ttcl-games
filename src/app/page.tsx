import Link from "next/link";
import { listarEquipo } from "@/lib/datos";
import { NOMBRE_JUEGO, JUEGOS } from "@/lib/juegos";
import { fecha, num, pct } from "@/lib/formato";
import { Forma } from "@/components/Forma";

export const dynamic = "force-dynamic";

export default async function PaginaEquipo() {
  const equipo = await listarEquipo();

  return (
    <>
      <h1>Equipo TTCL</h1>
      <p className="muted">
        Estadísticas de partidas guardadas. Se actualizan con el worker de sincronización.{" "}
        <Link href="/comparar">Comparar dos jugadores →</Link>
      </p>

      {equipo.length === 0 && (
        <p className="card">
          Todavía no hay jugadores. Copia <code>config/equipo.example.json</code> a{" "}
          <code>config/equipo.json</code> y ejecuta <code>npm run db:seed</code>.
        </p>
      )}

      <div className="grid" style={{ marginTop: 16 }}>
        {equipo.map((j) => (
          <Link
            key={j.slug}
            href={`/jugadores/${j.slug}`}
            className="card"
            style={{ textDecoration: "none", color: "inherit" }}
          >
            <strong style={{ fontSize: "1.1rem" }}>{j.nombre}</strong>
            {JUEGOS.map((juego) => {
              const r = j.resumenes.find((x) => x.juego === juego);
              const cuenta = j.cuentas.find((c) => c.juego === juego);
              if (!cuenta) return null;
              return (
                <div key={juego} style={{ marginTop: 12 }}>
                  <div className="muted">
                    {NOMBRE_JUEGO[juego]} · {cuenta.nombreExterno}
                  </div>
                  {r ? (
                    <div className="stats" style={{ marginTop: 6 }}>
                      <div className="stat">
                        <div className="valor">{pct(r.winrate)}</div>
                        <div className="etiqueta">winrate</div>
                      </div>
                      <div className="stat">
                        <div className="valor">{num(r.kd)}</div>
                        <div className="etiqueta">K/D</div>
                      </div>
                      <div className="stat">
                        <div className="valor">{r.partidas}</div>
                        <div className="etiqueta">partidas</div>
                      </div>
                    </div>
                  ) : (
                    <p className="muted">Sin partidas todavía.</p>
                  )}
                  {r && (
                    <div style={{ marginTop: 8 }}>
                      <Forma forma={r.forma} />
                      <span className="muted"> · última: {fecha(r.ultimaPartida)}</span>
                    </div>
                  )}
                </div>
              );
            })}
          </Link>
        ))}
      </div>
      <p className="muted" style={{ marginTop: 24 }}>
        Juegos disponibles: {JUEGOS.map((j) => NOMBRE_JUEGO[j]).join(" · ")}.
      </p>
    </>
  );
}
