ALTER TABLE constancia
  ADD COLUMN motivo_rechazo VARCHAR(200) NULL,
  ADD CONSTRAINT ck_constancia_motivo
    CHECK (estatus <> 'Rechazada' OR motivo_rechazo IS NOT NULL);

ALTER TABLE notificacion
  ADD COLUMN id_constancia INT UNSIGNED NULL,
  ADD CONSTRAINT fk_notificacion_constancia
    FOREIGN KEY (id_constancia) REFERENCES constancia (id_constancia);

CREATE INDEX ix_notificacion_constancia ON notificacion (id_constancia);
