CREATE TABLE concepto_pago (
  id_concepto INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(30) NOT NULL,
  requiere_nivel BOOLEAN NOT NULL DEFAULT FALSE,
  activo BOOLEAN NOT NULL DEFAULT TRUE,
  CONSTRAINT pk_concepto_pago PRIMARY KEY (id_concepto),
  CONSTRAINT uk_concepto_pago_nombre UNIQUE (nombre)
) ENGINE=InnoDB;

INSERT INTO concepto_pago (nombre, requiere_nivel, activo)
SELECT DISTINCT concepto, concepto = 'Curso de nivel', TRUE
FROM tarifa_pago;

ALTER TABLE tarifa_pago
  ADD COLUMN id_concepto INT UNSIGNED NULL;

UPDATE tarifa_pago t
JOIN concepto_pago c ON c.nombre = t.concepto
SET t.id_concepto = c.id_concepto;

ALTER TABLE tarifa_pago
  MODIFY COLUMN id_concepto INT UNSIGNED NOT NULL,
  ADD CONSTRAINT fk_tarifa_concepto_pago
    FOREIGN KEY (id_concepto) REFERENCES concepto_pago (id_concepto),
  DROP CHECK ck_tarifa_concepto;

ALTER TABLE autorizacion_pago
  ADD COLUMN estatus VARCHAR(15) NOT NULL DEFAULT 'Pendiente',
  ADD COLUMN saldo_pendiente DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  DROP CHECK ck_autorizacion_concepto,
  DROP CHECK ck_autorizacion_nivel,
  ADD CONSTRAINT ck_autorizacion_estatus
    CHECK (estatus IN ('Pendiente', 'Parcial', 'Liquidada')),
  ADD CONSTRAINT ck_autorizacion_saldo
    CHECK (saldo_pendiente >= 0 AND saldo_pendiente <= monto_esperado);

UPDATE autorizacion_pago a
LEFT JOIN (
  SELECT id_autorizacion,
         SUM(CASE WHEN estatus = 'Validado' THEN total ELSE 0 END) AS abonado
  FROM comprobante_pago
  GROUP BY id_autorizacion
) p ON p.id_autorizacion = a.id_autorizacion
SET a.saldo_pendiente = GREATEST(a.monto_esperado - COALESCE(p.abonado, 0), 0),
    a.estatus = CASE
      WHEN COALESCE(p.abonado, 0) >= a.monto_esperado THEN 'Liquidada'
      WHEN COALESCE(p.abonado, 0) > 0 THEN 'Parcial'
      ELSE 'Pendiente'
    END;

ALTER TABLE comprobante_pago
  MODIFY COLUMN uuid_cfdi CHAR(36) NULL,
  MODIFY COLUMN rfc_emisor VARCHAR(13) NULL,
  MODIFY COLUMN total DECIMAL(10,2) NULL,
  DROP CHECK ck_comprobante_uuid,
  DROP CHECK ck_comprobante_rfc,
  ADD CONSTRAINT ck_comprobante_uuid
    CHECK (uuid_cfdi IS NULL OR uuid_cfdi REGEXP '^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$'),
  ADD CONSTRAINT ck_comprobante_rfc
    CHECK (rfc_emisor IS NULL OR rfc_emisor = 'TNM140723GFA'),
  ADD CONSTRAINT ck_comprobante_total
    CHECK (total IS NULL OR total > 0);
