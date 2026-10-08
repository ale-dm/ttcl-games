# Propuestas para mejorar los datos y el Duende

Hoja de ruta para que el Duende dé consejos más útiles. Hoy su límite son los datos, no la IA: cada propuesta dice qué
dato añade, qué cambia en cada servicio y cómo lo aprovecha el Duende. Al terminar una, marcarla en la tabla y anotar
lo que se aprendió en su apartado.

## Estado

| ID | Propuesta | Fase | Esfuerzo | Datos | Estado |
|---|---|---|---|---|---|
| P1 | Rol del jugador | 1 | Bajo | Nuevos (los da el jugador) | Pendiente |
| P2 | Con quién juegas mejor (sinergias) | 1 | Bajo | Ya guardados | Pendiente |
| P3 | Sesiones y tilt | 1 | Bajo | Ya guardados | Pendiente |
| P4 | Filtros por periodo | 1 | Bajo | Ya guardados | Pendiente |
| P5 | Objetivos personales | 2 | Medio | Nuevos (los da el jugador) | Pendiente |
| P6 | Memoria de consejos | 2 | Medio | Se generan | Pendiente |
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

1. No sabe el rol de cada uno: puede regañar a un soporte por hacer pocas kills.
2. Las referencias de "jugador medio" son números fijos, no gente de tu nivel.
3. No sabe con quién juegas, cuándo ni cuántas seguidas, aunque esos datos ya están en la base.
4. No recuerda qué te dijo ni si sirvió.
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

### P2 · Con quién juegas mejor (sinergias)

**Datos**: ya están. Cada partida guarda a todos los del equipo que la jugaron (`participaciones`).

**Cambios**
- API: en `Estadisticas`, winrate y K/D de un jugador agrupado por compañero (y "solo"), con mínimo de 3 partidas.
  Endpoint `GET /api/jugadores/{slug}/sinergias?juego=`. Añadirlo a `JuegoContexto` para el Duende.
- Duende: reglas `companero_bueno` / `companero_malo` en `insights.py` (diferencia ≥ 15 pp frente a su winrate global) y
  pregunta nueva en `reglas_chat.py` ("¿con quién juego mejor?").
- Web: tarjeta "Con quién" en el perfil y, en el equipo, el mejor dúo y el mejor trío.

**Hecho cuando**: el Duende puede decir "con Jugador 3 ganas el 65 %, sin él el 38 %" con datos de ejemplo.

### P3 · Sesiones y tilt

**Datos**: ya están (`jugada_en` y `duracion_seg`). Una sesión son partidas separadas por menos de ~45 minutos.

**Cambios**
- API: en `Estadisticas`, rendimiento por número de partida dentro de la sesión (1ª, 2ª, 3ª+), después de una derrota y
  por franja horaria. Va al contexto del Duende.
- Duende: reglas `tilt_sesion` ("a partir de la 3ª seguida tu winrate cae del 55 al 35 %") y `mejor_horario`, con
  consejo concreto (parar tras dos derrotas seguidas, jugar a su mejor hora).
- `DemoSeeder`: dar a algún jugador de ejemplo un patrón de tilt para que se vea.

**Hecho cuando**: con datos de ejemplo sale al menos un aviso de tilt bien justificado y ninguno con menos de ~15 sesiones.

### P4 · Filtros por periodo

**Cambios**
- API: parámetro `desde` (o `periodo=7d|30d|todo`) en perfil, detalle, partidas, comparar y ranking. Las funciones de
  `Estadisticas` ya trabajan sobre listas: basta con filtrar antes.
- Web: selector de periodo en perfil, comparar y ranking (en la URL, como el juego).
- Duende: el chat reconoce "esta semana" o "este mes" en `reglas_chat.py`; con Gemini llega con P9.

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
- **Privacidad**: los datos de rivales (P8) se guardan sin nick y solo agregados; nada personal en los prompts.
