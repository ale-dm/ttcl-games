# Registro de cambios

Lo que se ha entregado, de lo más reciente a lo más antiguo. Lo que falta por hacer está en
[docs/propuestas.md](docs/propuestas.md) (P1–P12); al terminar una propuesta se marca allí y se anota aquí.

## 2026-10-11 · P12: análisis de demos de CS2

Commit `2be9bbc`. Con la demo de cada partida, el Duende ya no se queda en las medias: sabe qué pasó ronda a ronda,
dónde muere cada uno y si alguien le vengó.

**Qué se nota**
- En el perfil de CS2, la pestaña **Demos**: rating propio (al estilo del de HLTV), KAST, duelos de apertura, trades,
  muertes con trade, asistencias de flash, utilidad por ronda y rondas ganadas, cada una con la media del resto del
  equipo; CT y T; las rondas ganadas según la compra (pistola, eco, forzada, completa) y el **mapa de calor** de dónde
  muere en cada mapa, con las zonas donde más muere sin que le tradeen. En la pestaña principal, un resumen.
- En el historial, las partidas con la demo analizada se despliegan con sus rondas: una casilla por ronda con el lado,
  sus kills, si murió y si se ganó; al pasar por encima, todo lo de esa ronda (dónde murió, si le vengaron, la
  apertura, la compra).
- El Duende avisa de lo que solo dicen las demos. Con los datos de ejemplo, a Jugador 3: "En Outside te quedas solo. En
  Nuke, el 57,7 % de tus muertes son en Outside y sin que nadie te tradee (60 de 104)"; a Jugador 1: "De T te apagas. De
  T tu rating es 0,87; de CT, 1,70". También compara rating, KAST, muertes con trade, trades y asistencias de flash con
  el equipo, según el rol, y avisa si las forzadas no salen.
- En el chat: "¿Dónde muero más?", "¿Cómo voy de T?", "¿Qué tal mi KAST?" o "¿Quién tiene mejor rating?".

**Cómo funciona**
- Un servicio nuevo, `analisis/` (Python, FastAPI y `demoparser2`, en el 8001), lee cada demo y devuelve lo que hizo
  cada jugador del equipo en cada ronda y dónde murió la gente (sin decir quién). No guarda nada: la demo se borra al
  acabar y los datos los guarda la API.
- La API apunta la demo de cada partida nueva de CS2 y, cada 15 minutos, manda unas pocas pendientes al trabajador, por
  turnos. La demo sale de una de dos: con el token de la **API de descargas de FACEIT** (aparte de la clave de la Data
  API: se pide a FACEIT), una URL firmada; sin él, el fichero que se deje en la carpeta `demos/` (las que se bajan de la
  sala de la partida en FACEIT ya traen el id de la partida en el nombre).
- A cada uno se le busca por su steamid (lo da FACEIT en el perfil) o, si no, por su nick.
- La API calcula todo lo demás: KAST, trades (en menos de 5 s), aperturas, rating, CT y T, compras y zonas.

**Cambios por servicio**
- **Análisis (nuevo)**: `analisis/` con `rondas.py` (las cuentas, sin demoparser2), `demo.py` (lectura de la demo),
  `descarga.py` (URL o carpeta, descomprimir y borrar) y `POST /v1/analizar`. Configuración: `TTCL_DEMOS_DIR`,
  `ANALISIS_MAX_MB` y `ANALISIS_TIMEOUT_S`.
- **Base de datos**: migración `V7__demos.sql` (`steam_id` en `cuentas`; tablas `demos`, `rondas` y `muertes_mapa`; las
  partidas de CS2 que ya estaban quedan pendientes). Flyway la aplica sola.
- **API**: el steamid y la URL de la demo al sincronizar con FACEIT (`FaceitFuente`, `Sincronizador`); paquete
  `analisis` (`AnalisisDemos`, `AnalisisProgramado`, `AnalisisCliente`, `DescargasFaceit`); `stats/Rondas` (funciones
  puras); `GET /api/jugadores/{slug}/demos`, `/demos/calor` y `/partidas/{id}/rondas`; `analizada` en el historial y
  `analisis` en `/api/estado`. `DemoSeeder` analiza todas las partidas de CS2 de ejemplo con su propio generador
  (`RondasDemo`: no cambia ningún otro número). Configuración nueva: `ANALISIS_URL`, `ANALISIS_INTERVALO_MIN`,
  `ANALISIS_LOTE`, `TTCL_DEMOS_DIR` y `FACEIT_DOWNLOADS_TOKEN`.
- **Contrato compartido**: `ResumenDemos` (y `MetricasRondas`, `FilaLado`, `FilaCompra`, `MapaMuertes`, `ZonaMuerte`)
  en `PeticionInsights` y `JuegoContexto` (API → Duende); `analizada` en `PartidaVista` y las vistas de las demos (API →
  web). Con el trabajador: `AnalisisModelos.java` ↔ `analisis/app/modelos.py`.
- **Duende**: `METRICAS_DEMO` en `metricas.py` (y sus ajustes por rol), reglas `zona_sin_trade`, `lado_debil` y
  `forzadas_malas` en `insights.py`, la intención `demos` en el chat, el prompt de Gemini y la herramienta `demos`.
- **Web**: pestaña Demos (`panel-demos.ts`), mapa de calor (`compartido/mapa-calor.ts`), las rondas en el historial
  (`rondas-partida.ts`) y sus textos en los dos idiomas.
- **Docker**: el servicio `analisis` y la carpeta `./demos`, montada en el trabajador y en la API.

