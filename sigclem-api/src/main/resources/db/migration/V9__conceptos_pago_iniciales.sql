-- V9: conceptos de pago base.
-- V8 llenaba concepto_pago solo a partir de las tarifas existentes; en una base nueva (sin tarifas)
-- la tabla quedaba vacía y no era posible registrar tarifas ni cargos hasta crearlos a mano.
INSERT IGNORE INTO concepto_pago (nombre, requiere_nivel, activo) VALUES
  ('Curso de nivel', TRUE, TRUE),
  ('Examen de colocación', FALSE, TRUE),
  ('Examen de cuatro habilidades', FALSE, TRUE);
