-- V33: Importación histórica sin datos inventados.
-- 1) El semestre es opcional: los egresados y desertores ya no aparecen en la matrícula vigente y no se conoce.
--    Se admite hasta el semestre 15 porque la matrícula real incluye alumnos en semestres 13 a 15.
-- 2) El historial académico conserva la calificación tal como venía escrita ("100 (Cien)", "NA BAJA", ...)
--    y si el nivel quedó acreditado (70 o más), no acreditado (NA o menos de 70) o sin determinar.

-- Se busca la restricción por su contenido (no por nombre) porque una base restaurada puede haberla renombrado.
SET @check_semestre = (
  SELECT cc.CONSTRAINT_NAME
  FROM information_schema.CHECK_CONSTRAINTS cc
  JOIN information_schema.TABLE_CONSTRAINTS tc
    ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME
  WHERE cc.CONSTRAINT_SCHEMA = DATABASE() AND tc.TABLE_NAME = 'alumno'
    AND cc.CHECK_CLAUSE LIKE '%semestre%' AND cc.CHECK_CLAUSE NOT LIKE '%fecha%'
  LIMIT 1);
SET @migration_sql = IF(@check_semestre IS NULL, 'SELECT 1',
  CONCAT('ALTER TABLE alumno DROP CHECK `', @check_semestre, '`'));
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

ALTER TABLE alumno MODIFY COLUMN semestre TINYINT UNSIGNED NULL;
ALTER TABLE alumno ADD CONSTRAINT ck_alumno_semestre CHECK (semestre IS NULL OR semestre BETWEEN 1 AND 15);

ALTER TABLE historial_academico
  ADD COLUMN calificacion_texto VARCHAR(60) NULL AFTER calificacion,
  ADD COLUMN acreditado BOOLEAN NULL AFTER calificacion_texto;

UPDATE historial_academico
SET acreditado = CASE WHEN calificacion IS NULL THEN NULL WHEN calificacion >= 70 THEN TRUE ELSE FALSE END,
    calificacion_texto = CASE WHEN calificacion IS NULL THEN NULL ELSE CAST(calificacion AS CHAR) END
WHERE acreditado IS NULL;

-- 3) Alumnos externos (no pertenecen a una carrera del instituto) que aparecen en el concentrado.
INSERT INTO carrera (nombre, clave_plan)
SELECT 'Externo', 'EXTERNO'
WHERE NOT EXISTS (SELECT 1 FROM carrera WHERE nombre = 'Externo');
