# TTCL Games

Estadísticas del equipo TTCL en **CS2** y **SMITE 2**: una web para ver cada jugador, su detalle, compararlos, y
un worker que sincroniza las partidas. El **Duende** (Gemini) comenta los números: perfiles, comparaciones y consejos.

## Stack

| Parte | Tecnología |
|---|---|
| Web (frontend + API) | Next.js 16 (App Router) + React + TypeScript |
| Gráficas | Recharts |
| Worker de sincronización | TypeScript (`tsx`), mismo código que la web |
| Base de datos | PostgreSQL + Drizzle ORM |
| IA (Duende) | Gemini con `@google/genai`, solo desde el servidor |
| Tests | Vitest |

Sin frameworks extra: un solo proyecto, un worker y una base de datos.

## Estructura

```
src/
  app/                 páginas (equipo, jugador, comparar) y API del Duende
  components/          panel del Duende, gráfica, forma (V/D)
  db/                  esquema, conexión, migraciones y seed
  lib/
    sources/           adaptadores de FACEIT (CS2) y Hi-Rez (SMITE 2) con un contrato común
    stats/resumen.ts   cálculo de winrate, K/D, medias y comparativas (funciones puras)
    duende/            personalidad, prompts, caché por hash de datos y límite diario
    gemini.ts          cliente de Gemini: timeout, cuota y modelos de respaldo
  worker/              sincronización de partidas
drizzle/               migraciones SQL generadas
config/                equipo.example.json (la copia real no va al repo)
```

## Puesta en marcha

1. Instalar dependencias: `npm install`
2. Copiar `.env.example` a `.env` y rellenar, al menos `DATABASE_URL`.
3. Aplicar migraciones: `npm run db:migrate`
4. Cargar el equipo: copiar `config/equipo.example.json` a `config/equipo.json` con los nicks reales y ejecutar
   `npm run db:seed`. Es idempotente.
5. Sincronizar una vez: `npm run worker:once`. Para dejarlo corriendo: `npm run worker`
   (cada `SYNC_INTERVAL_MIN` minutos).
6. Web: `npm run dev` (o `npm run build && npm start`).

Comprobación completa: `npm run check` (typecheck, tests y formato).

## El Duende

- Nunca recibe partidas en bruto: recibe un **resumen ya calculado** (winrate, K/D, medias, forma reciente). Así no
  inventa números y gasta poca cuota.
- Los textos se **guardan** con un hash de los datos. Si las estadísticas no cambian, se devuelve el texto guardado.
- Límite de textos nuevos por 24 h (`DUENDE_DAILY_LIMIT`).
- Si el modelo principal (`GEMINI_MODEL`) ya no existe, prueba los de `GEMINI_FALLBACK_MODELS`. Un error de cuota o de
  red no cambia de modelo.
- La personalidad (`src/lib/duende/prompts.ts`) pica con las estadísticas, pero no entra en lo personal ni en lo
  hiriente de verdad.

## Fuentes de datos: estado

| Juego | Fuente | Estado |
|---|---|---|
| CS2 | FACEIT Data API | Implementada. Solo sirve para jugadores con cuenta de FACEIT: las partidas de CS2 no están expuestas por la API de Steam. El mapeo de campos está escrito a partir de la documentación y **hay que validarlo con una partida real**. |
| SMITE 2 | Hi-Rez API | Implementada **sin verificar**. Requiere dev ID y auth key de Hi-Rez. Hay que confirmar la URL base de SMITE 2 (`SMITE2_API_BASE`), los nombres de método y los campos de `getmatchhistory`. El cálculo de la firma sí está probado. |

Si una fuente no tiene credenciales, el worker simplemente no sincroniza ese juego.

## Estado del proyecto

Hecho: esquema, sincronización en dos pasadas (primero se resuelven los IDs, luego se piden partidas, así una partida
jugada por dos del equipo se guarda una vez con dos participaciones), páginas de equipo, detalle, comparación y Duende.

Pendiente:

- Validar FACEIT y Hi-Rez con cuentas reales (ver tabla de arriba).
- Pruebas de integración contra una base Postgres real para el worker.
- Despliegue (Vercel para la web, un servicio para el worker y Postgres).
