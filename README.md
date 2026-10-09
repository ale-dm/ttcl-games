# TTCL Games

Estadísticas del equipo TTCL en **CS2** y **SMITE 2**, al estilo de [csstats.gg](https://csstats.gg), con **el Duende**:
un entrenador que mira los números de cada uno, dice en qué mejorar y contesta preguntas en un chat.

- **Equipo**: una tarjeta por jugador con winrate, K/D, partidas, forma reciente y lo primero que diría el Duende.
- **Perfil** (estilo csstats): cifras clave con la tendencia de las últimas 10 partidas, gráfica de kills y muertes,
  rendimiento por mapa (CS2) o por dios (SMITE 2), historial paginado y el panel del Duende con sus recomendaciones.
- **Comparar**: dos jugadores cara a cara, métrica a métrica.
- **Ranking**: el equipo ordenado por la métrica que elijas.
- **Buscador** de jugadores y nicks en la barra superior.
- **Chat del Duende** en toda la web; sabe de quién estás hablando según la página.
- **Modo claro y oscuro**, y **español e inglés** (se recuerdan en el navegador).

## Arquitectura

```
 Navegador ──► Angular (web) ──/api──► Java · Spring Boot (API) ──► Python · FastAPI (Duende) ──► Gemini (opcional)
                                          │
                                          ├─► PostgreSQL (H2 en memoria para desarrollar)
                                          └─► FACEIT (CS2) · Hi-Rez (SMITE 2)   ← sincronización programada
```

| Parte | Carpeta | Tecnología | Qué hace |
|---|---|---|---|
| Web | `frontend/` | Angular 21 (standalone, signals, sin zone.js) | Páginas, i18n ES/EN, tema claro/oscuro, chat |
| API | `api/` | Java 21 · Spring Boot 4.1 · JPA · Flyway | Datos, estadísticas, comparación, ranking, sincronización, pasarela al Duende |
| Duende | `duende/` | Python 3.12+ · FastAPI · google-genai | Motor de recomendaciones y chatbot |
| Base de datos | — | PostgreSQL 17 (H2 en local) | Jugadores, cuentas, partidas compartidas, participaciones |

El navegador solo habla con la API Java; el Duende no se expone fuera. La API calcula los resúmenes y se los pasa al
Duende: **el Duende nunca ve partidas en bruto**, así no se inventa números.

## Puesta en marcha en local (sin Docker)

Hace falta JDK 21 o superior, Python 3.12+ y Node 24. Sin claves de nada: la API genera **datos de ejemplo** y el
Duende contesta **con reglas** (sin IA). Tres terminales:

**1. Duende (Python)** — puerto 8000

```bash
cd duende
python -m venv .venv
.venv/Scripts/pip install -r requirements-dev.txt     # en Linux/macOS: .venv/bin/pip
.venv/Scripts/python -m uvicorn app.main:app --port 8000
```

Con `.env` en la raíz (ver abajo), añade `--env-file ../.env` para que use Gemini.

**2. API (Java)** — puerto 8080. H2 en memoria y datos de ejemplo; lee `.env` de la raíz si existe.

```bash
cd api
./mvnw spring-boot:run          # en Windows: mvnw.cmd spring-boot:run
```

**3. Web (Angular)** — http://localhost:4200 (el proxy manda `/api` a la 8080)

```bash
cd frontend
npm install
npm start
```

## Con Docker

```bash
cp .env.example .env     # y rellena lo que tengas
docker compose up --build
```

Web en http://localhost:4200, con Postgres y datos de ejemplo (`TTCL_DEMO=true`; ponlo a `false` con el equipo real).

## Equipo real y claves

- **Equipo**: copia `config/equipo.example.json` a `config/equipo.json` con los nicks reales. La API lo carga al
  arrancar (es idempotente) y, si existe, no genera datos de ejemplo. Ese fichero no va al repo.
- **Rol** (opcional, por juego): `"cs2": { "nick": "...", "rol": "soporte" }`. CS2: `entry`, `awp`, `soporte`,
  `lurker`, `igl`, `rifler`. SMITE 2: `solo`, `jungla`, `mid`, `guardian`, `carry`. El Duende juzga cada métrica según
  lo que pide el rol (a un soporte no le pide kills) y la web lo enseña junto al nick.
- **Claves**: copia `.env.example` a `.env`. `GOOGLE_API_KEY` activa Gemini en el chat; `FACEIT_API_KEY` y las de
  Hi-Rez activan la sincronización (cada `SYNC_INTERVAL_MIN` minutos). Sin clave, ese juego simplemente no se toca.
- **Zona horaria**: `TTCL_ZONA_HORARIA` (por defecto `Europe/Madrid`). Con ella se sabe a qué hora del día se jugó cada
  partida (mañana, tarde, noche o madrugada).

## Tests

| Parte | Comando | Qué cubre |
|---|---|---|
| Duende | `cd duende && .venv/Scripts/python -m pytest` | Reglas de recomendación (también por rol, por compañero, tilt y hora del día), chat por reglas (también "esta semana" o "este mes"), uso y caché de Gemini, API |
| API | `cd api && ./mvnw test` | Estadísticas (también sinergias, dúos y tríos, sesiones y franjas horarias), periodos, mapeo de FACEIT y Hi-Rez, carga del equipo con roles, API completa contra H2 con datos de ejemplo |
| Web | `cd frontend && npm test` | Texto del Duende, i18n y formatos, estado del chat, rol, "Con quién", "Cuándo juegas mejor" y el periodo en el perfil, el periodo en el ranking, dúos y tríos en el equipo |

## El Duende

**Recomendaciones** (`duende/app/insights.py`): un motor de reglas, sin IA, que compara cada métrica con la media del
resto del equipo y con una referencia de jugador medio (`metricas.py`). Además tiene reglas con más miga: kills que
no se convierten en victorias, rachas, tendencia de las últimas partidas, mapas o dioses que se atragantan, compañeros
con los que se gana más o menos, tilt en las sesiones largas y la mejor hora del día. Cada
recomendación lleva nivel (*mejorar ya*, *a vigilar*, *lo haces bien*), los números comparados en barras y un consejo
concreto. Textos en español e inglés (`textos.py`).

**Rol** (`AJUSTES_ROL` en `metricas.py`, la fuente de verdad de esta tabla): si el jugador ha dicho su rol en
`config/equipo.json`, cada métrica cuenta lo que pide ese rol. *No se le juzga*: nunca sale como algo a mejorar.
*Se le tolera*: hace falta ir el doble de lejos de lo normal para que avise (tolerar no es un pase libre). *Pesa más*:
avisa antes, lo reconoce antes como fortaleza y lo pone primero. La recomendación lo explica con una coletilla
("Justo lo que pide tu rol de soporte"). Sin rol, todo cuenta igual.

| CS2 | No se le juzga | Se le tolera | Pesa más |
|---|---|---|---|
| `entry` | — | Muertes / partida, K/D, clutches | Éxito de entrada |
| `awp` | % headshot | Asistencias / partida, daño de utilidad | Kills / ronda, éxito de entrada |
| `soporte` | Kills / partida | K/D, ADR, kills / ronda, éxito de entrada | Asistencias / partida, daño de utilidad |
| `lurker` | Asistencias / partida, éxito de entrada | Daño de utilidad | Clutches, kills / ronda |
| `igl` | — | Kills / partida, K/D, ADR, kills / ronda, % headshot | Winrate, daño de utilidad |
| `rifler` | — | — | ADR, kills / ronda |

| SMITE 2 | No se le juzga | Se le tolera | Pesa más |
|---|---|---|---|
| `solo` | — | Asistencias / partida | Daño mitigado |
| `jungla` | Daño mitigado | — | Kills / partida |
| `mid` | Daño mitigado | — | Daño / min |
| `guardian` | Kills / partida, daño / min, oro / min | K/D | Asistencias / partida, daño mitigado |
| `carry` | Daño mitigado | — | Daño / min, oro / min |

**Con quién** (sinergias): la API calcula el winrate de cada jugador con cada compañero del equipo y en el resto de sus
partidas, y cómo le va solo (mínimo 3 partidas por fila). Solo cuentan los del mismo bando: en FACEIT dos del equipo
pueden caer en bandos contrarios. Si con alguien gana 15 puntos más (o menos) que sin él, con al menos 5 partidas a cada
lado, el Duende lo dice ("Con Jugador 2 ganas el 51,0 % de 51 partidas; sin Jugador 2, el 21,4 %"). La web lo enseña en
la tarjeta "Con quién" del perfil y, en el equipo, el mejor dúo y el mejor trío de cada juego.

**Cuándo** (sesiones y tilt): una sesión son partidas seguidas, con menos de 45 minutos entre el final de una y el
principio de la siguiente. La API calcula el winrate en la 1ª, la 2ª y de la 3ª en adelante de cada sesión, en la
partida que sigue a una victoria o a una derrota, y por hora del día (mañana, tarde, noche y madrugada, en la zona del
equipo); cada fila, con el winrate del resto de partidas al lado. Con 15 sesiones o más y 10 partidas a cada lado, el
Duende avisa de tilt si desde la 3ª seguida (o tras perder) gana 15 puntos menos ("A partir de la 3ª partida seguida
ganas el 37,5 % (16 partidas); en las dos primeras, el 63,9 %"), y dice su mejor hora si en ella gana 20 puntos más
que el resto del día. La web lo enseña en la tarjeta "Cuándo juegas mejor" del perfil.

**Periodo**: el perfil, el cara a cara y el ranking tienen un selector de *7 días · 30 días · Todo* (en la URL,
`?periodo=7d`). Todo lo de la página cuenta solo esas partidas, también la media del equipo con la que se compara y las
recomendaciones del Duende. En la API, `?periodo=7d|30d|todo` en el perfil, el detalle, el historial, con quién,
cuándo, los consejos, el cara a cara y el ranking (por defecto, todo). Sin partidas en el periodo, los datos vienen
vacíos; el 404 es solo para quien nunca ha jugado a ese juego.

**Chat** (`chat.py`): con `GOOGLE_API_KEY` contesta Gemini, que recibe los resúmenes del equipo (con el rol de cada uno),
las sinergias, las sesiones, los últimos 7 y 30 días y las recomendaciones ya calculadas. Caché por petición, límite
diario (`DUENDE_DAILY_LIMIT`) y modelos de respaldo si el principal ya no existe. Sin clave, sin cuota o si Gemini
falla, contestan las reglas (`reglas_chat.py`): en qué mejorar, qué haces bien, cómo vas últimamente, peor mapa o dios,
con quién juegas mejor, cuándo juegas mejor (tilt y hora), el mejor dúo, comparar a dos, quién es el mejor del equipo.
Si la pregunta dice "esta semana" o "este mes" (o la página tiene un periodo elegido), los números son los de esos
días, y "¿cómo voy esta semana?" los compara con los de siempre. La web indica bajo cada respuesta si la escribió
Gemini o las reglas.

**Personalidad** (`personalidad.py`): pica con las estadísticas como un colega del grupo, pero nunca entra en lo
personal (aspecto, familia, origen, salud, dinero…). Solo se mete con lo que pasa en el juego.

## Diseño

Parte del prototipo `TTCL Stats.html` y lo lleva a una web de estadísticas completa:

- **Base visual**: Geist y Geist Mono, colores en OKLCH, tarjetas con borde fino. Oscuro por defecto (o el del
  sistema) y claro con contraste cuidado. Cifras con números tabulares para que no bailen.
- **Color con significado**: verde y rojo solo para victoria/derrota y mejor/peor; el violeta es del Duende (botones,
  cara, barras de "tú"). Así se reconoce de un vistazo qué es dato y qué es consejo.
- **Estructura tipo csstats**: buscador siempre a mano, perfil con fila de cifras clave + tendencia, gráfica por
  partida, desglose por mapa/dios con mejor y peor marcados, con quién y cuándo juega mejor e historial. Ranking, cara
  a cara y los mejores dúos y tríos del equipo. Perfil, cara a cara y ranking, por periodo (7 días, 30 días o todo).
- **El Duende integrado, no escondido**: panel de recomendaciones junto a las estadísticas (fijo al hacer scroll en
  escritorio), consejo destacado en cada tarjeta del equipo, y el chat en un panel lateral con preguntas sugeridas
  según la página.
- **Móvil**: secciones en una segunda fila, todo a una columna, cifras en dos columnas y el botón del Duende solo con
  su cara.
- **Accesibilidad**: foco visible, etiquetas para lectores de pantalla, `prefers-reduced-motion` y textos del Duende
  pintados como texto (nunca como HTML).

Siguientes pasos: la hoja de ruta está en [docs/propuestas.md](docs/propuestas.md) (objetivos, memoria de consejos,
percentiles por nivel de FACEIT, Duende en Discord, análisis de demos…). Lo ya entregado, con lo que cambia en cada
servicio y cómo actualizar, está en [CHANGELOG.md](CHANGELOG.md).

## Fuentes de datos: estado

| Juego | Fuente | Estado |
|---|---|---|
| CS2 | FACEIT Data API (`api/.../sync/FaceitFuente.java`) | Implementada. Solo para jugadores con cuenta de FACEIT (la API de Steam no da partidas de CS2). Los campos (ADR, HS %, Entry, 1vX, Utility Damage…) siguen la documentación: **hay que validarlos con una partida real**. |
| SMITE 2 | Hi-Rez API (`HirezFuente.java`) | Implementada **sin verificar**: confirmar la URL base de SMITE 2 (`SMITE2_API_BASE`), los métodos y los campos de `getmatchhistory`. La firma sí está probada. |

La sincronización va en dos pasadas: primero resuelve los IDs de todas las cuentas y después pide partidas, así una
partida jugada por dos del equipo se guarda una vez con dos participaciones.

## Notas

- **Angular 21 y no 22**: Angular 22 pide Node ≥ 24.15. Con Node actualizado se sube con
  `npx ng update @angular/core@22 @angular/cli@22` (el código ya usa la API moderna: signals, `input()`, control flow).
- **Windows con tildes o eñes en el usuario**: la JDK no puede abrir sockets si la carpeta temporal real tiene
  caracteres fuera de ASCII (`C:\Users\JavierGalvañ\…`) y Tomcat no arranca. `CarpetaTemporal` lo resuelve sola al
  arrancar usando `C:\Users\Public\ttcl-tmp`.
- **Versión anterior**: la primera versión era un solo proyecto Next.js con Drizzle; se sustituyó por esta arquitectura
  (está en el historial de git, commit `36bf25f`).

## Estructura

```
frontend/src/app/
  core/        API, i18n (textos ES/EN), tema, métricas, carga reactiva
  layout/      barra superior con buscador, idioma y tema
  duende/      estado del chat, panel lateral, panel de recomendaciones
  compartido/  avatar, forma (V/D), gráfica SVG
  paginas/     equipo, jugador, comparar, ranking
api/src/main/java/com/ttcl/games/
  dominio/     entidades JPA y repositorios
  stats/       cálculos puros (resumen, medias del equipo, desglose, comparación)
  servicio/    consultas para la web
  duende/      cliente HTTP del Duende y casos de uso
  sync/        FACEIT, Hi-Rez y sincronizador
  carga/       equipo desde config/equipo.json y datos de ejemplo
  web/         controladores REST
duende/app/    insights (reglas), chat, reglas del chat, Gemini, textos y métricas
config/        equipo.example.json
```
