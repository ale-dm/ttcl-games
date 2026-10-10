# TTCL Games

Web de estadísticas del equipo TTCL (CS2 y SMITE 2) al estilo de csstats.gg, con el Duende: recomendaciones y chatbot.
Tres servicios; el navegador solo habla con la API Java y la API pasa resúmenes ya calculados al Duende. Con Gemini, el
Duende también consulta a la API (solo lectura, `duende/app/api_ttcl.py`) lo que no está en los resúmenes (P9).

- `frontend/` — Angular 21 (standalone, signals, sin zone.js). Textos en `src/app/core/textos.ts` (ES y EN).
- `api/` — Java 21, Spring Boot 4.1, JPA, Flyway. H2 en local con datos de ejemplo; Postgres con el perfil `postgres`.
- `duende/` — Python, FastAPI. Recomendaciones por reglas (`app/insights.py`) y chat con Gemini o, sin clave, con reglas.

**Hoja de ruta**: `docs/propuestas.md` (P1–P12) — lo siguiente que hay que construir para dar mejores datos al Duende.
Al terminar una propuesta, marcarla allí (con lo aprendido) y anotar la entrega en `CHANGELOG.md`. Hechas: P1, P2, P3, P4, P6, P7, P8, P9.

## Comandos

| Qué | Comando |
|---|---|
| Duende | `cd duende && .venv/Scripts/python -m uvicorn app.main:app --port 8000` |
| API | `cd api && ./mvnw spring-boot:run` (o `java -jar target/ttcl-api-0.1.0.jar` tras `./mvnw package -DskipTests`) |
| Web | `cd frontend && npm start` → http://localhost:4200 |
| Tests | `cd duende && .venv/Scripts/python -m pytest` · `cd api && ./mvnw test` · `cd frontend && npm test` |
| Todo con Docker | `docker compose up --build` |

En la app de Claude, `.claude/launch.json` tiene las tres configuraciones (`duende`, `api`, `web`). Parar la API antes
de `./mvnw package`: el jar en uso no se puede sobrescribir (ni `./mvnw clean` lo puede borrar; `./mvnw test` sí
funciona con la API arrancada).

## Convenciones

- Nombres, comentarios y commits en español; el código sigue el estilo de lo que ya hay.
- Contrato compartido en camelCase: `api/.../duende/DuendeModelos.java` ↔ `duende/app/modelos.py` ↔
  `frontend/src/app/core/modelos.ts`. Si cambia uno, cambian los tres.
- Roles por juego (P1): `Juego.java` (`roles`) ↔ `duende/app/metricas.py` (`AJUSTES_ROL`) y `textos.py`
  (`NOMBRES_ROL`) ↔ `textos.ts` (`rol.*`), y la tabla del README sale de `AJUSTES_ROL`. Ningún rol se llama igual en
  dos juegos: la web los traduce sin mirar el juego.
- Filas de las sesiones (P3): las claves de `Estadisticas.sesiones` ("1", "2", "3+", "victoria", "derrota", "manana",
  "tarde", "noche", "madrugada") ↔ `NOMBRES_MOMENTO` en `textos.py` ↔ `momento.*` en `textos.ts` (hay tests que lo vigilan).
- Periodo (P4): `?periodo=7d|30d|todo` (por defecto, todo). En `EquipoServicio` basta con pedir la foto recortada
  (`instantanea(periodo)`); el 404 es solo para quien nunca ha jugado a ese juego (`exigirPartidas`). En la web, el
  periodo va en la URL y en el contexto del chat.
- Valoraciones (P7): `GET /api/duende/valoraciones` da lo peor valorado (recomendaciones por id; respuestas por origen e
  `intencion`, que es el `Intencion` de `reglas_chat.py`). Mirarlo antes de retocar reglas, textos o el prompt.
- Nivel (P8): la tabla `muestras` guarda a los que no son del equipo sin nick ni id (ni de la partida); que siga así.
  El Duende compara con `ComparativaNivel` (lo normal en su nivel de FACEIT) si llega y, si no, con las referencias
  fijas de `metricas.py`.
- Consultas del chat (P9): las herramientas de `duende/app/herramientas.py` llaman a endpoints de la API y devuelven
  datos, nunca texto; si una pregunta necesita una cuenta (medias, winrate de un filtro), la hace la API
  (`/api/jugadores/{slug}/consulta`), no Gemini. Si cambia un endpoint que usan, cambian ellas y sus tests.
- `DemoSeeder` es determinista (semilla fija, horas en la zona del equipo): tocarlo cambia todos los números de ejemplo
  y lo que dice el Duende. `ApiTest` comprueba que sigan saliendo los patrones (tilt, hora buena, rachas, compañeros).
  Las muestras de otros jugadores (P8) van al final y con su propio generador, para no mover lo demás.
- Todo texto visible, en los dos idiomas (web y `duende/app/textos.py`).
- El Duende no inventa números: todo dato sale de la API.
- Migraciones nuevas como `V{n}__*.sql`, válidas en PostgreSQL y en H2 (modo PostgreSQL).

## Entorno (Windows del usuario)

- La ruta de usuario tiene "ñ": la JDK no puede abrir selectores NIO con la carpeta temporal por defecto. La API lo
  arregla sola (`config/CarpetaTemporal.java`). Por lo mismo, los tests usan un Duende falso escrito a mano, no Mockito.
- Node 24.12: Angular 22 pide ≥ 24.15, por eso el frontend está en Angular 21.
- No hay `mvn` global: usar `api/mvnw`.
