// Formato para mostrar números y fechas en español.
export function num(valor: number | null | undefined, decimales = 2): string {
  if (valor === null || valor === undefined || !Number.isFinite(valor)) return "—";
  return valor.toLocaleString("es-ES", { maximumFractionDigits: decimales });
}

export function pct(valor: number | null | undefined): string {
  return valor === null || valor === undefined ? "—" : `${num(valor, 1)} %`;
}

export function fecha(valor: Date | null | undefined): string {
  if (!valor) return "—";
  return valor.toLocaleDateString("es-ES", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    timeZone: "Europe/Madrid",
  });
}

export function fechaCorta(valor: Date): string {
  return valor.toLocaleDateString("es-ES", { day: "2-digit", month: "2-digit", timeZone: "Europe/Madrid" });
}
