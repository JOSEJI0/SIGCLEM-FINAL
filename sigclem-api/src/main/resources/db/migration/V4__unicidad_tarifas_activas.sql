ALTER TABLE tarifa_pago
  ADD COLUMN id_nivel_activo INT UNSIGNED GENERATED ALWAYS AS
    (CASE WHEN activa THEN COALESCE(id_nivel, 0) ELSE NULL END) STORED,
  ADD CONSTRAINT uk_tarifa_pago_activa UNIQUE (concepto, id_nivel_activo);
