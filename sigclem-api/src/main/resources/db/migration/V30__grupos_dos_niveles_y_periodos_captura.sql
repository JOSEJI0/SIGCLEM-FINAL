ALTER TABLE grupo
  ADD COLUMN id_nivel_secundario INT UNSIGNED NULL AFTER id_nivel,
  ADD COLUMN fecha_inicio_captura DATE NULL AFTER fecha_cierre_acta,
  ADD COLUMN fecha_fin_captura DATE NULL AFTER fecha_inicio_captura,
  ADD CONSTRAINT fk_grupo_nivel_secundario FOREIGN KEY (id_nivel_secundario) REFERENCES nivel (id_nivel);

UPDATE grupo g
JOIN periodo p ON p.id_periodo = g.id_periodo
SET g.fecha_inicio_captura = p.fecha_inicio,
    g.fecha_fin_captura = p.fecha_fin;

ALTER TABLE grupo
  MODIFY COLUMN fecha_inicio_captura DATE NOT NULL,
  MODIFY COLUMN fecha_fin_captura DATE NOT NULL;

ALTER TABLE inscripcion
  ADD COLUMN id_comprobante_secundario INT UNSIGNED NULL,
  ADD COLUMN id_autorizacion_secundaria INT UNSIGNED NULL,
  ADD COLUMN calificacion_secundaria TINYINT UNSIGNED NULL,
  ADD COLUMN avance_pendiente_secundario BOOLEAN NOT NULL DEFAULT FALSE,
  ADD CONSTRAINT uk_inscripcion_comprobante_secundario UNIQUE (id_comprobante_secundario),
  ADD CONSTRAINT uk_inscripcion_autorizacion_secundaria UNIQUE (id_autorizacion_secundaria),
  ADD CONSTRAINT fk_inscripcion_comprobante_secundario
    FOREIGN KEY (id_comprobante_secundario) REFERENCES comprobante_pago (id_comprobante),
  ADD CONSTRAINT fk_inscripcion_autorizacion_secundaria
    FOREIGN KEY (id_autorizacion_secundaria) REFERENCES autorizacion_pago (id_autorizacion),
  ADD CONSTRAINT ck_inscripcion_calificacion_secundaria
    CHECK (calificacion_secundaria IS NULL OR calificacion_secundaria <= 100);