**Tests**: análisis 21 (nuevo), Duende 117 → 136, API 75 → 93, web 31 → 35, todos en verde.
- Análisis: las rondas con eventos escritos a mano (aperturas, trades, kills de salida, fuego amigo, tope de daño,
  utilidad, compras, KAST), la lectura de la demo con un demoparser2 de mentira (calentamiento, reinicios, steamids,
  lados), la descarga con tope de tamaño, la descompresión y la carpeta (sin salirse de ella) y el endpoint.
- API: las cuentas de las rondas y el rating, el mapa de calor y los nombres de zona; el steamid y la URL de la demo de
  FACEIT; el análisis de pendientes (carpeta, URL firmada, sin acceso, demo que no vale, trabajador caído, sin nadie del
  equipo); con los datos de ejemplo, las demos de cada uno, que las rondas cuadren con cada partida, el mapa de calor y
  lo que recibe el Duende.
- Duende: cada regla de las demos (también por rol y en inglés), el chat por reglas, el contrato en camelCase y la
  herramienta.
- Web: la pestaña Demos, el resumen, el mapa de calor y las rondas en el historial.
- A mano, en el navegador, con los cuatro servicios y los datos de ejemplo. Con una demo y un token de verdad no se ha
  podido probar: aquí no hay ninguno de los dos.

**Para actualizar una instalación**: parar la API, `./mvnw package -DskipTests` y arrancar (Flyway añade las tablas);
crear el entorno de `analisis/` (`python -m venv .venv` y `pip install -r requirements-dev.txt`) y arrancarlo en el 8001;
reiniciar el Duende y la web. Con Docker, `docker compose up --build` levanta el servicio nuevo. Para analizar demos:
`FACEIT_DOWNLOADS_TOKEN` o dejarlas en `demos/`.

## 2026-10-10 · P10 y P11: informe de cada partida y el Duende en Discord

Commit `15f5b85`. Cada partida del historial lleva lo que dice el Duende de ella, y lo mismo puede llegar a Discord con
el resumen de la semana.

**Qué se nota**
- En el historial del perfil, debajo de cada partida con algo especial, el Duende: "Quinta victoria seguida. Y encima,
  tu mejor ADR del mes: 122 (lo mejor de antes, 116)", "Cuarta derrota seguida en Dust2", "Se rompe la racha de 3
  derrotas. Ya era hora", "Primera vez en Anubis". En español o inglés, según la web.
- Para Discord: `GET /api/novedades?desde=` (las partidas guardadas desde entonces, con su comentario) y `GET
  /api/novedades/semana` (el mejor y el peor de los últimos 7 días de cada juego, contado por el Duende), para que el
  bot del grupo los publique.
- Con `DISCORD_WEBHOOK_URL`, la API publica sola en un canal: las partidas nuevas tras cada sincronización y el
  resumen de la semana los lunes a las 10.

**Cómo funciona**
- La API busca lo especial de cada partida frente a las de antes del mismo jugador: rachas de 3 o más y su final,
  rachas en un mapa o dios, estrenos, su mejor o su peor del mes (con 10 partidas o más en esos 30 días) y si la
  métrica principal (ADR o KDA) se va un 30 % de su media. Solo mira las de antes, así que se calcula al vuelo y no
  cambia.
- El Duende cuenta como mucho dos cosas (una racha y un número), unidas según lo que dicen, siempre por reglas.
- Las novedades van por cuándo se guardó cada partida (una de ayer sincronizada hoy es de hoy), de 100 en 100 como
  mucho, con `hasta` para seguir. El mejor y el peor de la semana, entre los que tienen 3 partidas o más; si solo
  llega uno, se dice tal cual.

**Cambios por servicio**
- **Base de datos**: migración `V6__guardada_en.sql` (columna `guardada_en` en `partidas`; las de antes, la de cuando
  se jugaron). Flyway la aplica sola.
- **API**: `Informes` (hechos de cada partida y la semana, funciones puras); `comentario` en las partidas del historial
  (con `lang`); `NovedadesController` (`/api/novedades` y `/api/novedades/semana`); `DiscordWebhook` y
  `AvisosDiscord` (publicar tras sincronizar y el resumen semanal). `DemoSeeder` guarda cada partida al acabar (no
  cambia ningún número). Configuración nueva: `DISCORD_WEBHOOK_URL`, `DISCORD_LANG` y `DISCORD_RESUMEN_SEMANAL`.
- **Contrato compartido**: `Hecho`, `ItemInforme`, `TextoInforme`, `FilaSemana` y `SemanaJuego` (API → Duende);
  `comentario` en `PartidaVista` (API → web).
- **Duende**: `informes.py` y los endpoints `/v1/informes` y `/v1/semana`; textos en `textos.py`.
- **Web**: el comentario bajo cada partida del historial (texto `tabla.duende`); el historial se pide con el idioma.
- **Docker**: la API recibe las variables de Discord.

**Tests**: Duende 110 → 117, API 64 → 75, web 30 → 31, todos en verde.
- API: los hechos (rachas, final de racha, racha en un mapa, estreno, mejor y peor del mes, media, KDA en SMITE 2) y
  la semana; trocear mensajes para Discord; con los datos de ejemplo, el historial con comentarios (y sin Duende), las
  novedades (desde un momento, de 100 en 100 sin repetir, por defecto el último día, 400), la semana y lo que se
  publica en Discord.
- Duende: cada tipo de hecho en los dos idiomas, cómo se unen dos frases, entre varios récords el que más se pasa,
  variantes fijas por partida, la semana (también con uno solo) y los endpoints.
- Web: el comentario bajo su partida.

**Para actualizar una instalación**: parar la API, `./mvnw package -DskipTests` y arrancar (Flyway añade la columna);
reiniciar el Duende y la web. Para Discord, crear un webhook en el canal y ponerlo en `DISCORD_WEBHOOK_URL`.

