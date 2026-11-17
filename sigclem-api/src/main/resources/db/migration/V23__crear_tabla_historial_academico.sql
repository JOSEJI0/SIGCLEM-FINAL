-- V23: Historial académico de alumnos egresados / que ya concluyeron sus cursos.
-- Antes se llamaba "V23_crear_..." (un solo guion bajo) y Flyway la ignoraba: en una
-- instalación nueva no se creaba la tabla y la V26 fallaba. Es idempotente porque en
-- bases existentes la tabla y la columna alumno.liberado se crearon a mano.

CREATE TABLE IF NOT EXISTS historial_academico (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alumno_id VARCHAR(50) NOT NULL,
    nivel VARCHAR(50) NOT NULL,
    periodo VARCHAR(100),
    facilitador VARCHAR(150),
    grupo VARCHAR(50),
    horario VARCHAR(100),
    aula VARCHAR(50),
    calificacion DECIMAL(5,2),
    fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_historial_alumno FOREIGN KEY (alumno_id) REFERENCES alumno (numero_control) ON DELETE CASCADE
) ENGINE=InnoDB;

SET @migration_sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'alumno' AND COLUMN_NAME = 'liberado') = 0,
  'ALTER TABLE alumno ADD COLUMN liberado BOOLEAN DEFAULT FALSE',
  'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;
