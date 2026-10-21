-- V11: encuesta de inicio de semestre (elección de curso y horario por el alumno).

-- Encuesta de inicio de semestre: el alumno elige, entre los grupos abiertos de su siguiente nivel,
--    el curso y horario que prefiere. Coordinación toma estas elecciones para asignarlo al grupo.
CREATE TABLE preferencia_grupo (
  id_preferencia INT UNSIGNED NOT NULL AUTO_INCREMENT,
  numero_control VARCHAR(10)  NOT NULL,
  id_grupo       INT UNSIGNED NOT NULL,
  id_periodo     INT UNSIGNED NOT NULL,
  fecha_registro DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  estatus        VARCHAR(10)  NOT NULL DEFAULT 'Pendiente',
  CONSTRAINT pk_preferencia PRIMARY KEY (id_preferencia),
  CONSTRAINT uk_preferencia_periodo UNIQUE (numero_control, id_periodo),
  CONSTRAINT fk_preferencia_alumno FOREIGN KEY (numero_control) REFERENCES alumno (numero_control),
  CONSTRAINT fk_preferencia_grupo FOREIGN KEY (id_grupo) REFERENCES grupo (id_grupo) ON DELETE CASCADE,
  CONSTRAINT fk_preferencia_periodo FOREIGN KEY (id_periodo) REFERENCES periodo (id_periodo),
  CONSTRAINT ck_preferencia_estatus CHECK (estatus IN ('Pendiente','Asignada'))
) ENGINE=InnoDB;
