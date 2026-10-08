// Juegos del proyecto: nombres para la interfaz y los campos que se muestran en cada uno.
export const JUEGOS = ["cs2", "smite2"] as const;
export type Juego = (typeof JUEGOS)[number];

export const NOMBRE_JUEGO: Record<Juego, string> = {
  cs2: "Counter-Strike 2",
  smite2: "SMITE 2",
};

export function esJuego(valor: unknown): valor is Juego {
  return typeof valor === "string" && (JUEGOS as readonly string[]).includes(valor);
}