## 2026-10-10 · P9: chat que consulta la API

Commit `071ff31`. Con Gemini, el Duende ya no se queda en los resúmenes: si la pregunta va de un mapa, unas fechas o
unas partidas concretas, se lo pregunta a la API antes de contestar.

**Qué se nota**
- Con `GOOGLE_API_KEY`, el chat puede responder "¿cómo voy en Mirage este mes?", "¿qué pasó en mis dos últimas
  derrotas?", "¿cómo me fue ayer?" o "¿con quién juego mejor esta semana?" con los números de esas partidas, y
  comparar con lo que hizo el resto del equipo en las mismas.
- Sin Gemini no cambia nada: contestan las reglas, como antes.

**Cómo funciona**
- Gemini tiene cuatro herramientas que consultan a la API Java: partidas filtradas (mapa o dios, resultado, días o las
  últimas n), desglose por mapa o dios, cara a cara y sinergias, estas tres por periodo (7 días, 30 días o todo).
- La API hace las cuentas: de las partidas filtradas devuelve el resumen y la media del resto del equipo ya
  calculados, y las más recientes. Gemini no suma ni promedia nada.
- Hasta 4 consultas por respuesta, en dos vueltas como mucho; luego tiene que contestar con lo que tiene. Si una
  consulta falla, se le dice y sigue. Cada consulta se guarda un minuto.
- La API le pasa al chat la fecha de hoy en la zona del equipo, para que "ayer" o "septiembre" sean los días buenos.

**Cambios por servicio**
- **API**: `GET /api/jugadores/{slug}/consulta?juego=&clave=&resultado=&desde=&hasta=&ultimas=&limite=`
  (`FiltroPartidas`, función pura; `EquipoServicio.consulta`). `hoy` en la petición del chat. La API espera al Duende
  hasta 60 s (`DUENDE_TIMEOUT_MS`, antes 30): con consultas, una respuesta puede llevar tres llamadas a Gemini.
- **Contrato compartido**: `hoy` en `PeticionChat` (API → Duende).
- **Duende**: `api_ttcl.py` (cliente de la API con caché), `herramientas.py` (declaraciones y ejecución), el bucle de
  consultas en `gemini.py`, cuándo usarlas en el prompt (`personalidad.py`). Configuración nueva: `API_URL`,
  `API_TIMEOUT_MS` y `DUENDE_MAX_CONSULTAS`. `httpx` pasa a ser dependencia directa.
- **Docker**: el Duende recibe `API_URL=http://api:8080`.
- **Web**: sin cambios.

**Tests**: Duende 99 → 110, API 57 → 64, web 30 (sin cambios), todos en verde.
- API: el filtro (mapa sin "de_" ni mayúsculas, resultado, días en la zona del equipo, las últimas después de los demás
  filtros, validación) y la consulta con los datos de ejemplo (resumen igual que el desglose, media del equipo, las
  dos últimas derrotas, días sin partidas, 400 y 404); `hoy` en el chat.
- Duende: las herramientas contra una API de mentira (parámetros, recorte, nombres a slugs, errores, sin API, caché),
  el bucle de Gemini (consulta y respuesta con su id, consultas a la vez con tope, vueltas máximas, sin herramientas) y
  el chat con y sin `API_URL`.
- A mano, las cuatro herramientas contra la API de verdad con los datos de ejemplo. Con Gemini de verdad no se ha
  podido probar: aquí no hay clave.

**Para actualizar una instalación**: parar la API, `./mvnw package -DskipTests` y arrancar; en el Duende,
`pip install -r requirements.txt` (por `httpx`, que ya venía con google-genai) y reiniciar. Fuera de Docker, si la API
no está en `http://localhost:8080`, poner `API_URL`.

## 2026-10-10 · P8: comparar con jugadores de tu nivel (FACEIT)

Commit `e401cf9`. En CS2, el Duende ya no compara con números inventados: compara con lo que hacen los jugadores de tu
nivel de FACEIT, sacado de las partidas que se sincronizan.

**Qué se nota**
- En el perfil, junto al nick de CS2: "Nivel 5 · 1164 ELO".
- Las recomendaciones de CS2 comparan con tu nivel y dicen tu percentil. Con los datos de ejemplo, a Jugador 3: "Pocos
  headshots. Tienes 35,0 %; el resto del equipo, 49,2 %, y un jugador de nivel 5 de FACEIT anda por 42,9 %. Lo haces
  mejor que en el 21 % de las partidas de ese nivel". La barra pasa de "Referencia" a "Nivel 5".
- Si en tu nivel vas en lo normal, lo que te separe del equipo se queda en *a vigilar*. A Jugador 3 la utilidad le
  salía como *mejorar ya* por la media del equipo (el soporte la sube mucho), aunque en su nivel es justo lo normal.
- En el chat: "¿Cómo voy para mi nivel?" (cada métrica con lo normal en tu nivel y tu percentil, dónde más destacas y
  dónde más te queda) y "¿Quién tiene más nivel?". La primera sale entre las preguntas sugeridas si se sabe tu nivel.

**Cómo funciona**
- Al sincronizar con FACEIT, de cada partida nueva se guarda lo que hicieron los jugadores que no son del equipo, con
  su nivel en esa partida. Sin nick ni id (ni de ellos ni de la partida) y sin el marcador. Y de cada cuenta del
  equipo, su nivel y su ELO.
- Lo normal en un nivel es la mediana de sus partidas (en entradas y clutches, el total), con 50 partidas o más con
  dato. Se compara con los umbrales de siempre (como con la referencia fija); el percentil se enseña. El winrate no se
  compara con el nivel (en tu nivel es un 50 % por cómo se emparejan las partidas).
