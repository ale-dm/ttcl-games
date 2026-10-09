# Registro de cambios

Lo que se ha entregado, de lo más reciente a lo más antiguo. Lo que falta por hacer está en
[docs/propuestas.md](docs/propuestas.md) (P1–P12); al terminar una propuesta se marca allí y se anota aquí.

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
