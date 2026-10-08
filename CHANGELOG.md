# Registro de cambios

Lo que se ha entregado, de lo más reciente a lo más antiguo. Lo que falta por hacer está en
[docs/propuestas.md](docs/propuestas.md) (P1–P12); al terminar una propuesta se marca allí y se anota aquí.

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
