## Resumen del cambio
Describe brevemente el propósito de este pull request y qué problemas resuelve.

Closes # / Propuesta P...

## Tipo de cambio
- [ ] Corrección de error (bugfix)
- [ ] Nueva funcionalidad o propuesta
- [ ] Refactorización o mejora interna
- [ ] Documentación

## Lista de comprobación de calidad

Antes de enviar el pull request, revisa los siguientes puntos:

- [ ] **Bilingüismo:** Todos los textos visibles añadidos o modificados están en español e inglés (`frontend/src/app/core/textos.ts` y `duende/app/textos.py`).
- [ ] **Contrato tripartito:** Si se alteró el contrato de datos, se actualizaron a la vez `DuendeModelos.java`, `duende/app/modelos.py` y `frontend/src/app/core/modelos.ts`.
- [ ] **Migraciones:** Si hay nuevas migraciones (`V{n}__*.sql`), son válidas en PostgreSQL y en H2 (modo PostgreSQL).
- [ ] **Datos deterministas:** No se han descalibrado los datos base de `DemoSeeder` (nuevas muestras añadidas al final con generador propio).
- [ ] **Pruebas:** Se han ejecutado y pasan las suites de tests de los servicios afectados.
- [ ] **Documentación:** Se ha actualizado `docs/propuestas.md` (si concluye una propuesta) y `CHANGELOG.md`.
