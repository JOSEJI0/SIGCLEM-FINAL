CREATE TABLE historial_academico (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alumno_id VARCHAR(50) NOT NULL, -- Asumo que tu numero_control es String/VARCHAR
    nivel VARCHAR(50) NOT NULL,
    periodo VARCHAR(100),
    facilitador VARCHAR(150),
    grupo VARCHAR(50),
    horario VARCHAR(100),
    aula VARCHAR(50),
    calificacion DECIMAL(5,2),
    fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_historial_alumno FOREIGN KEY (alumno_id) REFERENCES alumno(numero_control) ON DELETE CASCADE
);

-- Opcional: Agregar columna 'liberado' a la tabla alumnos si no la tienes
ALTER TABLE alumno ADD COLUMN liberado BOOLEAN DEFAULT FALSE;