-- V20 (cambio 15): concepto de pago del examen global.
-- El examen global lo solicita el alumno y cuesta $3,500; el coordinador le emite este cargo como cualquier otro
-- pago. Al aprobarlo, el coordinador libera la lengua extranjera (se registra en la auditoría y queda reflejado en
-- el avance con la forma de liberación).
INSERT IGNORE INTO concepto_pago (nombre, requiere_nivel, activo) VALUES ('Examen global', FALSE, TRUE);