- Sin nivel o sin partidas suficientes, siguen las referencias fijas de antes. SMITE 2, igual que antes.

**Cambios por servicio**
- **Base de datos**: migración `V5__nivel_y_muestras.sql` (columnas `nivel` y `elo` en `cuentas`, tabla `muestras`).
  Flyway la aplica sola.
- **API**: `FuenteJuego.nivel` y el nivel de cada participación externa; `FaceitFuente` lee el nivel y el ELO del
  jugador (`/players/{id}`) y el de cada uno en la partida (`/matches/{id}`, una petición más por partida nueva);
  `Sincronizador` guarda las muestras y el nivel; entidad `Muestra` y su repositorio;
  `Estadisticas.comparativaNivel` (función pura); `EquipoServicio` la pasa al Duende. `DemoSeeder` da nivel a las
  cuentas de CS2 de ejemplo y genera 180 partidas de otros jugadores de cada nivel del 4 al 8, con su propio
  generador (los demás números de ejemplo no cambian).
- **Contrato compartido**: `ComparativaNivel` y `MetricaNivel`, en `PeticionInsights` y `JuegoContexto` (`nivel`);
  `nivel` y `elo` en `CuentaVista` (API → web).
- **Duende**: `Referencia` en `insights.py` (lo normal en su nivel o la fija) en textos y barras; intención `nivel` en
  `reglas_chat.py`; textos nuevos en `textos.py`; el nivel explicado en el prompt de Gemini.
- **Web**: nivel y ELO junto al nick (textos `jugador.nivel` y `jugador.nivelFaceit`).

**Tests**: Duende 87 → 99, API 52 → 57, web 29 → 30, todos en verde.
- API: lo normal en un nivel y el percentil (empates, K/D sin muertes, entradas del total, sin winrate, pocas
  partidas); el nivel del jugador y del roster de FACEIT; la sincronización (muestras sin identificar, solo de partidas
  nuevas y de los que no son del equipo, nivel de cada cuenta) con su propia base; lo que recibe el Duende con los
  datos de ejemplo (perfil, consejos, periodo, SMITE 2 sin nivel, chat y tarjetas).
- Duende: comparar con el nivel en vez de con la fija (texto, percentil, barras, inglés), el nivel cambia lo que se
  pide, métricas sin referencia fija y las que es mejor tener bajas, entradas sin percentil, el tope de *a vigilar*,
  el chat (intención, "para mi nivel", el equipo, sin nivel, SMITE 2, periodo, sugerencias, Gemini) y la API HTTP.
- Web: nivel y ELO en el perfil, en los dos idiomas.

**Para actualizar una instalación**: parar la API, `./mvnw package -DskipTests` y arrancar (Flyway añade las columnas
y la tabla); reiniciar el Duende y la web. Con FACEIT configurado, nivel y muestras llegan en la siguiente
sincronización; las partidas ya guardadas no traen muestras.

## 2026-10-10 · P7: valoración de las respuestas

Commit `8282302`. Ya se puede decir si lo que dice el Duende sirve o no, y quien lo mantiene sabe qué revisar.

**Qué se nota**
- Bajo cada recomendación del perfil, "¿Te sirve?" con un 👍 y un 👎; al votar pasa a "¡Gracias!". Bajo cada respuesta
  del chat, junto a "Sin IA · con reglas" o "Gemini · modelo", los mismos dos botones.
- Pulsar el marcado quita el voto y pulsar el otro lo cambia. Los votos a recomendaciones siguen marcados al volver al
  perfil (se recuerdan en el navegador); los del chat duran lo que la conversación.
- Para revisar: `GET /api/duende/valoraciones`. Con los datos de ejemplo, un 👎 a "Las sesiones largas se te
  atragantan" de Jugador 3 sale como `tilt_sesion`, y uno a la respuesta de "¿Qué tal el tiempo?" como `reglas / ayuda`
  (una pregunta que las reglas no entienden), cada uno con su texto.

**Cómo funciona**
- Cada navegador vota con un id al azar, sin datos personales, guardado en localStorage: un voto por cosa valorada.
  Una recomendación es su id con el jugador y el juego; una respuesta, el hash de la pregunta y la respuesta.
- La API guarda el voto con lo que se vio: de las recomendaciones, título, texto, consejo y nivel; de las respuestas, la
  pregunta, la respuesta, reglas o Gemini (con el modelo), de quién iba la charla y de qué iba la pregunta según las
  reglas (`intencion`, que ahora devuelve el Duende con cada respuesta).
- El resumen da los totales, las recomendaciones por id y las respuestas por origen e intención (lo peor valorado
  primero) y las 20 últimas valoraciones negativas con su texto.

**Cambios por servicio**
- **Base de datos**: migración `V4__valoraciones.sql` (tabla `valoraciones`). Flyway la aplica sola.
- **API**: entidad `Valoracion` y su repositorio; servicio `Valoraciones` (votar, cambiar o quitar el voto y el
  resumen). Endpoints `PUT /api/duende/valoraciones/consejo`, `PUT /api/duende/valoraciones/respuesta` (voto 1, -1 o 0;
  204) y `GET /api/duende/valoraciones`. `DemoSeeder` no cambia.
- **Contrato compartido**: `intencion` en `RespuestaChat` (Duende → API → web).
- **Duende**: `reglas_chat.intencion` (lo que entienden las reglas de la última pregunta) en todas las respuestas del
  chat, también las de Gemini y las de la caché.
