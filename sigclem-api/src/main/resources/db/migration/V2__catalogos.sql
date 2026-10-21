-- V2: datos iniciales de los catálogos.
-- Las claves de plan, las horas y las equivalencias MCER de los niveles deben confirmarse con la coordinación.

INSERT INTO rol (nombre) VALUES ('Alumno'), ('Facilitador'), ('Coordinador'), ('Jefa del DGTV');

INSERT INTO modalidad (nombre) VALUES ('Entre semana'), ('Sabatino'), ('Verano'), ('Invierno');

INSERT INTO criterio_evaluacion (nombre) VALUES
  ('Gramática'), ('Vocabulario'), ('Fluidez'), ('Pronunciación'), ('Interacción');

INSERT INTO lengua_materna (nombre) VALUES ('Náhuatl'), ('Me''phaa'), ('Tu''un savi');

INSERT INTO carrera (nombre, clave_plan) VALUES
  ('Ingeniería en Sistemas Computacionales', 'ISIC-2010-224'),
  ('Ingeniería Civil', 'ICIV-2010-208'),
  ('Contador Público', 'COPU-2010-205'),
  ('Ingeniería en Gestión Empresarial', 'IGEM-2009-201'),
  ('Ingeniería Informática', 'IINF-2010-220');

INSERT INTO nivel (numero, clave, nombre, nivel_mcer, horas) VALUES
  (1,  'B1', 'Básico 1',      'A1', 45),
  (2,  'B2', 'Básico 2',      'A1', 45),
  (3,  'B3', 'Básico 3',      'A1', 45),
  (4,  'B4', 'Básico 4',      'A2', 45),
  (5,  'B5', 'Básico 5',      'A2', 45),
  (6,  'I1', 'Intermedio 1',  'A2', 45),
  (7,  'I2', 'Intermedio 2',  'B1', 45),
  (8,  'I3', 'Intermedio 3',  'B1', 45),
  (9,  'I4', 'Intermedio 4',  'B1', 45),
  (10, 'I5', 'Intermedio 5',  'B1', 45);
