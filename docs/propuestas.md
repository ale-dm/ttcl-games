# Propuestas para mejorar los datos y el Duende

Hoja de ruta para que el Duende dé consejos más útiles. Hoy su límite son los datos, no la IA: cada propuesta dice qué
dato añade, qué cambia en cada servicio y cómo lo aprovecha el Duende. Al terminar una, marcarla en la tabla, anotar
lo que se aprendió en su apartado y apuntar la entrega en [CHANGELOG.md](../CHANGELOG.md).

## Estado

| ID | Propuesta | Fase | Esfuerzo | Datos | Estado |
|---|---|---|---|---|---|
| P1 | Rol del jugador | 1 | Bajo | Nuevos (los da el jugador) | Hecho |
| P2 | Con quién juegas mejor (sinergias) | 1 | Bajo | Ya guardados | Hecho |
| P3 | Sesiones y tilt | 1 | Bajo | Ya guardados | Hecho |
| P4 | Filtros por periodo | 1 | Bajo | Ya guardados | Hecho |
| P5 | Objetivos personales | 2 | Medio | Nuevos (los da el jugador) | Pendiente |
| P6 | Memoria de consejos | 2 | Medio | Se generan | Hecho |
| P7 | Valoración de las respuestas | 2 | Bajo | Se generan | Pendiente |
| P8 | Comparar con jugadores de tu nivel (FACEIT) | 3 | Medio | Ya llegan, se tiran | Pendiente |
| P9 | Chat que consulta la API (function calling) | 3 | Medio | Ya guardados | Pendiente |
| P10 | Informe de cada partida | 3 | Bajo | Ya guardados | Pendiente |
| P11 | El Duende en Discord | 4 | Medio | — | Pendiente |
| P12 | Análisis de demos de CS2 | 5 | Alto | Nuevos (demos) | Pendiente |

**Orden recomendado**: P1 → P2 → P3 (corrigen lo que el Duende dice mal hoy y usan lo que ya hay), luego P8 (cambia
referencias inventadas por datos reales), luego P5/P6 y P11 (seguimiento y que la gente vuelva). P12 es el gran salto
y conviene hacerlo cuando lo demás esté estable.

## Punto de partida (cómo funciona hoy)

- La API Java guarda, por partida y jugador del equipo: resultado, kills, muertes, asistencias y un `datos` JSON con lo
  específico del juego (CS2: mapa, ADR, HS %, K/R, entradas, clutches, daño de utilidad; SMITE 2: dios, daño, oro,
  mitigado, curación, minutos). Ver `FaceitFuente.mapearEstadisticas` y `HirezFuente.mapearFila`.
- Las estadísticas se calculan en memoria (`api/.../stats/Estadisticas.java`): resumen, últimas 10, media del resto
  del equipo, desglose por mapa/dios y comparación.
- El Duende (Python) recibe solo resúmenes. Las recomendaciones salen de `duende/app/insights.py`, que compara con la
  media del equipo y con **referencias fijas escritas a mano** (`duende/app/metricas.py`).
- El chat (`duende/app/chat.py`) manda a Gemini los resúmenes y las recomendaciones; sin clave contesta
  `reglas_chat.py`.

**Limitaciones que motivan esta hoja de ruta**

1. No sabe el rol de cada uno: puede regañar a un soporte por hacer pocas kills. (Resuelto en P1.)
2. Las referencias de "jugador medio" son números fijos, no gente de tu nivel.
3. No sabe con quién juegas, cuándo ni cuántas seguidas, aunque esos datos ya están en la base. (Resuelto en P2 y P3.)
4. No recuerda qué te dijo ni si sirvió. (Resuelto en P6.)
5. Solo ve medias por partida: nada de rondas, posiciones, trades o economía.

---

## Fase 1 · Usar mejor lo que ya tenemos

### P1 · Rol del jugador

**Por qué**: es lo que más mejora la calidad del consejo. Un soporte con pocas kills o un entry que muere mucho están
haciendo su trabajo.