- **Web**: componente `BotonesVoto`; servicio `Valoraciones` (id del navegador y votos a recomendaciones);
  `DuendeEstado.valorar` para el chat; `Api.valorarConsejo` y `Api.valorarRespuesta`; textos `duende.voto*`. El panel
  de recomendaciones recibe el jugador y el juego.

**Tests**: Duende 86 → 87, API 49 → 52, web 24 → 29, todos en verde.
- API: un voto por navegador y recomendación (cambiar, quitar, la misma a otro jugador cuenta aparte), validación
  (jugador que no existe, voto, votante, campos que faltan, origen), respuestas por pregunta y respuesta (hash, primer
  jugador del foco que existe, idioma) y el resumen (grupos, orden, negativas con su texto); `intencion` en el chat.
- Duende: la intención en las respuestas por reglas (también *ayuda*) y en las de Gemini, también desde la caché.
- Web: valorar una recomendación (lo que se manda, marcado, recordado, quitar y cambiar el voto, sin jugador no hay
  botones, si falla vuelve atrás) y una respuesta del chat (con la pregunta que la provocó, quitar el voto, si falla
  vuelve atrás, la pregunta no se valora).

**Para actualizar una instalación**: parar la API, `./mvnw package -DskipTests` y arrancar (Flyway crea la tabla);
reiniciar el Duende y la web.

## 2026-10-09 · P6: memoria de consejos

Commit `7c91ccb`. El Duende ya se acuerda de lo que le dijo a cada uno y comprueba si ha servido.

**Qué se nota**
- Si un consejo ha funcionado, el Duende lo celebra. Con los datos de ejemplo, a Jugador 3: "Mejora en ADR. Hace 12
  días te avisé: «Poco daño por ronda». Entonces tenías 78; en las 11 partidas desde entonces, 103".
- Si no, lo dice y repite el consejo: a Jugador 4, "Muertes / partida sigue sin mejorar" (de 6,63 a 9,22) con "Toca
  insistir: pon wards…". Ese aviso sustituye al de siempre de esa métrica.
- En el chat: "¿Ha funcionado lo que me dijiste?" repasa cada consejo (funciona, sigue sin mejorar, aún es pronto o
  aún no has jugado). Sale entre las preguntas sugeridas cuando hay algo que revisar.

**Cómo funciona**
- Al abrir el perfil (con todas las partidas), la API apunta los avisos de *mejorar ya* y *a vigilar*: recomendación,
  métrica y su valor ese día. No apunta la misma en 7 días ni los avisos del propio seguimiento.
- Después le pasa al Duende, de cada aviso con métrica de los últimos 60 días (la primera vez que se dio), el valor de
  entonces y el de las partidas jugadas desde entonces.
- El Duende juzga con 7 días y 5 partidas o más desde el consejo: funciona si mejora 5 puntos (porcentajes) o un 10 %
  (lo demás); no funciona si no mejora o empeora. Solo el que más ha mejorado y el peor.

**Cambios por servicio**
- **Base de datos**: migración `V3__consejos_dados.sql` (tabla `consejos_dados`). Flyway la aplica sola.
- **API**: entidad `ConsejoDado` y su repositorio; servicio `MemoriaConsejos` (apunta al pedir los consejos del
  perfil); `Estadisticas.seguimiento` (función pura). `DemoSeeder` apunta dos consejos antiguos y hace que Jugador 4
  muera más desde el suyo.
- **Contrato compartido**: `SeguimientoConsejo` y `seguimiento` en `PeticionInsights` y `JuegoContexto`.
- **Duende**: reglas `consejo_funciona` y `consejo_no_funciona` en `insights.py`; textos en `textos.py`; intención
  `seguimiento` en `reglas_chat.py`; el seguimiento explicado en el prompt de Gemini.
- **Web**: sin cambios (los avisos nuevos salen en el panel de siempre).

**Tests**: Duende 76 → 86, API 46 → 49, web 24 (sin cambios), todos en verde.
- API: seguimiento (la primera vez de cada aviso, sin métrica fuera, más de 60 días fuera, sin partidas desde
  entonces); apuntar consejos (solo nuevos, solo *mejorar ya* y *a vigilar*, con el valor de ese día, sin repetir, no
  con un periodo) y el seguimiento que recibe el Duende, también el de los datos de ejemplo.
- Duende: funciona y no funciona (con su nivel y su consejo, sustituyendo al aviso de siempre), aún es pronto, solo el
  mejor y el peor, inglés y avisos de reglas especiales, chat (repaso, sin consejos, sin foco, sugerencias), prompt de
  Gemini y la API HTTP.

**Para actualizar una instalación**: parar la API, `./mvnw package -DskipTests` y arrancar (Flyway crea la tabla);
reiniciar el Duende. En Postgres con datos de ejemplo viejos no habrá consejos de ejemplo: aparecen en cuanto se abran
los perfiles y pase una semana.

## 2026-10-09 · P4: filtros por periodo

Commit `3522cdd`. Ya se puede ver cómo va cada uno en los últimos 7 o 30 días, no solo con todas sus partidas.

**Qué se nota**
- Selector **7 días · 30 días · Todo** en el perfil, el cara a cara y el ranking. Va en la URL (`?periodo=7d`), así que
  se puede compartir, y se conserva al saltar de una página a otra.
- En el perfil, todo cuenta solo las partidas del periodo: cifras, gráfica, mapas o dioses, historial, con quién,
  cuándo y las recomendaciones del Duende. La media del equipo con la que se compara también es la de esos días. La
  fecha de la última partida es siempre la de verdad.
- Si no jugó en esos días, se dice: "Jugador 1 no ha jugado a SMITE 2 en los últimos 7 días". Lo mismo en el ranking y
  el cara a cara.
