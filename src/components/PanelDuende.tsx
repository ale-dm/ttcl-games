"use client";

import { useState } from "react";

export interface OpcionDuende {
  etiqueta: string;
  cuerpo: Record<string, string>;
}

/** Botones que piden un comentario al Duende y muestran el texto. La clave de Gemini solo la usa el servidor. */
export function PanelDuende({ titulo, opciones }: { titulo: string; opciones: OpcionDuende[] }) {
  const [texto, setTexto] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cargando, setCargando] = useState(false);
  const [origen, setOrigen] = useState<string | null>(null);

  async function pedir(opcion: OpcionDuende) {
    setCargando(true);
    setError(null);
    try {
      const respuesta = await fetch("/api/duende", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(opcion.cuerpo),
      });
      const datos = (await respuesta.json()) as {
        texto?: string;
        modelo?: string;
        desdeCache?: boolean;
        error?: string;
      };
      if (!respuesta.ok || !datos.texto) {
        setError(datos.error ?? "El Duende no ha podido contestar.");
        return;
      }
      setTexto(datos.texto);
      setOrigen(
        `${datos.modelo ?? "Gemini"}${datos.desdeCache ? " · guardado (las estadísticas no han cambiado)" : ""}`,
      );
    } catch {
      setError("No se pudo contactar con el servidor.");
    } finally {
      setCargando(false);
    }
  }

  return (
    <section className="card duende">
      <h2 style={{ marginTop: 0 }}>🧙 {titulo}</h2>
      <div className="acciones">
        {opciones.map((o) => (
          <button key={o.etiqueta} className="primario" disabled={cargando} onClick={() => pedir(o)}>
            {o.etiqueta}
          </button>
        ))}
      </div>
      {cargando && <p className="muted">El Duende está pensando…</p>}
      {error && <p className="error">{error}</p>}
      {texto && !cargando && (
        <>
          <p className="texto">{texto}</p>
          {origen && <p className="muted">{origen}</p>}
        </>
      )}
    </section>
  );
}
