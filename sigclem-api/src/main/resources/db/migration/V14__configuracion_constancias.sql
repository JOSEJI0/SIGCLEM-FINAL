-- Firmantes, textos del oficio e imágenes del membrete de las constancias, editables por coordinación.
-- Los cargos y el membrete cambian con el tiempo (los logos son por año); las constancias ya emitidas
-- conservan el PDF con el que se generaron.
CREATE TABLE configuracion_constancia (
  id_configuracion       TINYINT UNSIGNED NOT NULL,
  subdirector_nombre     VARCHAR(100) NOT NULL,
  subdirector_genero     CHAR(1)      NOT NULL,
  jefe_dgtv_nombre       VARCHAR(100) NOT NULL,
  jefe_dgtv_genero       CHAR(1)      NOT NULL,
  coordinador_nombre     VARCHAR(100) NOT NULL,
  coordinador_genero     CHAR(1)      NOT NULL,
  lema                   VARCHAR(200) NOT NULL,
  registro_coordinacion  VARCHAR(100) NOT NULL,
  prefijo_oficio         VARCHAR(60)  NOT NULL,
  copias                 VARCHAR(200) NOT NULL,
  fecha_actualizacion    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  id_usuario_actualiza   INT UNSIGNED NULL,
  CONSTRAINT pk_configuracion_constancia PRIMARY KEY (id_configuracion),
  CONSTRAINT ck_configuracion_unica CHECK (id_configuracion = 1),
  CONSTRAINT ck_configuracion_generos CHECK (subdirector_genero IN ('F', 'M') AND jefe_dgtv_genero IN ('F', 'M')
                                             AND coordinador_genero IN ('F', 'M')),
  CONSTRAINT fk_configuracion_usuario FOREIGN KEY (id_usuario_actualiza) REFERENCES usuario (id_usuario)
) ENGINE=InnoDB;

INSERT INTO configuracion_constancia (id_configuracion, subdirector_nombre, subdirector_genero, jefe_dgtv_nombre,
  jefe_dgtv_genero, coordinador_nombre, coordinador_genero, lema, registro_coordinacion, prefijo_oficio, copias)
VALUES (1, 'Yanet Evangelista Alcocer', 'F', 'María Esther Durán Figueroa', 'F', 'José Antonio Chávez Román', 'M',
  'Excelencia en Educación Tecnológica®\nCrear Tecnología es Forjar Libertad',
  'TecNM-SeyV-DVIA-CNLE-07/24-ITChilpancingo-142', '12DIT0002E/D/SPYV/DGTYV', 'c.c.p. Archivo\nYEA/MEDF');

CREATE TABLE imagen_constancia (
  tipo                VARCHAR(20)  NOT NULL,
  contenido           MEDIUMBLOB   NOT NULL,
  tipo_contenido      VARCHAR(20)  NOT NULL,
  fecha_actualizacion DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT pk_imagen_constancia PRIMARY KEY (tipo),
  CONSTRAINT ck_imagen_constancia_tipo CHECK (tipo IN ('ENCABEZADO', 'PIE', 'LOGO_COORDINACION')),
  CONSTRAINT ck_imagen_constancia_contenido CHECK (tipo_contenido IN ('image/png', 'image/jpeg'))
) ENGINE=InnoDB;
