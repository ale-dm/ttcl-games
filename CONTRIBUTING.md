# Guía de colaboración en TTCL Games

¡Gracias por tu interés en colaborar con TTCL Games! Este documento recopila la información necesaria para levantar el proyecto en local, pasar los tests y seguir los convenios de arquitectura y desarrollo.

---

## 1. Puesta en marcha rápida (sin claves externas)

El proyecto está diseñado para funcionar en desarrollo local sin requerir credenciales externas (como la clave de Gemini o acceso a FACEIT). En modo local se utilizan datos de prueba deterministas (`DemoSeeder`) y un motor de recomendaciones por reglas.

### Requisitos previos
- **Java 21**
- **Python 3.11+**
- **Node.js** (v24 recomendado) y npm
- *(Opcional)* **Docker** y Docker Compose

### Levantar los servicios individualmente

1. **API (Spring Boot + H2 en memoria):**
   ```bash
   cd api
   ./mvnw spring-boot:run
   ```
   *(En Windows puedes usar `mvnw.cmd spring-boot:run`). La API arranca por defecto con base de datos H2 en memoria y carga datos de prueba automáticos.*

2. **Duende (FastAPI):**
   ```bash
   cd duende
   python -m venv .venv
   # Windows: .venv\Scripts\activate | Linux/macOS: source .venv/bin/activate
   pip install -r requirements.txt
   uvicorn app.main:app --port 8000
   ```

3. **Análisis de demos (FastAPI):**
   ```bash
   cd analisis
   python -m venv .venv
   # Windows: .venv\Scripts\activate | Linux/macOS: source .venv/bin/activate
   pip install -r requirements.txt
   uvicorn app.main:app --port 8001
   ```

4. **Frontend (Angular 21):**
   ```bash
   cd frontend
   npm install
   npm start
   ```
   La interfaz queda disponible en [http://localhost:4200](http://localhost:4200).

### Levantar todo con Docker
Si prefieres usar contenedores:
```bash
docker compose up --build
```

---

## 2. Ejecución de pruebas

Cada uno de los cuatro servicios cuenta con su propia suite de pruebas automatizadas:

| Servicio | Comando de tests |
|---|---|
| **API** | `cd api && ./mvnw test` |
| **Duende** | `cd duende && pytest` |
| **Análisis** | `cd analisis && pytest` |
| **Frontend** | `cd frontend && npm test` |

> [!NOTE]
> **Usuarios de Windows con ruta de usuario con tildes o «ñ»:**  
> La JDK puede tener conflictos abriendo selectores NIO con ciertas rutas temporales por defecto. La API incluye `config/CarpetaTemporal.java` para resolverlo automáticamente, y las pruebas emplean un Duende simulado sin dependencias problemáticas de mocks complejos.

---

## 3. Reglas de arquitectura y desarrollo

Para mantener la coherencia y estabilidad del proyecto, solicitamos cumplir con las siguientes normas:

1. **Contrato tripartito sincronizado:**
   - La estructura de datos compartida entre los servicios debe mantenerse idéntica:  
     `api/.../duende/DuendeModelos.java` ↔ `duende/app/modelos.py` ↔ `frontend/src/app/core/modelos.ts`.
   - Si se añade, renombra o elimina un campo en uno, deben actualizarse los tres a la vez.

2. **Bilingüismo estricto (ES / EN):**
   - Cualquier texto visible al usuario final debe existir en ambos idiomas:
     - En el frontend: `frontend/src/app/core/textos.ts`.
     - En el Duende: `duende/app/textos.py`.

3. **Migraciones de base de datos:**
   - Las nuevas migraciones deben seguir el formato `V{n}__*.sql`.
   - Deben ser compatibles tanto con **PostgreSQL** como con **H2** (en modo PostgreSQL).

4. **Determinismo de datos de ejemplo (`DemoSeeder`):**
   - `DemoSeeder` utiliza semilla y secuencias fijas para garantizar que los patrones estadísticos (rachas, horas pico, tilt) sean reproducibles en los tests.
   - No alteres los datos base existentes. Si necesitas agregar nuevas entidades o muestras, hazlo al final y mediante un generador independiente.

5. **Idioma del proyecto:**
   - Nombres de variables de dominio, funciones, comentarios y mensajes de commit se escriben en **español**.

6. **Propuestas y registro de cambios:**
   - Las funcionalidades planificadas se documentan en `docs/propuestas.md`.
   - Al completar una propuesta o cambio de alcance, actualiza `docs/propuestas.md` indicando lo aprendido y anota la entrega en `CHANGELOG.md`.

---

## 4. Cómo proponer cambios

- **Bugs y mejoras menores:** Puedes abrir directamente un issue o enviar un pull request con la solución y sus correspondientes pruebas.
- **Nuevas propuestas o cambios estructurales grandes:** Abre primero un issue explicando el alcance (qué datos nuevos aporta, cómo impacta en cada servicio y cómo lo consume el Duende) antes de implementar código sustancial.
