-- Rol del jugador en cada juego (P1): entry, awp, soporte... en CS2; solo, jungla, mid... en SMITE 2.
-- Lo declara cada uno en config/equipo.json. Null si no lo ha dicho.
ALTER TABLE cuentas ADD COLUMN rol VARCHAR(20);
