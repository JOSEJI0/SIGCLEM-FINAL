-- V17 (cambio 13): cierre del acta por coordinación aunque haya cursos sin liquidar, asignación posterior del nivel
-- y actualización del semestre por el propio alumno.

-- Cierre del acta: quién la cerró y la observación obligatoria cuando la cierra coordinación.
ALTER TABLE grupo
  ADD COLUMN cerrada_por        VARCHAR(12)  NULL AFTER fecha_cierre_acta,
  ADD COLUMN observacion_cierre VARCHAR(500) NULL AFTER cerrada_por,
  ADD CONSTRAINT ck_grupo_cerrada_por CHECK (cerrada_por IS NULL OR cerrada_por IN ('Facilitador','Coordinación'));

-- El alumno que no había liquidado su curso al cerrar el acta conserva su calificación, pero su nivel queda
-- pendiente hasta que coordinación lo asigna de forma individual.
ALTER TABLE inscripcion
  ADD COLUMN avance_pendiente BOOLEAN NOT NULL DEFAULT FALSE;

-- Bitácora de los cambios de nivel hechos por coordinación (quién, cuándo, por qué y el estado del pago).
CREATE TABLE ajuste_avance (
  id_ajuste       INT UNSIGNED     NOT NULL AUTO_INCREMENT,
  numero_control  VARCHAR(10)      NOT NULL,
  id_nivel        INT UNSIGNED     NOT NULL,
  id_inscripcion  INT UNSIGNED     NULL,
  tipo            VARCHAR(20)      NOT NULL,
  estatus_nuevo   VARCHAR(12)      NULL,
  estatus_pago    VARCHAR(12)      NULL,
  observacion     VARCHAR(500)     NOT NULL,
  usuario         VARCHAR(80)      NOT NULL,
  fecha           DATETIME         NOT NULL,
  CONSTRAINT pk_ajuste_avance PRIMARY KEY (id_ajuste),
  CONSTRAINT fk_ajuste_alumno FOREIGN KEY (numero_control) REFERENCES alumno (numero_control),
  CONSTRAINT fk_ajuste_nivel FOREIGN KEY (id_nivel) REFERENCES nivel (id_nivel),
  CONSTRAINT fk_ajuste_inscripcion FOREIGN KEY (id_inscripcion) REFERENCES inscripcion (id_inscripcion),
  CONSTRAINT ck_ajuste_tipo CHECK (tipo IN ('Cierre sin pago','Nivel asignado'))
) ENGINE=InnoDB;
CREATE INDEX ix_ajuste_alumno ON ajuste_avance (numero_control, fecha);

-- Semestre: fecha en que el alumno lo confirmó o actualizó por última vez.
ALTER TABLE alumno
  ADD COLUMN fecha_actualizacion_semestre DATE NULL;
