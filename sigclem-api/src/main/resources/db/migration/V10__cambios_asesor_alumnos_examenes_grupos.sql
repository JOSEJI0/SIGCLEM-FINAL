-- V10: cambios solicitados por el asesor (15/10/2026).

-- 1) El apartado de lengua materna solo se muestra a quien declara hablar una en su registro.
ALTER TABLE alumno
  ADD COLUMN habla_lengua_materna BOOLEAN NOT NULL DEFAULT FALSE;

-- 2) Los exámenes no se borran: se ocultan (borrado lógico).
ALTER TABLE examen
  ADD COLUMN oculto BOOLEAN NOT NULL DEFAULT FALSE;

-- 3) Se elimina la restricción del horario institucional (10:00 a 17:00); solo se exige un rango válido.
ALTER TABLE horario
  DROP CHECK ck_horario_rango,
  ADD CONSTRAINT ck_horario_rango CHECK (hora_fin > hora_inicio);

-- 4) El coordinador puede asignar alumnos a un grupo antes del pago: al asignarlo se genera
--    automáticamente el cargo del nivel. La inscripción queda ligada a ese cargo y el comprobante
--    se registra después.
ALTER TABLE inscripcion
  DROP FOREIGN KEY fk_inscripcion_comprobante;
ALTER TABLE inscripcion
  MODIFY COLUMN id_comprobante INT UNSIGNED NULL,
  ADD COLUMN id_autorizacion INT UNSIGNED NULL,
  ADD CONSTRAINT fk_inscripcion_comprobante FOREIGN KEY (id_comprobante) REFERENCES comprobante_pago (id_comprobante),
  ADD CONSTRAINT uk_inscripcion_autorizacion UNIQUE (id_autorizacion),
  ADD CONSTRAINT fk_inscripcion_autorizacion FOREIGN KEY (id_autorizacion) REFERENCES autorizacion_pago (id_autorizacion);
