/** Últimos resultados como cuadraditos: V verde, D rojo, ? gris. */
export function Forma({ forma }: { forma: string }) {
  if (!forma) return null;
  return (
    <span className="forma" aria-label={`Últimas partidas: ${forma}`}>
      {forma.split("").map((letra, i) => (
        <span key={i} className={letra}>
          {letra}
        </span>
      ))}
    </span>
  );
}
