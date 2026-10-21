-- V21 (cambio 15): amplía las formas de liberación del idioma permitidas en el origen del avance.
-- Antes solo se permitían «Curso», «Examen de colocación» y «Examen de cuatro habilidades»; ahora también
-- «Examen global», «Lengua materna» y «Otra» (liberación manual por el coordinador).
ALTER TABLE avance_nivel DROP CHECK ck_avance_origen;
ALTER TABLE avance_nivel ADD CONSTRAINT ck_avance_origen CHECK (origen IS NULL OR origen IN
  ('Curso', 'Examen de colocación', 'Examen de cuatro habilidades', 'Examen global', 'Lengua materna', 'Otra'));
