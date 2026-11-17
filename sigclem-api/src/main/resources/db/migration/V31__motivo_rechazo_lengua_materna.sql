ALTER TABLE registro_lengua_materna
  ADD COLUMN documento_rechazado VARCHAR(20) NULL,
  ADD COLUMN motivo_rechazo VARCHAR(500) NULL;
