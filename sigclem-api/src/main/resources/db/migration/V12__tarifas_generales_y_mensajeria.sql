-- V12: tarifas generales por concepto y mensajería entre alumnos, facilitadores y coordinación (16/10/2026).

-- 1) Todos los niveles pagan lo mismo: cada concepto tiene un solo monto general (sin nivel).
ALTER TABLE tarifa_pago DROP CHECK ck_tarifa_nivel;

-- Conceptos que solo tenían tarifas por nivel: se crea su tarifa general con el monto de su tarifa más reciente,
-- activa si el concepto está activo.
INSERT INTO tarifa_pago (concepto, id_concepto, id_nivel, monto, activa)
SELECT c.nombre, c.id_concepto, NULL,
       (SELECT t.monto FROM (SELECT id_concepto, monto, activa, id_tarifa FROM tarifa_pago) t
         WHERE t.id_concepto = c.id_concepto ORDER BY t.activa DESC, t.id_tarifa DESC LIMIT 1),
       c.activo
FROM concepto_pago c
WHERE EXISTS (SELECT 1 FROM (SELECT id_concepto, id_nivel FROM tarifa_pago) n
              WHERE n.id_concepto = c.id_concepto AND n.id_nivel IS NOT NULL)
  AND NOT EXISTS (SELECT 1 FROM (SELECT id_concepto, id_nivel FROM tarifa_pago) g
                  WHERE g.id_concepto = c.id_concepto AND g.id_nivel IS NULL);

-- Las tarifas por nivel quedan como historial (inactivas).
UPDATE tarifa_pago SET activa = FALSE WHERE id_nivel IS NOT NULL;

-- 2) Mensajería: un envío a varios destinatarios (difusión) comparte el mismo lote; mensajes más largos.
ALTER TABLE notificacion
  ADD COLUMN lote CHAR(36) NULL,
  MODIFY COLUMN mensaje VARCHAR(2000) NOT NULL;
CREATE INDEX ix_notificacion_lote ON notificacion (lote);
