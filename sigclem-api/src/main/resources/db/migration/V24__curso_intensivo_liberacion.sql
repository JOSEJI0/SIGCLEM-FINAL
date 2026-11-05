INSERT INTO concepto_pago (nombre, requiere_nivel, activo)
SELECT 'Curso intensivo', FALSE, TRUE
WHERE NOT EXISTS (
    SELECT 1 FROM concepto_pago WHERE nombre = 'Curso intensivo'
);

INSERT INTO tarifa_pago (concepto, id_concepto, id_nivel, monto, activa)
SELECT c.nombre, c.id_concepto, NULL, 8000.00, TRUE
FROM concepto_pago c
WHERE c.nombre = 'Curso intensivo'
  AND NOT EXISTS (
      SELECT 1 FROM tarifa_pago t
      WHERE t.id_concepto = c.id_concepto
        AND t.id_nivel IS NULL
        AND t.activa = TRUE
  );
