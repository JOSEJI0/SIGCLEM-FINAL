SET @migration_sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupo' AND COLUMN_NAME = 'intensivo') = 0,
  'ALTER TABLE grupo ADD COLUMN intensivo BOOLEAN NOT NULL DEFAULT FALSE AFTER clave',
  'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inscripcion' AND COLUMN_NAME = 'id_nivel_acreditado') = 0,
  'ALTER TABLE inscripcion ADD COLUMN id_nivel_acreditado INT UNSIGNED NULL AFTER calificacion_final',
  'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @migration_sql = IF(
  (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
   WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'inscripcion'
     AND CONSTRAINT_NAME = 'fk_inscripcion_nivel_acreditado') = 0,
  'ALTER TABLE inscripcion ADD CONSTRAINT fk_inscripcion_nivel_acreditado
   FOREIGN KEY (id_nivel_acreditado) REFERENCES nivel (id_nivel)',
  'SELECT 1'
);
PREPARE migration_statement FROM @migration_sql;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

INSERT INTO modalidad (nombre)
SELECT 'Intensivo virtual'
WHERE NOT EXISTS (SELECT 1 FROM modalidad WHERE nombre = 'Intensivo virtual');

INSERT INTO aula (nombre)
SELECT 'Virtual'
WHERE NOT EXISTS (SELECT 1 FROM aula WHERE nombre = 'Virtual');