**Datos**: rol por cuenta y juego.
- CS2: `entry`, `awp`, `soporte`, `lurker`, `igl`, `rifler`.
- SMITE 2: `solo`, `jungla`, `mid`, `guardian`, `carry`. Si no se declara, se puede intentar deducir de la clase del dios
  más jugado (Guardian → guardián, Hunter → carry, Mage → mid, Assassin → jungla, Warrior → solo), pero solo como
  sugerencia.

**Cambios**
- API: migración `V2__rol.sql` con columna `rol` en `cuentas`; leerlo de `config/equipo.json`
  (`"cs2": { "nick": "...", "rol": "entry" }`) en `EquipoSeeder`; incluirlo en `CuentaVista`, en `PeticionInsights` y en
  `JuegoContexto` (`DuendeModelos.java`).
- Duende: añadir `rol` a `modelos.py`. En `metricas.py`, ajustes por rol: qué métricas no se juzgan (soporte: kills;
  entry: muertes y K/D más bajos aceptables) y qué métricas pesan más (soporte: asistencias y utilidad; AWP: K/R y
  primeras kills). Textos nuevos en ES y EN en `textos.py`. El rol va también al prompt de Gemini.
- Web: rol junto al nick en el perfil; en el futuro, editable (ver nota de autenticación en P5).

**Hecho cuando**: un soporte con asistencias altas y kills bajas no recibe "Kills / partida: por debajo del equipo" y sí
un "bien" por asistencias; tests en `duende/tests/test_insights.py`.

**Hecho** (8 de octubre de 2026). Lo que quedó y lo que se aprendió:
- Cada rol da a cada métrica un factor (`AJUSTES_ROL` en `metricas.py`): `NO_SE_JUZGA` (0), `TOLERA` (0,5) o
  `PESA_MAS` (1,5); las demás cuentan 1. La desviación frente al equipo y la referencia se multiplica por el factor
  antes de decidir el nivel, así que tolerar no es un pase libre: un entry que muere un 50 % más que el equipo sigue
  recibiendo "alto". Lo que pesa más también se reconoce antes como fortaleza y va primero dentro de su nivel.