- En el chat: "¿Cómo voy esta semana?" compara los números de esos días con los de siempre ("Jugador 3 en
  Counter-Strike 2: 5 partidas (5 victorias y 0 derrotas)… Mejor que de costumbre"). "¿En qué tengo que mejorar este
  mes?" o "¿Quién es el mejor esta semana?" responden con los números de esos días. Si en la página hay un periodo
  elegido, el Duende lo usa salvo que la pregunta diga otro ("en total" vuelve a todas).

**Cómo funciona**
- Periodos móviles: los últimos 7 o 30 días desde ahora.
- La API recorta las partidas antes de calcular, así que todo sale del periodo. Sin partidas en él, datos vacíos (no
  404); el 404 queda para quien nunca ha jugado a ese juego.
- El chat recibe, además de todo, un resumen de los últimos 7 y 30 días de cada uno (con la media del equipo en esos
  días) y el periodo de la página. Mapas, compañeros y sesiones se responden con todas las partidas, y lo avisa.

**Cambios por servicio**
- **API**: `Periodo` (7d, 30d, todo) y `Estadisticas.desde`; la foto del equipo se recorta con `desde()`. Parámetro
  `?periodo=` en `GET /api/jugadores/{slug}`, `/juegos/{juego}`, `/partidas`, `/sinergias`, `/sesiones`, `/consejos`,
  `/api/comparar` y `/api/ranking`. Un periodo desconocido da 400.
- **Contrato compartido**: `ResumenPeriodo` y `periodos` en `JuegoContexto`; `periodo` en `PeticionChat` (web → API →
  Duende).
- **Duende**: `detectar_periodo` y respuestas con los números de esos días en `reglas_chat.py` ("¿cómo voy esta
  semana?" frente a siempre, plurales bien puestos); los periodos en el prompt de Gemini.
- **Web**: componente `SelectorPeriodo`, `periodoDe()` en `modelos.ts`, `i18n.enPeriodo()`, textos `periodo.*`; el
  periodo en el contexto del chat (y en su etiqueta: "Hablando de Ana · CS2 · 7 días").
- Sin migración.

**Tests**: Duende 67 → 76, API 40 → 46, web 20 → 24, todos en verde.
- API: el código del periodo y su inicio; el recorte por fecha; con los datos de ejemplo, el mismo recorte en detalle,
  historial, perfil, ranking y cara a cara; un periodo sin partidas (datos vacíos, no 404) y quien nunca jugó (404); lo
  que recibe el chat (resúmenes de 7 y 30 días, el periodo de la página, 400 con uno desconocido).
- Duende: detectar el periodo (la pregunta manda sobre la página), "¿cómo voy esta semana?" en los dos idiomas,
  consejos de esos días, sin partidas en esos días, el equipo de la semana, mapas/compañeros/sesiones con todas, el
  prompt de Gemini y la API HTTP.
- Web: el perfil con periodo (pide todo con él, avisa si no hay partidas, la URL al cambiarlo), el ranking con periodo
  y el periodo en el chat.

**Para actualizar una instalación**: como siempre (parar la API, `./mvnw package -DskipTests`, arrancar; reiniciar el
Duende). No hay migraciones.

## 2026-10-09 · P3: sesiones y tilt

Commit `9e1b779`. El Duende ya sabe cuándo juega cada uno: cuántas partidas seguidas, qué pasa después de perder y a
qué hora del día. Con eso avisa del tilt y dice a qué hora rinde más cada uno.

**Qué se nota**
- En el perfil, tarjeta **"Cuándo juegas mejor"**: winrate en la 1ª, la 2ª y de la 3ª en adelante de cada sesión,
  después de ganar o de perder, y por hora del día; cada fila, con el winrate del resto de partidas.
- El Duende avisa del tilt y da la mejor hora. Con los datos de ejemplo, a Jugador 3 en CS2: "Las sesiones largas se te
  atragantan. A partir de la 3ª partida seguida ganas el 37,5 % (16 partidas); en las dos primeras, el 63,9 %", con el
  consejo de jugar sesiones de dos o tres partidas y parar tras dos derrotas seguidas. A Jugador 4 en SMITE 2: "Rindes
  más por la tarde. Por la tarde ganas el 64,3 % (14 partidas); el resto del día, el 40,0 %".
- En el chat: "¿Cuándo juego mejor?", "¿Me tilteo?" o "¿A qué hora juego mejor?" (todas las filas, el aviso y el
  consejo). Está entre las preguntas sugeridas.

**Cómo funciona**
- Sesión: partidas con menos de 45 minutos entre el final de una y el principio de la siguiente (sin duración, desde su
  principio). Horas del día en la zona del equipo: mañana 6–14 h, tarde 14–20 h, noche 20–24 h, madrugada 0–6 h.
- Cada fila se compara con el resto de partidas, no con el winrate global (como en P2).
- El Duende no dice nada con menos de 15 sesiones y pide 10 partidas a cada lado. Tilt: 15 puntos menos desde la 3ª
  seguida o, si eso no sale, en la partida que sigue a una derrota (25 o más, *mejorar ya*). Mejor hora: 20 puntos más
  que el resto del día (se elige entre cuatro, así que se pide más).
- El resultado es del equipo: si uno se tiltea, quien juega con él también lo nota. Con los datos de ejemplo, Jugador 2
  también recibe el aviso. El texto no culpa a nadie y el consejo vale igual.

**Cómo se configura**: `TTCL_ZONA_HORARIA` en `.env` (por defecto `Europe/Madrid`; con Docker también).

**Cambios por servicio**
- **API**: `Estadisticas.sesiones` (función pura) y `PAUSA_SESION`; `FilaParticipacion` lleva la duración de la
  partida; endpoint `GET /api/jugadores/{slug}/sesiones?juego=`; `ttcl.zona-horaria` en `application.yml`.
  `DemoSeeder` juega por sesiones (mismo grupo, varias partidas seguidas, casi siempre por la tarde o la noche), con un
  patrón de tilt (Jugador 3) y una hora buena (Jugador 4). **Cambian todos los números de ejemplo**: el de P2 es ahora
  "Con Jugador 2 ganas el 51,0 % de 51 partidas; sin Jugador 2, el 21,4 %".
- **Contrato compartido**: `Sesiones` y `FilaMomento` en `PeticionInsights` y `JuegoContexto` (API → Duende) y para la
  web.
- **Duende**: reglas `tilt_sesion`, `tilt_derrota` y `mejor_horario` en `insights.py`; textos y `NOMBRES_MOMENTO` en
  `textos.py`; intención `sesiones` en `reglas_chat.py`; las sesiones explicadas en el prompt de Gemini. Arreglado de
  paso: el consejo de las reglas especiales no rellenaba sus `{huecos}`.
- **Web**: tarjeta en el perfil, `i18n.momento()`, textos `momento.*` y la pregunta sugerida.
- Sin migración: la duración ya se guardaba.

**Tests**: Duende 55 → 67, API 34 → 40, web 17 → 20, todos en verde.
- API: sesiones con pausas justas, sin duración, pasando la medianoche y sin resultado; la zona horaria y los límites de
  cada franja; una sola partida y ninguna; el endpoint con los datos de ejemplo (cada lista suma todas las partidas);
  que los datos de ejemplo tengan tilt y hora buena; lo que se manda al Duende.
- Duende: tilt (medio y alto), sin avisos con menos de 15 sesiones, pocas partidas o poca diferencia, tras derrota (y
  que no se repita con el de sesiones), mejor hora (y que no premie una franja con pocas partidas), inglés, nombres de
  todas las filas, chat (con foco, pocas sesiones, sin nada que decir, sin datos, sin foco, el consejo aunque no quepa
  en el panel, sugerencias), prompt de Gemini y la API HTTP.
- Web: la tarjeta con sus tres bloques, los bloques vacíos fuera y la traducción de todas las filas.

**Para actualizar una instalación**: como en P1 (parar la API, `./mvnw package -DskipTests`, arrancar; reiniciar el
Duende). No hay migraciones. Con datos de ejemplo en H2, al arrancar se generan los nuevos; en Postgres con datos de
ejemplo viejos, vacía la base para verlos.

## 2026-10-09 · P2: con quién juegas mejor (sinergias)

Commit `c109873`. El Duende ya sabe con quién juega cada uno, algo que estaba en la base desde el principio (cada
partida guarda a todos los del equipo que la jugaron) pero no se usaba.

**Qué se nota**
- En el perfil, tarjeta **"Con quién"**: winrate con cada compañero del equipo, en el resto de partidas y jugando solo.
- En la página del equipo, **"Los que mejor se entienden"**: el mejor dúo y el mejor trío de cada juego.
- El Duende avisa cuando con alguien se gana mucho más o mucho menos. Con los datos de ejemplo, a Jugador 1: "Con
  Jugador 3 ganas el 55,2 % de 29 partidas; sin Jugador 3, el 16,7 %". Y a Jugador 3, al revés, que con Jugador 1 no
  termina de cuajar (55,2 % con él, 72,7 % sin él).
- En el chat: "¿Con quién juego mejor?" (compañeros, solo y el consejo) y, desde la página del equipo, "¿Cuál es
  nuestro mejor dúo?". Las dos están entre las preguntas sugeridas.

**Cómo funciona**
- Solo cuenta como compañero quien estaba en el mismo bando (mismo resultado): en FACEIT dos del equipo pueden caer en
  bandos contrarios.
- Filas con al menos 3 partidas. El Duende compara jugar **con** el compañero frente a jugar **sin** él (no frente al
  winrate global, como decía la propuesta: en un grupo pequeño casi todo se juega con los mismos y la diferencia no se
  vería). Avisa con 15 puntos o más de diferencia y 5 partidas o más a cada lado; solo del mejor y del peor compañero.
- Dúos y tríos: partidas en que estaban juntos en el mismo bando, aunque hubiera alguien más.

**Cambios por servicio**
- **API**: `Estadisticas.sinergias` y `Estadisticas.grupos` (funciones puras); endpoints
  `GET /api/jugadores/{slug}/sinergias?juego=` y `GET /api/equipo/grupos?juego=`. El historial de partidas sigue
  igual, ahora calculado a partir de quién jugó cada partida.
- **Contrato compartido**: `Sinergias` y `FilaSinergia` en `PeticionInsights` y `JuegoContexto` (API → Duende);
  `Sinergias`, `Grupo` y `GruposJuego` para la web.
- **Duende**: reglas `companero_bueno` y `companero_malo` en `insights.py`, textos en `textos.py`, intención
  `companeros` en `reglas_chat.py` y las sinergias explicadas en el prompt de Gemini.
- **Web**: tarjeta en el perfil, sección en el equipo y textos en `textos.ts`.
- Sin migración: no hay datos nuevos.

**Tests**: Duende 42 → 55, API 27 → 34, web 13 → 17, todos en verde.
- API: sinergias con compañero, solo y bandos contrarios; mínimo de partidas; dúos y tríos con su orden; los endpoints
  con los datos de ejemplo (con + sin = todas las partidas); lo que se manda al Duende.
- Duende: compañero bueno y malo, umbrales y muestras, solo el mejor y el peor, inglés, chat (con foco, sin datos,
  mejor dúo, sugerencias, el consejo aunque no quepa en el panel), prompt de Gemini y la API HTTP.
- Web: la tarjeta "Con quién" (con y sin datos) y los mejores grupos del equipo.

**Para actualizar una instalación**: como en P1 (parar la API, `./mvnw package -DskipTests`, arrancar; reiniciar el
Duende). No hay migraciones.

## 2026-10-08 · P1: rol del jugador

Commit `9d35552`. Hasta ahora el Duende no sabía qué papel juega cada uno y podía regañar a un soporte por hacer pocas
kills o a un entry por morir mucho, que es justo su trabajo.

**Qué se nota**
- Cada cuenta puede declarar su rol. CS2: `entry`, `awp`, `soporte`, `lurker`, `igl`, `rifler`. SMITE 2: `solo`,
  `jungla`, `mid`, `guardian`, `carry`.
- El Duende juzga cada métrica según el rol: lo que el rol no pide no se juzga o se tolera, y lo suyo pesa más. La tabla
  completa está en el [README](README.md#el-duende). Cada recomendación afectada lo explica con una coletilla, en
  español e inglés: "Justo lo que pide tu rol de soporte", "En tu rol de entry se perdona algo, pero no tanto".
- El chat también lo usa: las reglas lo dicen en "¿en qué tengo que mejorar?" y Gemini recibe el rol de cada uno con la
  instrucción de no reprochar lo que el rol no pide.
- La web enseña el rol junto al nick en el perfil, traducido.
- Ejemplo con los datos de ejemplo, Jugador 2 (soporte) en CS2. Antes: "Kills / partida", "K/D" y "ADR" en *mejorar
  ya*. Ahora: nada de kills, el K/D baja a *a vigilar* con su coletilla, el ADR se le tolera y las asistencias salen en
  *lo haces bien*.

**Cómo se configura**

En `config/equipo.json`, dentro de cada cuenta (es opcional):

```json
"cuentas": {
  "cs2": { "nick": "NICK_FACEIT", "rol": "soporte" },
  "smite2": { "nick": "NICK_SMITE2", "rol": "guardian" }
}
```

No distingue mayúsculas ni tildes ("Guardián" vale). Se vuelve a leer en cada arranque: cambiarlo o quitarlo del
fichero lo cambia o lo quita. Un rol que no es de ese juego deja la cuenta sin rol y avisa en el log de la API.

**Cambios por servicio**
- **Base de datos**: migración `V2__rol.sql` (columna `rol` en `cuentas`, puede ser null). Flyway la aplica sola al
  arrancar.
- **Contrato compartido**: campo `rol` (texto o null) en `CuentaVista` (API → web), `PeticionInsights` y
  `JuegoContexto` (API → Duende).
- **API**: `Juego.roles()` y `Juego.rol(texto)` (valida y normaliza); `EquipoSeeder` lee el rol; `DemoSeeder` da rol a
  los jugadores de ejemplo (Jugador 1 rifler y mid, Jugador 2 soporte y guardián, Jugador 3 entry, Jugador 4 jungla).
- **Duende**: `AJUSTES_ROL` en `metricas.py` da a cada métrica un factor por rol (no se juzga 0, se tolera 0,5, pesa más
  1,5) que multiplica la desviación antes de decidir el nivel. Nombres de los roles y coletillas en `textos.py`. Un rol
  desconocido cuenta como ninguno.
- **Web**: `i18n.rol()` y textos `rol.*` en `textos.ts`.

**Tests**: Duende 31 → 42, API 21 → 27, web 10 → 13, todos en verde.
- Duende: soporte, entry (con el límite de la tolerancia), AWP, guardián de SMITE 2, umbral de fortaleza, rol
  desconocido, textos en inglés, coherencia de la tabla de roles, chat por reglas, prompt de Gemini y la API HTTP.
- API: validación de roles en `Juego`; carga de `equipo.json` con roles, roles no válidos y recarga sin duplicar (con
  su propia base H2); el rol en el perfil y en lo que se manda al Duende.
- Web: traducción de roles y el rol en la cabecera del perfil.

**Para actualizar una instalación**
- En local: parar la API, `cd api && ./mvnw package -DskipTests` y arrancarla (el jar en uso no se puede sobrescribir,
  y `./mvnw clean` tampoco puede borrarlo); reiniciar el Duende; la web se recarga sola.
- Con Docker: `docker compose up --build`.

**Pendiente**: deducir el rol de SMITE 2 por la clase del dios más jugado (falta una tabla dios → clase de una fuente
verificada) y poder editar el rol desde la web (necesita la autenticación de P5).

## 2026-10-08 · Hoja de ruta del Duende

Commit `3b35a8e`. `docs/propuestas.md` con doce propuestas (P1–P12) para dar mejores datos al Duende, con orden
recomendado y criterio de terminado, y `CLAUDE.md` con la arquitectura, los comandos y las peculiaridades del entorno.

## 2026-10-08 · Nueva arquitectura: Angular + API Java + Duende en Python

Commit `b0fe8b1`. Sustituye la versión en Next.js por tres servicios: web en Angular 21 al estilo de csstats.gg, API en
Spring Boot 4.1 (estadísticas, sincronización con FACEIT y Hi-Rez, datos de ejemplo) y el Duende en FastAPI
(recomendaciones por reglas y chat con Gemini o, sin clave, con reglas). Docker Compose con Postgres.

## 2026-10-08 · Base del proyecto

Commit `36bf25f`. Primera versión en Next.js con PostgreSQL, sincronización con FACEIT y Hi-Rez y el Duende con Gemini.
Sigue en el historial como referencia.
