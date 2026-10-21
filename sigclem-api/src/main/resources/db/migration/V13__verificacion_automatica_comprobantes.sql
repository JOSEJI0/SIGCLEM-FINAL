-- Verificación automática de comprobantes de pago (CFDI):
-- lectura del QR en el servidor, consulta del estado ante el SAT y cotejo de la descripción del recibo.
ALTER TABLE comprobante_pago
  ADD COLUMN origen_datos       VARCHAR(15)  NULL,
  ADD COLUMN sello_final        VARCHAR(8)   NULL,
  ADD COLUMN estado_sat         VARCHAR(20)  NULL,
  ADD COLUMN detalle_sat        VARCHAR(200) NULL,
  ADD COLUMN fecha_consulta_sat DATETIME     NULL,
  ADD COLUMN cotejo_descripcion VARCHAR(15)  NULL,
  ADD COLUMN detalle_cotejo     VARCHAR(400) NULL,
  ADD COLUMN descripcion_leida  VARCHAR(500) NULL,
  ADD CONSTRAINT ck_comprobante_origen
    CHECK (origen_datos IS NULL OR origen_datos IN ('QR servidor', 'QR alumno', 'Alumno', 'Coordinación')),
  ADD CONSTRAINT ck_comprobante_estado_sat
    CHECK (estado_sat IS NULL OR estado_sat IN ('Vigente', 'Cancelado', 'No encontrado', 'No disponible', 'Sin verificar')),
  ADD CONSTRAINT ck_comprobante_cotejo
    CHECK (cotejo_descripcion IS NULL OR cotejo_descripcion IN ('Coincide', 'Parcial', 'No coincide', 'Sin texto'));
