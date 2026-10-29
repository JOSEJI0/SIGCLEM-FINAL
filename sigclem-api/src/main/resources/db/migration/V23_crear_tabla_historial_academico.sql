CREATE TABLE historial_academico (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alumno_id BIGINT NOT NULL,
    nivel VARCHAR(50) NOT NULL, -- 'Básico 1', 'Intermedio 3', etc.
    periodo VARCHAR(100),
    facilitador VARCHAR(150),
    grupo VARCHAR(50),
    horario VARCHAR(100),
    aula VARCHAR(50),
    calificacion DECIMAL(5,2),
    fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_historial_alumno FOREIGN KEY (alumno_id) REFERENCES alumnos(id) ON DELETE CASCADE
);

-- Opcional: Agregar columna 'liberado' a la tabla alumnos si no la tienes
ALTER TABLE alumnos ADD COLUMN liberado BOOLEAN DEFAULT FALSE;