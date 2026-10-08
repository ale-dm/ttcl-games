import type { Metadata } from "next";
import Link from "next/link";
import "./globals.css";

export const metadata: Metadata = {
  title: "TTCL Games",
  description: "Estadísticas del equipo TTCL en CS2 y SMITE 2, con el Duende.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="es">
      <body>
        <header className="cabecera">
          <div className="contenedor">
            <Link href="/" className="marca">
              TTCL Games
            </Link>
            <nav>
              <Link href="/">Equipo</Link>
              <Link href="/comparar">Comparar</Link>
            </nav>
          </div>
        </header>
        <main>
          <div className="contenedor">{children}</div>
        </main>
      </body>
    </html>
  );
}
