import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // El cliente de Postgres y el SDK de Gemini solo se usan en el servidor.
  serverExternalPackages: ["postgres"],
};

export default nextConfig;
