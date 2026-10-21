-- V19 (cambio 15): sexo del alumno y baja manual de alumnos.
-- El sexo es opcional para los alumnos ya registrados y obligatorio al crear cuentas nuevas (desde la interfaz).
-- La baja es reversible: desactiva la cuenta y excluye al alumno de las listas y los reportes activos, pero
-- conserva todo su historial.
ALTER TABLE alumno
  ADD COLUMN sexo        CHAR(1)      NULL AFTER apellido_materno,
  ADD COLUMN estatus     VARCHAR(10)  NOT NULL DEFAULT 'Activo' AFTER habla_lengua_materna,
  ADD COLUMN motivo_baja VARCHAR(200) NULL AFTER estatus,
  ADD COLUMN fecha_baja  DATE         NULL AFTER motivo_baja,
  ADD CONSTRAINT ck_alumno_sexo CHECK (sexo IS NULL OR sexo IN ('H', 'M')),
  ADD CONSTRAINT ck_alumno_estatus CHECK (estatus IN ('Activo', 'Baja')),
  ADD CONSTRAINT ck_alumno_baja CHECK ((estatus = 'Baja') = (fecha_baja IS NOT NULL));
CREATE INDEX ix_alumno_estatus ON alumno (estatus);
