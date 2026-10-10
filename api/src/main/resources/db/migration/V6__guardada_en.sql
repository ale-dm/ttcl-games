-- P11: cuándo se guardó cada partida, para dar las novedades desde la última vez que se preguntó (una partida jugada
-- ayer y sincronizada hoy es una novedad de hoy). Las que ya estaban cuentan como guardadas al jugarse.
ALTER TABLE partidas ADD COLUMN guardada_en TIMESTAMP WITH TIME ZONE;
UPDATE partidas SET guardada_en = jugada_en;
ALTER TABLE partidas ALTER COLUMN guardada_en SET NOT NULL;
CREATE INDEX partidas_guardada_idx ON partidas (guardada_en);