- El texto lo explica con una coletilla ("Justo lo que pide tu rol de soporte", "En tu rol de entry se perdona algo,
  pero no tanto"). El chat por reglas pone el rol en "en qué mejorar" y Gemini recibe el rol y la instrucción de no
  reprochar lo que el rol no pide.
- Efecto secundario buscado: con el tope de dos fortalezas, las del rol desplazan a otras (al soporte de ejemplo ya no
  le sale "Estás on fire" porque asistencias y utilidad pesan más). Y al quitar avisos que no tocaban, entran otros que
  antes se quedaban fuera del tope de cuatro (su winrate, su peor mapa).
- La API valida el rol al leer `config/equipo.json` sin distinguir mayúsculas ni tildes ("Guardián" → `guardian`). Uno
  que no es del juego deja la cuenta sin rol y lo avisa en el log; el Duende, por si acaso, trata igual un rol que no
  conoce. Ningún rol se llama igual en los dos juegos: la web los traduce sin mirar el juego (hay test que lo vigila).
- Datos de ejemplo: Jugador 1 rifler y mid, Jugador 2 soporte y guardián, Jugador 3 entry, Jugador 4 jungla.
- **Queda pendiente**: deducir el rol de SMITE 2 por la clase del dios más jugado (hace falta una tabla dios → clase
  que hoy no da ninguna fuente verificada) y poder editar el rol desde la web (necesita la autenticación de P5).

### P2 · Con quién juegas mejor (sinergias)

**Datos**: ya están. Cada partida guarda a todos los del equipo que la jugaron (`participaciones`).

**Cambios**
- API: en `Estadisticas`, winrate y K/D de un jugador agrupado por compañero (y "solo"), con mínimo de 3 partidas.
  Endpoint `GET /api/jugadores/{slug}/sinergias?juego=`. Añadirlo a `JuegoContexto` para el Duende.
- Duende: reglas `companero_bueno` / `companero_malo` en `insights.py` (diferencia ≥ 15 pp frente a su winrate global) y
  pregunta nueva en `reglas_chat.py` ("¿con quién juego mejor?").
- Web: tarjeta "Con quién" en el perfil y, en el equipo, el mejor dúo y el mejor trío.

**Hecho cuando**: el Duende puede decir "con Jugador 3 ganas el 65 %, sin él el 38 %" con datos de ejemplo.

**Hecho** (9 de octubre de 2026). Con los datos de ejemplo, a Jugador 1: "Con Jugador 3 ganas el 55,2 % de 29 partidas;
sin Jugador 3, el 16,7 %". Lo que quedó y lo que se aprendió:
- **Cambio sobre lo previsto: se compara con y sin el compañero, no con el winrate global.** En un grupo pequeño casi
  todo se juega con los mismos, así que "con él" es casi todo el global y la diferencia no se ve: con los datos de
  ejemplo, el caso más claro (Jugador 1 con Jugador 3: 55 % con él, 17 % sin él) se quedaba en +14,8 puntos frente al
  global. Las reglas `companero_bueno` / `companero_malo` saltan con 15 puntos o más entre con y sin, y piden al menos 5
  partidas a cada lado. Solo se habla del mejor y del peor compañero.
- **Mismo bando**: en FACEIT dos del equipo pueden caer en bandos contrarios. Solo cuenta como compañero quien tuvo el
  mismo resultado en esa partida; si no hay nadie del equipo en su bando, la partida cuenta como "solo".
- La API filtra compañeros, "solo", dúos y tríos con menos de 3 partidas (`MIN_PARTIDAS_SINERGIA`). Dúos y tríos cuentan
  las partidas en que estaban juntos aunque hubiera alguien más; el mejor es el de más winrate y, a igualdad, el que
  más ha jugado. Con 3 partidas un 3 de 3 puede quedar primero: la web enseña siempre cuántas son.
- Con el tope de dos fortalezas, el compañero puede quedarse fuera del panel (a Jugador 2 le pasa: sus asistencias y su
  utilidad pesan más por su rol). Al preguntar "¿con quién juego mejor?", el chat lo da igualmente.
- El chat por reglas responde "¿con quién juego mejor?" (compañeros, solo y el consejo) y, sin nadie en el foco, "¿cuál
  es nuestro mejor dúo?". Gemini recibe las sinergias de los jugadores del foco.
- Endpoints nuevos: `GET /api/jugadores/{slug}/sinergias?juego=` y `GET /api/equipo/grupos?juego=` (sin `juego`, todos).

### P3 · Sesiones y tilt

**Datos**: ya están (`jugada_en` y `duracion_seg`). Una sesión son partidas separadas por menos de ~45 minutos.

**Cambios**
- API: en `Estadisticas`, rendimiento por número de partida dentro de la sesión (1ª, 2ª, 3ª+), después de una derrota y
  por franja horaria. Va al contexto del Duende.
- Duende: reglas `tilt_sesion` ("a partir de la 3ª seguida tu winrate cae del 55 al 35 %") y `mejor_horario`, con
  consejo concreto (parar tras dos derrotas seguidas, jugar a su mejor hora).
- `DemoSeeder`: dar a algún jugador de ejemplo un patrón de tilt para que se vea.

**Hecho cuando**: con datos de ejemplo sale al menos un aviso de tilt bien justificado y ninguno con menos de ~15 sesiones.

**Hecho** (9 de octubre de 2026). Con los datos de ejemplo, a Jugador 3 en CS2: "A partir de la 3ª partida seguida ganas
el 37,5 % (16 partidas); en las dos primeras, el 63,9 %", y a Jugador 4 en SMITE 2: "Por la tarde ganas el 64,3 % (14
partidas); el resto del día, el 40,0 %". Lo que quedó y lo que se aprendió:
- **Sesión**: menos de 45 minutos entre el final de una partida y el principio de la siguiente (`PAUSA_SESION`). Si no
  se sabe cuánto duró, se cuenta desde su principio. Sesiones por jugador y juego.
- **Se compara cada fila con el resto de partidas**, como en P2 (no con el winrate global): la API manda, por cada fila,
  `partidasResto` y `winrateResto`. Filas: 1ª, 2ª y 3ª en adelante; tras victoria y tras derrota (dentro de la misma
  sesión); mañana (6–14 h), tarde (14–20 h), noche (20–24 h) y madrugada (0–6 h) en la zona del equipo
  (`TTCL_ZONA_HORARIA`, por defecto `Europe/Madrid`).
- **Umbrales del Duende**: 15 sesiones o más (con menos, ni un aviso: lo pide la propuesta y hay test), 10 partidas a
  cada lado. Tilt con 15 puntos menos desde la 3ª seguida (`tilt_sesion`) o tras perder (`tilt_derrota`); desde 25
  puntos, *mejorar ya*. Mejor hora con 20 puntos más: se elige entre cuatro franjas y con 15 saldría alguna por pura
  casualidad.
- `tilt_derrota` solo sale si no sale `tilt_sesion`: casi siempre son lo mismo (las derrotas se amontonan al final de
  las sesiones largas) y dos avisos de tilt seguidos sobran.
- **El tilt se contagia en los datos**: Jugador 2 también recibe el aviso (39 % desde la 3ª, 62 % antes) porque juega
  casi siempre con Jugador 3 y el resultado es del equipo. El Duende no puede saber de quién es la culpa, así que el
  texto no culpa a nadie ("Las sesiones largas se te atragantan") y el consejo vale igual: sesiones más cortas.
- Como en P2, con el tope de dos fortalezas la mejor hora puede quedarse fuera del panel (a Jugador 1 en SMITE 2 le
  pasa). Al preguntar "¿cuándo juego mejor?", el chat la da igualmente.
- `DemoSeeder` ahora juega por sesiones: el mismo grupo, varias partidas seguidas, casi siempre por la tarde o la noche.
  Jugador 3 se tiltea desde la 3ª y Jugador 4 rinde más por la tarde. Todos los números de ejemplo han cambiado (el
  ejemplo de P2 es ahora "Con Jugador 2 ganas el 51,0 % de 51 partidas; sin Jugador 2, el 21,4 %").
- De paso: el consejo de las reglas especiales no rellenaba sus `{huecos}` (ninguno los usaba hasta ahora).
- Endpoint nuevo: `GET /api/jugadores/{slug}/sesiones?juego=`.
- **Queda pendiente**: comprobar con datos reales si `Entry_Datetime` de Hi-Rez es el principio o el final de la
  partida (si es el final, las pausas salen una partida más cortas y alguna sesión se juntaría con la siguiente).

### P4 · Filtros por periodo

**Cambios**
- API: parámetro `desde` (o `periodo=7d|30d|todo`) en perfil, detalle, partidas, comparar y ranking. Las funciones de
  `Estadisticas` ya trabajan sobre listas: basta con filtrar antes.
- Web: selector de periodo en perfil, comparar y ranking (en la URL, como el juego).
- Duende: el chat reconoce "esta semana" o "este mes" en `reglas_chat.py`; con Gemini llega con P9.

**Hecho** (9 de octubre de 2026). Con los datos de ejemplo, en los últimos 7 días Jugador 3 lleva 5 de 5 en CS2 y, a
SMITE 2, solo ha jugado Jugador 4. Lo que quedó y lo que se aprendió:
- **`?periodo=7d|30d|todo`** (no `desde`: con tres opciones basta y la URL se lee mejor). Periodos móviles: los últimos
  7 o 30 días desde ahora, no la semana o el mes del calendario.
- **El periodo recorta la foto entera**: en `EquipoServicio`, la foto del equipo se recorta antes de calcular nada, así
  que todo sale ya del periodo, también la media del equipo con la que se compara (sería injusto comparar tu semana con
  la temporada del resto). Lo admiten perfil, detalle, historial, sinergias, sesiones, consejos, cara a cara y ranking.
  El equipo y los dúos, no (no lo pedía la propuesta).
- **Sin partidas en el periodo no es un 404**: los datos vienen vacíos (0 partidas) y la web lo dice ("Jugador 1 no ha
  jugado a SMITE 2 en los últimos 7 días"). El 404 queda para quien nunca ha jugado a ese juego. El perfil se pide
  siempre con todas las partidas, para que las pestañas de juego no desaparezcan al cambiar de periodo.
- **Todo lo de la página va con el periodo**, también las recomendaciones del Duende: con 7 días suele salir "aún es
  pronto para juzgarte", y es lo honesto. Con quién y cuándo salen vacíos o casi (con pocos días no hay muestra).
- **Chat**: el periodo no podía esperar a P9. La API manda, además de todo, un resumen de los últimos 7 y 30 días de
  cada jugador y juego (con la media del equipo en esos días), y la web manda el periodo que se está viendo. Las reglas
  reconocen "esta semana", "este mes", "en total"... (la pregunta manda sobre la página) y responden con los números de
  esos días; "¿cómo voy esta semana?" los compara con los de siempre. Mapas, compañeros y sesiones se responden con
  todas las partidas y lo avisan. Gemini recibe los mismos resúmenes.
- Cambiar de periodo en la página empieza otra conversación con el Duende, como al cambiar de juego.

---

## Fase 2 · Seguimiento

### P5 · Objetivos personales

**Datos**: tabla `objetivos` (jugador, juego, métrica, valor objetivo, creado, alcanzado).

**Cambios**
- API: CRUD de objetivos. **Ojo**: hoy la web no tiene usuarios, así que cualquiera podría editar los objetivos de otro.
  Mínimo: un token por jugador en `config/equipo.json`, o login con Discord si se hace P11.
- Duende: el progreso de cada objetivo entra en las recomendaciones ("vas por 42 % de HS de 45 %") y en el chat. El
  Duende puede proponer el objetivo a partir de su recomendación más grave.
- Web: crear y ver objetivos en el perfil, con barra de progreso.

### P6 · Memoria de consejos

**Datos**: tabla `consejos_dados` (jugador, juego, id de la recomendación, valor de la métrica ese día, fecha).

**Cambios**
- API: al pedir recomendaciones, guardar las de nivel `alto`/`medio` nuevas (no repetir la misma en 7 días).
- Duende: regla `consejo_funciona` / `consejo_no_funciona` comparando el valor de entonces con el actual ("hace dos
  semanas te dije que usaras más utilidad: has pasado de 85 a 120"). El historial va al prompt del chat.
- Sirve además para medir qué consejos funcionan y ajustar `textos.py`.

**Hecho** (9 de octubre de 2026). Con los datos de ejemplo, a Jugador 3: "Mejora en ADR. Hace 12 días te avisé: «Poco
daño por ronda». Entonces tenías 78; en las 11 partidas desde entonces, 103"; y a Jugador 4: "Muertes / partida sigue
sin mejorar" (de 6,63 a 9,22), con el consejo de entonces otra vez. Lo que quedó y lo que se aprendió:
- **Se compara el valor de entonces con el de las partidas jugadas desde entonces**, no con el de ahora con todas: el
  de todas cambia muy despacio y la mejora no se vería. La API calcula los dos (`Estadisticas.seguimiento`); el Duende
  no inventa números.
- **Qué se apunta**: las recomendaciones de *mejorar ya* y *a vigilar* que se ven en el perfil con todas las partidas
  (con un periodo no: el valor sería el de esos días). Cada una con su métrica y su valor ese día; las que no hablan
  de una métrica (tilt, compañeros...) se apuntan igual, para medir más adelante, pero no tienen seguimiento. No se
  repite la misma en 7 días. Las del propio seguimiento no se apuntan. Las tarjetas del equipo no apuntan nada.
- **Cuándo se juzga**: con 7 días o más y 5 partidas o más desde el consejo. Para cada recomendación cuenta la primera
  vez que se dio en los últimos 60 días. Funciona si la métrica mejora como en el resto de reglas (5 puntos en
  porcentajes, un 10 % en lo demás); no funciona si no mejora nada o empeora (*mejorar ya* si empeora un 10 % o más).
  Solo se habla del que más ha mejorado y del peor.
- "Sigue sin mejorar" sustituye al aviso de siempre de esa métrica (decir las dos cosas sobra) y repite el consejo de
  entonces con un "toca insistir". Los dos van por delante en su nivel: es lo más personal que dice el Duende.
- En el chat: "¿Ha funcionado lo que me dijiste?" repasa cada consejo (funciona, sigue sin mejorar, aún es pronto o
  aún no has jugado) y está entre las preguntas sugeridas cuando hay algo que revisar. Gemini recibe el seguimiento.
- Datos de ejemplo: a Jugador 3 se le avisó del ADR hace 12 días (y viene mejorando); a Jugador 4, de las muertes hace
  25 días, y desde entonces muere más (`DemoSeeder` lo genera así).
- Migración `V3__consejos_dados.sql`.
- **Queda pendiente**: un informe de qué consejos funcionan más (los datos ya están en `consejos_dados`) para ajustar
  los textos; y que la memoria distinga a quién se le enseñó el consejo (hoy cuenta como dado cuando cualquiera abre el
  perfil; con P5 habrá usuarios).

### P7 · Valoración de las respuestas

- Web: 👍/👎 bajo cada respuesta del chat y cada recomendación.
- API: tabla `valoraciones` (tipo, id de recomendación o hash de respuesta, voto, origen gemini/reglas).
- Uso: revisar las peor valoradas para cambiar reglas o el prompt de `personalidad.py`.

---

## Fase 3 · Mejores datos de las fuentes actuales

### P8 · Comparar con jugadores de tu nivel (FACEIT)

**Por qué**: las referencias de `metricas.py` son inventadas. Con datos reales se puede decir "tu ADR está en el
percentil 30 de los jugadores de nivel 6".

**Datos**: cada partida de FACEIT ya trae las estadísticas de los 10 jugadores (`mapearEstadisticas` las devuelve todas),
pero `Sincronizador.guardar` se queda solo con las del equipo.
- Guardar las del resto **sin identificarlos** (sin nick; como mucho un hash del id) en una tabla de muestras:
  juego, nivel, mapa, métricas.
- El nivel de cada jugador viene en los detalles de la partida (`GET /matches/{id}`, roster con `game_skill_level`). Y el
  nivel y el ELO del propio jugador, en `GET /players/{id}` (`games.cs2.skill_level`, `faceit_elo`). **Comprobar ambos
  campos con una respuesta real.**

**Cambios**
- API: guardar muestras, calcular percentiles por nivel y métrica, y mandárselos al Duende en lugar de las referencias
  fijas. Nivel y ELO en el perfil.
- Duende: en `insights.py`, comparar con el percentil (debilidad si < 30, fortaleza si > 70). Las referencias fijas se
  quedan solo como respaldo cuando no hay muestra suficiente.
- SMITE 2: lo mismo si `getmatchdetails` de Hi-Rez da los 10 jugadores (por verificar).

### P9 · Chat que consulta la API (function calling)

**Por qué**: ahora Gemini solo ve resúmenes; no puede responder "¿cómo voy en Mirage este mes?" o "¿qué pasó en mis dos
últimas derrotas?".

**Cambios**
- Duende: declarar herramientas para Gemini (partidas con filtros de juego, mapa/dios, fechas y resultado; desglose;
  comparación; sinergias) que llamen a la API Java. Necesita `API_URL` en la configuración del Duende.
- Mantener el principio: **todo número sale de la API**; las herramientas devuelven datos, nunca texto inventado.
- Límite de llamadas a herramientas por pregunta y caché. Sin Gemini, las reglas siguen como hoy.

### P10 · Informe de cada partida

- API: tras cada sincronización, para cada partida nueva, comparar al jugador con su propia media ("tu mejor ADR del
  mes", "tercera derrota seguida en Nuke").
- Duende: texto corto con la personalidad de siempre (reglas o Gemini).
- Web: en el historial, cada partida con su comentario. Es la materia prima de P11.

---

## Fase 4 · Llevar al Duende fuera de la web

### P11 · El Duende en Discord

- Ya existe un bot de Discord del grupo en `../ElDuende/bot-discord` (otro repositorio, con Gemini). Coordinar con él en
  vez de crear otro bot.
- API: endpoint de novedades (`GET /api/novedades?desde=`) o un webhook al terminar cada sincronización.
- Contenido: informe de cada partida (P10), resumen semanal con el mejor y el peor de la semana, y avisos de objetivos
  cumplidos (P5).
- Opcional: login con Discord en la web, que además resuelve la autenticación de P5.

---

## Fase 5 · El gran salto

### P12 · Análisis de demos de CS2

**Por qué**: es lo que hacen csstats y Leetify. Con la demo se sabe qué pasó ronda a ronda.

**Métricas nuevas**: KAST, trades (dados y recibidos), duelos de apertura, asistencias de flash, utilidad por ronda,
rendimiento de CT frente a T, economía y mapa de calor de dónde mueres por mapa. Con eso, un rating propio tipo HLTV.

**Datos**
- FACEIT da la URL de la demo en los detalles de la partida (`demo_url`). **Comprobar** si la descarga necesita permiso
  aparte (API de descargas de FACEIT) y el tamaño de cada demo.
- Para quien no juega en FACEIT: Valve permite enlazar la cuenta con el código de autenticación del juego y los códigos
  para compartir partidas, pero hace falta un cliente de Steam que hable con el coordinador del juego. Mucho más trabajo.

**Cambios**
- Python: un trabajador aparte (por ejemplo `analisis/`, con `demoparser2`) que descarga, analiza y guarda métricas por
  ronda. Las demos no se guardan para siempre.
- API: tablas por ronda y nuevas métricas en el resumen; el desglose CT/T y por zona.
- Duende: reglas nuevas ("mueres el 40 % de las veces en A main sin que te tradeen").
- Web: mapa de calor por mapa y la parte por ronda en el detalle de cada partida.

---

## Al implementar cualquiera

- **Contrato compartido**: si cambia un dato que viaja entre servicios, tocar a la vez `api/.../duende/DuendeModelos.java`,
  `duende/app/modelos.py` (alias camelCase) y `frontend/src/app/core/modelos.ts`.
- **Textos en los dos idiomas**: `frontend/src/app/core/textos.ts` (EN tiene el mismo tipo que ES: si falta una
  traducción no compila) y `duende/app/textos.py`.
- **Migraciones**: siempre una nueva `V{n}__*.sql`, compatible con PostgreSQL y con H2 en modo PostgreSQL.
- **Datos de ejemplo**: ampliar `DemoSeeder` para que la propuesta se vea sin claves.
- **Tests** en los tres servicios: `pytest` (duende), `./mvnw test` (api), `npm test` (frontend).
- **Registro**: al terminar, la entrega en `CHANGELOG.md` (qué se nota, cómo se configura, cambios por servicio, tests y
  cómo actualizar).
- **Privacidad**: los datos de rivales (P8) se guardan sin nick y solo agregados; nada personal en los prompts.
