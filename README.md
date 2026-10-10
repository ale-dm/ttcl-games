# TTCL Games

Estadísticas del equipo TTCL en **CS2** y **SMITE 2**, al estilo de [csstats.gg](https://csstats.gg), con **el Duende**:
un entrenador que mira los números de cada uno, dice en qué mejorar y contesta preguntas en un chat.

- **Equipo**: una tarjeta por jugador con winrate, K/D, partidas, forma reciente y lo primero que diría el Duende.
- **Perfil** (estilo csstats): cifras clave con la tendencia de las últimas 10 partidas, gráfica de kills y muertes,
  rendimiento por mapa (CS2) o por dios (SMITE 2), historial paginado y el panel del Duende con sus recomendaciones.
  En CS2, el nivel y el ELO de FACEIT, y el Duende compara con lo normal en ese nivel.
- **Comparar**: dos jugadores cara a cara, métrica a métrica.
- **Ranking**: el equipo ordenado por la métrica que elijas.
- **Buscador** de jugadores y nicks en la barra superior.
- **Chat del Duende** en toda la web; sabe de quién estás hablando según la página.
- **Demos de CS2**: con la demo de cada partida, ronda a ronda: rating, KAST, trades, duelos de apertura, CT y T,
  economía y un **mapa de calor** de dónde muere cada uno, con las rondas de cada partida en el historial.
- **Comentario de cada partida** en el historial ("Tercera derrota seguida en Nuke", "tu mejor ADR del mes") y, si se
  quiere, en **Discord**: las partidas nuevas y el resumen de la semana.
- **Valoraciones**: 👍/👎 en cada recomendación y cada respuesta del Duende, para saber qué hay que mejorar de él.
- **Modo claro y oscuro**, y **español e inglés** (se recuerdan en el navegador).

## Arquitectura

```
 Navegador ──► Angular (web) ──/api──► Java · Spring Boot (API) ◄─► Python · FastAPI (Duende) ──► Gemini (opcional)
                                          │
                                          ├─► PostgreSQL (H2 en memoria para desarrollar)
                                          ├─► FACEIT (CS2) · Hi-Rez (SMITE 2)   ← sincronización programada
                                          └─► Python · FastAPI (análisis de demos) ◄── demos de FACEIT o de demos/
```

| Parte | Carpeta | Tecnología | Qué hace |
|---|---|---|---|
| Web | `frontend/` | Angular 21 (standalone, signals, sin zone.js) | Páginas, i18n ES/EN, tema claro/oscuro, chat |
| API | `api/` | Java 21 · Spring Boot 4.1 · JPA · Flyway | Datos, estadísticas, comparación, ranking, sincronización, pasarela al Duende |
| Duende | `duende/` | Python 3.12+ · FastAPI · google-genai | Motor de recomendaciones y chatbot |
| Análisis | `analisis/` | Python 3.12+ · FastAPI · demoparser2 | Lee las demos de CS2 y devuelve cada ronda |
| Base de datos | — | PostgreSQL 17 (H2 en local) | Jugadores, cuentas, partidas compartidas, participaciones |

El navegador solo habla con la API Java; ni el Duende ni el análisis de demos se exponen fuera. La API calcula los resúmenes y se los pasa al
Duende: **el Duende nunca ve partidas en bruto**, así no se inventa números. Con Gemini, el Duende también puede
consultar a la API (solo lectura) lo que no está en los resúmenes, y la API le da los números ya calculados.

## Puesta en marcha en local (sin Docker)

Hace falta JDK 21 o superior, Python 3.12+ y Node 24. Sin claves de nada: la API genera **datos de ejemplo** (con todas
las demos de CS2 ya analizadas) y el Duende contesta **con reglas** (sin IA). Tres terminales, y una cuarta para
analizar demos de verdad:

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

**4. Análisis de demos (Python)** — puerto 8001. Solo hace falta para analizar demos de verdad.

```bash
cd analisis
python -m venv .venv
.venv/Scripts/pip install -r requirements-dev.txt     # en Linux/macOS: .venv/bin/pip
.venv/Scripts/python -m uvicorn app.main:app --port 8001
```

## Con Docker

```bash
cp .env.example .env     # y rellena lo que tengas
docker compose up --build
```

Web en http://localhost:4200, con Postgres y datos de ejemplo (`TTCL_DEMO=true`; ponlo a `false` con el equipo real).
Las demos que se dejen a mano van en `./demos`, que se monta en la API y en el análisis.

## Equipo real y claves

- **Equipo**: copia `config/equipo.example.json` a `config/equipo.json` con los nicks reales. La API lo carga al
  arrancar (es idempotente) y, si existe, no genera datos de ejemplo. Ese fichero no va al repo.
- **Rol** (opcional, por juego): `"cs2": { "nick": "...", "rol": "soporte" }`. CS2: `entry`, `awp`, `soporte`,
  `lurker`, `igl`, `rifler`. SMITE 2: `solo`, `jungla`, `mid`, `guardian`, `carry`. El Duende juzga cada métrica según
  lo que pide el rol (a un soporte no le pide kills) y la web lo enseña junto al nick.
- **Discord** (opcional): con `DISCORD_WEBHOOK_URL` (el webhook de un canal), la API publica ahí las partidas nuevas
  tras cada sincronización y el resumen de la semana (`DISCORD_RESUMEN_SEMANAL`, por defecto los lunes a las 10).
- **Demos de CS2** (opcional): el análisis va en `ANALISIS_URL` (por defecto `http://localhost:8001`; con Docker ya
  va puesta) y mira las demos pendientes cada `ANALISIS_INTERVALO_MIN` minutos. Se analizan las que se dejen en
  `demos/` (o en `TTCL_DEMOS_DIR`) con el id de la partida al principio del nombre: las que se bajan de la sala de la
  partida en FACEIT ya vienen así (`1-cb03…-1-1.dem.zst`). La descarga automática por la API de descargas de FACEIT es
  de pago y no se usa: todo lo que necesita la app es gratis (la clave de la Data API). `GET /api/estado` dice cuántas hay analizadas, pendientes y fallidas.
- **Consultas del chat**: con Gemini, el Duende consulta a la API en `API_URL` (por defecto `http://localhost:8080`;
  con Docker ya va puesta). Vacía o con `DUENDE_MAX_CONSULTAS=0`, Gemini contesta solo con los resúmenes.
- **Claves**: copia `.env.example` a `.env`. `GOOGLE_API_KEY` activa Gemini en el chat; `FACEIT_API_KEY` y las de
  Hi-Rez activan la sincronización (cada `SYNC_INTERVAL_MIN` minutos). Sin clave, ese juego simplemente no se toca.
- **Zona horaria**: `TTCL_ZONA_HORARIA` (por defecto `Europe/Madrid`). Con ella se sabe a qué hora del día se jugó cada
  partida (mañana, tarde, noche o madrugada).

## Tests

| Parte | Comando | Qué cubre |
|---|---|---|
| Duende | `cd duende && .venv/Scripts/python -m pytest` | Reglas de recomendación (también por rol, por compañero, tilt, hora del día, seguimiento de consejos, frente a su nivel de FACEIT y lo de las demos), chat por reglas (también "esta semana", "este mes", "para mi nivel" y "¿dónde muero más?") y de qué iba cada pregunta, uso y caché de Gemini, consultas a la API (herramientas, tope y caché), comentario de cada partida y de la semana, API |
| Análisis | `cd analisis && .venv/Scripts/python -m pytest` | Rondas de una partida (aperturas, trades, kills de salida, fuego amigo, daño, compras, KAST), lectura de la demo con un demoparser2 de mentira, descarga con tope de tamaño, descompresión, carpeta de demos, endpoint |
| API | `cd api && ./mvnw test` | Estadísticas (también sinergias, dúos y tríos, sesiones y franjas horarias, seguimiento de consejos, comparación con su nivel, filtro de partidas, lo especial de cada partida, la semana, las rondas de las demos y el rating), periodos, memoria de consejos, valoraciones, novedades y Discord, mapeo de FACEIT (también niveles, steamid y URL de la demo) y Hi-Rez, sincronización con muestras sin identificar, análisis de demos pendientes, carga del equipo con roles, API completa contra H2 con datos de ejemplo |
| Web | `cd frontend && npm test` | Texto del Duende, i18n y formatos, estado del chat, rol, nivel y ELO, "Con quién", "Cuándo juegas mejor" y el periodo en el perfil, el periodo en el ranking, dúos y tríos en el equipo, valorar recomendaciones y respuestas, comentario de cada partida, pestaña Demos con el mapa de calor, rondas de cada partida |

## El Duende

**Recomendaciones** (`duende/app/insights.py`): un motor de reglas, sin IA, que compara cada métrica con la media del
resto del equipo y con lo normal en su nivel de FACEIT o, si no se sabe, con una referencia fija de jugador medio
(`metricas.py`). Además tiene reglas con más miga: kills que
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
| `awp` | % headshot | Asistencias / partida, daño de utilidad | Kills / ronda, éxito de entrada, rating |
| `soporte` | Kills / partida | K/D, ADR, kills / ronda, éxito de entrada, rating | Asistencias / partida, daño de utilidad, asistencias de flash / partida, trades / partida |
| `lurker` | Asistencias / partida, éxito de entrada, muertes tradeadas | Daño de utilidad, trades / partida | Clutches, kills / ronda |
| `igl` | — | Kills / partida, K/D, ADR, kills / ronda, % headshot, rating | Winrate, daño de utilidad, asistencias de flash / partida |
| `rifler` | — | — | ADR, kills / ronda, rating |

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

**Memoria** (P6): la API apunta los avisos de *mejorar ya* y *a vigilar* que el Duende da en el perfil (tabla
`consejos_dados`: recomendación, métrica y su valor ese día; no la misma en 7 días). Después le pasa al Duende cómo ha
ido cada uno: el valor de entonces y el de las partidas jugadas desde entonces. Con 7 días y 5 partidas o más, el
Duende dice si ha funcionado ("Mejora en ADR. Hace 12 días te avisé: «Poco daño por ronda». Entonces tenías 78; en las
11 partidas desde entonces, 103") o si sigue sin mejorar (y repite el consejo).

**Nivel** (P8, CS2): al sincronizar, la API guarda de cada partida nueva lo que hicieron los jugadores que no son del
equipo y su nivel de FACEIT en esa partida (tabla `muestras`, sin nick ni id de nadie ni de la partida), y el nivel y el
ELO de cada cuenta del equipo. Con 50 partidas o más de su nivel, el Duende compara cada métrica con lo normal en ese
nivel (la mediana; en entradas y clutches, el total) en vez de con la referencia fija, y dice su percentil ("Tienes
35,0 %; el resto del equipo, 49,2 %, y un jugador de nivel 5 de FACEIT anda por 42,9 %. Lo haces mejor que en el 21 % de
las partidas de ese nivel"). Si en su nivel va en lo normal, lo que le separe del equipo se queda en *a vigilar*. En el
chat, "¿Cómo voy para mi nivel?" y "¿Quién tiene más nivel?". La web enseña el nivel y el ELO junto al nick de CS2.

**Demos** (P12, CS2): un trabajador aparte (`analisis/`, con `demoparser2`) lee la demo de cada partida y la API guarda
lo que hizo cada uno del equipo en cada ronda (tabla `rondas`) y dónde murió la gente, sin decir quién (`muertes_mapa`).
La demo sale de la carpeta `demos/` (la descarga automática de FACEIT es de pago y no se usa); la copia temporal se
borra al acabar. Con eso, la API
calcula un rating propio (la aproximación que circula del Rating 2.0 de HLTV), KAST, trades (en menos de 5 s),
aperturas, asistencias de flash, utilidad por ronda, CT y T, rondas ganadas según la compra y dónde muere por mapa y
zona. Con 5 partidas analizadas o más, el Duende compara rating, KAST, muertes con trade, trades y asistencias de flash
(estas, solo para reconocerlas) con el equipo y con una referencia, según el rol, y avisa de la zona de un mapa donde
muere sin que le tradeen ("En Nuke, el 57,7 % de tus muertes son en Outside y sin que nadie te tradee (60 de 104)"), del
lado en el que se apaga ("De T tu rating es 0,87; de CT, 1,70") y de las forzadas que no salen. El ADR, la utilidad y
las entradas siguen saliendo de FACEIT. En el chat, "¿Dónde muero más?" o "¿Cómo voy de T?". La web lo enseña en la
pestaña *Demos* del perfil (con un mapa de calor dibujado con las muertes de todos, sin imagen del mapa) y en las rondas
de cada partida del historial.

**Periodo**: el perfil, el cara a cara y el ranking tienen un selector de *7 días · 30 días · Todo* (en la URL,
`?periodo=7d`). Todo lo de la página cuenta solo esas partidas, también la media del equipo con la que se compara y las
recomendaciones del Duende. En la API, `?periodo=7d|30d|todo` en el perfil, el detalle, el historial, con quién,
cuándo, los consejos, el cara a cara y el ranking (por defecto, todo). Sin partidas en el periodo, los datos vienen
vacíos; el 404 es solo para quien nunca ha jugado a ese juego.

**Chat** (`chat.py`): con `GOOGLE_API_KEY` contesta Gemini, que recibe los resúmenes del equipo (con el rol de cada uno),
las sinergias, las sesiones, los últimos 7 y 30 días, el seguimiento de sus consejos, su nivel de FACEIT, sus demos y
las recomendaciones ya calculadas. Caché por petición, límite diario (`DUENDE_DAILY_LIMIT`) y modelos de respaldo si el
principal ya no existe. Sin clave, sin cuota o si Gemini falla, contestan las reglas (`reglas_chat.py`): en qué
mejorar, qué haces bien, cómo vas últimamente, peor mapa o dios, con quién juegas mejor, cuándo juegas mejor (tilt y
hora), si ha funcionado lo que te dijo, cómo vas para tu nivel de FACEIT, qué dicen tus demos (dónde mueres, CT y T,
KAST, el rating de cada uno), el mejor dúo, comparar a dos, quién es el mejor del equipo.
Si la pregunta dice "esta semana" o "este mes" (o la página tiene un periodo elegido), los números son los de esos
días, y "¿cómo voy esta semana?" los compara con los de siempre. La web indica bajo cada respuesta si la escribió
Gemini o las reglas.

**Consultas** (P9): para lo que no está en los resúmenes ("¿cómo voy en Mirage este mes?", "¿qué pasó en mis dos
últimas derrotas?", "¿con quién juego mejor esta semana?"), Gemini puede consultar a la API con cinco herramientas
(`herramientas.py`): partidas filtradas por mapa o dios, resultado, días o las últimas n (`GET
/api/jugadores/{slug}/consulta`, con su resumen y el del resto del equipo ya calculados), desglose, cara a cara,
sinergias y lo de las demos por periodo. Hasta `DUENDE_MAX_CONSULTAS` consultas por respuesta (4) y caché de un minuto; las herramientas
devuelven datos, nunca texto. Se configura con `API_URL` (vacía: sin consultas). Sin Gemini, las reglas no consultan.

**Cada partida** (P10): la API mira qué tiene de especial cada partida frente a las de antes del mismo jugador (rachas
de 3 o más y su final, rachas en un mapa o dios, estrenos, su mejor o su peor del mes con 10 partidas o más en esos 30
días, si se va un 30 % de su media) y el Duende lo cuenta en una o dos frases, siempre por reglas: "Quinta victoria
seguida. Y encima, tu mejor ADR del mes: 122 (lo mejor de antes, 116)". Sale en el historial (`comentario` en
`GET /api/jugadores/{slug}/partidas`). Se calcula al vuelo: solo mira las partidas de antes, así que no cambia.

**Discord** (P11): `GET /api/novedades?desde=` da las partidas guardadas desde ese momento (sin él, el último día),
con su comentario, y `hasta` para la siguiente consulta; `GET /api/novedades/semana`, el mejor y el peor de los
últimos 7 días de cada juego (3 partidas o más) con lo que dice el Duende. Así el bot del grupo puede publicarlo. Sin
tocar el bot, con `DISCORD_WEBHOOK_URL` la API lo publica ella misma en un canal, firmado como "El Duende" y sin
menciones: las partidas nuevas tras cada sincronización y el resumen de la semana.

**Valoraciones** (P7): bajo cada recomendación del perfil y cada respuesta del chat hay un 👍 y un 👎 (pulsar el marcado
quita el voto). Cada navegador vota con un id al azar, sin datos personales: un voto por cosa valorada, que se puede
cambiar. La API guarda el voto con lo que se vio (tabla `valoraciones`): de las recomendaciones, su id, el jugador, el
nivel y el texto; de las respuestas, la pregunta, la respuesta, quién la escribió (reglas o Gemini, con el modelo) y de
qué iba la pregunta según las reglas (`intencion`: mejorar, companeros… o *ayuda* si no la entienden). Para revisar,
`GET /api/duende/valoraciones`: las recomendaciones y los tipos de pregunta peor valorados primero y las 20 últimas
valoraciones negativas con su texto. Con eso se cambian las reglas (`insights.py`, `reglas_chat.py`) o el prompt
(`personalidad.py`).

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

Siguientes pasos: la hoja de ruta está en [docs/propuestas.md](docs/propuestas.md) (objetivos personales, y lo que ha
quedado pendiente de cada propuesta). Lo ya entregado, con lo que cambia en cada servicio y cómo
actualizar, está en [CHANGELOG.md](CHANGELOG.md).

## Fuentes de datos: estado

| Juego | Fuente | Estado |
|---|---|---|
| CS2 | FACEIT Data API (`api/.../sync/FaceitFuente.java`) | Implementada. Solo para jugadores con cuenta de FACEIT (la API de Steam no da partidas de CS2). Los campos (ADR, HS %, Entry, 1vX, Utility Damage…) siguen la documentación: **hay que validarlos con una partida real**. Los del nivel (`skill_level`, `faceit_elo`, `game_skill_level`) están comprobados con el swagger oficial, aún no con una respuesta real. |
| SMITE 2 | Hi-Rez API (`HirezFuente.java`) | Implementada **sin verificar**: confirmar la URL base de SMITE 2 (`SMITE2_API_BASE`), los métodos y los campos de `getmatchhistory`. La firma sí está probada. |
| Demos de CS2 | La carpeta `demos/` (las demos se bajan a mano de la sala de la partida en FACEIT), leídas con demoparser2 (`analisis/`) | Implementado **sin probar con una demo real**: lo que se lee de la demo sigue los nombres que documenta demoparser2, probado con tablas escritas a mano. La API de descargas de FACEIT es de pago y no se usa. |

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
  duende/      estado del chat, panel lateral, panel de recomendaciones, valoraciones (👍/👎)
  compartido/  avatar, forma (V/D), gráfica SVG
  paginas/     equipo, jugador, comparar, ranking
api/src/main/java/com/ttcl/games/
  dominio/     entidades JPA y repositorios
  stats/       cálculos puros (resumen, medias del equipo, desglose, comparación)
  servicio/    consultas para la web
  duende/      cliente HTTP del Duende y casos de uso
  analisis/    demos pendientes (de la carpeta de demos) y cliente del análisis
  sync/        FACEIT, Hi-Rez y sincronizador
  carga/       equipo desde config/equipo.json y datos de ejemplo
  web/         controladores REST
duende/app/    insights (reglas), chat, reglas del chat, Gemini, textos y métricas
analisis/app/  lectura de demos (demoparser2), rondas (cuentas puras), descarga y carpeta de demos
config/        equipo.example.json
```
