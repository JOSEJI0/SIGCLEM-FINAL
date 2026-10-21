-- V1: esquema inicial de SIGCLEM (diseño físico, apartado 4.3 y Anexo A del documento)

-- ---------- Seguridad ----------
CREATE TABLE rol (
  id_rol   INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre   VARCHAR(30)  NOT NULL,
  CONSTRAINT pk_rol PRIMARY KEY (id_rol),
  CONSTRAINT uk_rol_nombre UNIQUE (nombre)
) ENGINE=InnoDB;

CREATE TABLE usuario (
  id_usuario      INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre_usuario  VARCHAR(50)  NOT NULL,
  contrasena_hash VARCHAR(100) NOT NULL,
  id_rol          INT UNSIGNED NOT NULL,
  activo          BOOLEAN      NOT NULL DEFAULT TRUE,
  CONSTRAINT pk_usuario PRIMARY KEY (id_usuario),
  CONSTRAINT uk_usuario_nombre UNIQUE (nombre_usuario),
  CONSTRAINT fk_usuario_rol FOREIGN KEY (id_rol) REFERENCES rol (id_rol)
) ENGINE=InnoDB;

-- ---------- Catálogos ----------
CREATE TABLE carrera (
  id_carrera INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre     VARCHAR(80)  NOT NULL,
  clave_plan VARCHAR(20)  NOT NULL,
  CONSTRAINT pk_carrera PRIMARY KEY (id_carrera),
  CONSTRAINT uk_carrera_nombre UNIQUE (nombre)
) ENGINE=InnoDB;

CREATE TABLE nivel (
  id_nivel   INT UNSIGNED     NOT NULL AUTO_INCREMENT,
  numero     TINYINT UNSIGNED NOT NULL,
  clave      VARCHAR(10)      NOT NULL,
  nombre     VARCHAR(20)      NOT NULL,
  nivel_mcer CHAR(2)          NOT NULL,
  horas      SMALLINT UNSIGNED NOT NULL,
  CONSTRAINT pk_nivel PRIMARY KEY (id_nivel),
  CONSTRAINT uk_nivel_numero UNIQUE (numero),
  CONSTRAINT uk_nivel_clave UNIQUE (clave),
  CONSTRAINT ck_nivel_numero CHECK (numero BETWEEN 1 AND 10),
  CONSTRAINT ck_nivel_mcer CHECK (nivel_mcer IN ('A1','A2','B1')),
  CONSTRAINT ck_nivel_horas CHECK (horas > 0)
) ENGINE=InnoDB;

CREATE TABLE periodo (
  id_periodo   INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre       VARCHAR(30)  NOT NULL,
  fecha_inicio DATE         NOT NULL,
  fecha_fin    DATE         NOT NULL,
  CONSTRAINT pk_periodo PRIMARY KEY (id_periodo),
  CONSTRAINT uk_periodo_nombre UNIQUE (nombre),
  CONSTRAINT ck_periodo_fechas CHECK (fecha_fin >= fecha_inicio)
) ENGINE=InnoDB;

CREATE TABLE modalidad (
  id_modalidad INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre       VARCHAR(20)  NOT NULL,
  CONSTRAINT pk_modalidad PRIMARY KEY (id_modalidad),
  CONSTRAINT uk_modalidad_nombre UNIQUE (nombre)
) ENGINE=InnoDB;

CREATE TABLE aula (
  id_aula INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre  VARCHAR(30)  NOT NULL,
  CONSTRAINT pk_aula PRIMARY KEY (id_aula),
  CONSTRAINT uk_aula_nombre UNIQUE (nombre)
) ENGINE=InnoDB;

CREATE TABLE criterio_evaluacion (
  id_criterio INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre      VARCHAR(30)  NOT NULL,
  CONSTRAINT pk_criterio PRIMARY KEY (id_criterio),
  CONSTRAINT uk_criterio_nombre UNIQUE (nombre)
) ENGINE=InnoDB;

CREATE TABLE lengua_materna (
  id_lengua INT UNSIGNED NOT NULL AUTO_INCREMENT,
  nombre    VARCHAR(40)  NOT NULL,
  CONSTRAINT pk_lengua PRIMARY KEY (id_lengua),
  CONSTRAINT uk_lengua_nombre UNIQUE (nombre)
) ENGINE=InnoDB;

-- ---------- Personas ----------
CREATE TABLE alumno (
  numero_control    VARCHAR(10)      NOT NULL,
  id_usuario        INT UNSIGNED     NOT NULL,
  nombre            VARCHAR(60)      NOT NULL,
  apellido_paterno  VARCHAR(40)      NOT NULL,
  apellido_materno  VARCHAR(40)      NULL,
  id_carrera        INT UNSIGNED     NOT NULL,
  semestre          TINYINT UNSIGNED NOT NULL,
  correo            VARCHAR(80)      NULL,
  fecha_inscripcion DATE             NOT NULL,
  CONSTRAINT pk_alumno PRIMARY KEY (numero_control),
  CONSTRAINT uk_alumno_usuario UNIQUE (id_usuario),
  CONSTRAINT fk_alumno_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
  CONSTRAINT fk_alumno_carrera FOREIGN KEY (id_carrera) REFERENCES carrera (id_carrera),
  CONSTRAINT ck_alumno_numero CHECK (numero_control REGEXP '^[A-Z0-9]+$'),
  CONSTRAINT ck_alumno_semestre CHECK (semestre BETWEEN 1 AND 12)
) ENGINE=InnoDB;
CREATE INDEX ix_alumno_nombre ON alumno (apellido_paterno, apellido_materno, nombre);

CREATE TABLE facilitador (
  id_facilitador   INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_usuario       INT UNSIGNED NOT NULL,
  nombre           VARCHAR(60)  NOT NULL,
  apellido_paterno VARCHAR(40)  NOT NULL,
  apellido_materno VARCHAR(40)  NULL,
  correo           VARCHAR(80)  NOT NULL,
  telefono         VARCHAR(15)  NULL,
  CONSTRAINT pk_facilitador PRIMARY KEY (id_facilitador),
  CONSTRAINT uk_facilitador_usuario UNIQUE (id_usuario),
  CONSTRAINT uk_facilitador_correo UNIQUE (correo),
  CONSTRAINT fk_facilitador_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
) ENGINE=InnoDB;
CREATE INDEX ix_facilitador_nombre ON facilitador (apellido_paterno, apellido_materno, nombre);

-- ---------- Grupos y horarios ----------
CREATE TABLE grupo (
  id_grupo          INT UNSIGNED     NOT NULL AUTO_INCREMENT,
  clave             VARCHAR(2)       NOT NULL,
  id_nivel          INT UNSIGNED     NOT NULL,
  id_periodo        INT UNSIGNED     NOT NULL,
  id_modalidad      INT UNSIGNED     NOT NULL,
  id_facilitador    INT UNSIGNED     NOT NULL,
  id_aula           INT UNSIGNED     NOT NULL,
  cupo              TINYINT UNSIGNED NOT NULL,
  estatus_acta      VARCHAR(10)      NOT NULL DEFAULT 'Abierta',
  fecha_cierre_acta DATE             NULL,
  CONSTRAINT pk_grupo PRIMARY KEY (id_grupo),
  CONSTRAINT uk_grupo UNIQUE (id_periodo, id_nivel, id_modalidad, clave),
  CONSTRAINT fk_grupo_nivel FOREIGN KEY (id_nivel) REFERENCES nivel (id_nivel),
  CONSTRAINT fk_grupo_periodo FOREIGN KEY (id_periodo) REFERENCES periodo (id_periodo),
  CONSTRAINT fk_grupo_modalidad FOREIGN KEY (id_modalidad) REFERENCES modalidad (id_modalidad),
  CONSTRAINT fk_grupo_facilitador FOREIGN KEY (id_facilitador) REFERENCES facilitador (id_facilitador),
  CONSTRAINT fk_grupo_aula FOREIGN KEY (id_aula) REFERENCES aula (id_aula),
  CONSTRAINT ck_grupo_clave CHECK (clave REGEXP '^[A-Z]{1,2}$'),
  CONSTRAINT ck_grupo_cupo CHECK (cupo > 0),
  CONSTRAINT ck_grupo_acta CHECK (estatus_acta IN ('Abierta','En captura','Cerrada')),
  CONSTRAINT ck_grupo_cierre CHECK ((estatus_acta = 'Cerrada') = (fecha_cierre_acta IS NOT NULL))
) ENGINE=InnoDB;
CREATE INDEX ix_grupo_facilitador ON grupo (id_facilitador, id_periodo);

CREATE TABLE horario (
  id_horario  INT UNSIGNED     NOT NULL AUTO_INCREMENT,
  id_grupo    INT UNSIGNED     NOT NULL,
  dia_semana  TINYINT UNSIGNED NOT NULL,
  hora_inicio TIME             NOT NULL,
  hora_fin    TIME             NOT NULL,
  CONSTRAINT pk_horario PRIMARY KEY (id_horario),
  CONSTRAINT fk_horario_grupo FOREIGN KEY (id_grupo) REFERENCES grupo (id_grupo) ON DELETE CASCADE,
  CONSTRAINT ck_horario_dia CHECK (dia_semana BETWEEN 1 AND 6),
  CONSTRAINT ck_horario_rango CHECK (hora_inicio >= '10:00:00' AND hora_fin <= '17:00:00' AND hora_fin > hora_inicio)
) ENGINE=InnoDB;
CREATE INDEX ix_horario_dia ON horario (dia_semana, hora_inicio, hora_fin);

-- ---------- Pagos ----------
CREATE TABLE autorizacion_pago (
  id_autorizacion  INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  numero_control   VARCHAR(10)   NOT NULL,
  concepto         VARCHAR(30)   NOT NULL,
  id_nivel         INT UNSIGNED  NULL,
  id_periodo       INT UNSIGNED  NOT NULL,
  monto_esperado   DECIMAL(10,2) NOT NULL,
  fecha_emision    DATE          NOT NULL,
  id_usuario_emite INT UNSIGNED  NOT NULL,
  CONSTRAINT pk_autorizacion PRIMARY KEY (id_autorizacion),
  CONSTRAINT fk_autorizacion_alumno FOREIGN KEY (numero_control) REFERENCES alumno (numero_control),
  CONSTRAINT fk_autorizacion_nivel FOREIGN KEY (id_nivel) REFERENCES nivel (id_nivel),
  CONSTRAINT fk_autorizacion_periodo FOREIGN KEY (id_periodo) REFERENCES periodo (id_periodo),
  CONSTRAINT fk_autorizacion_usuario FOREIGN KEY (id_usuario_emite) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_autorizacion_concepto CHECK (concepto IN ('Curso de nivel','Examen de colocación','Examen de cuatro habilidades')),
  CONSTRAINT ck_autorizacion_nivel CHECK ((concepto = 'Curso de nivel') = (id_nivel IS NOT NULL)),
  CONSTRAINT ck_autorizacion_monto CHECK (monto_esperado > 0)
) ENGINE=InnoDB;

CREATE TABLE comprobante_pago (
  id_comprobante    INT UNSIGNED  NOT NULL AUTO_INCREMENT,
  id_autorizacion   INT UNSIGNED  NOT NULL,
  uuid_cfdi         CHAR(36)      NOT NULL,
  rfc_emisor        VARCHAR(13)   NOT NULL,
  total             DECIMAL(10,2) NOT NULL,
  contenido_qr      VARCHAR(255)  NULL,
  archivo           VARCHAR(255)  NOT NULL,
  fecha_carga       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  estatus           VARCHAR(15)   NOT NULL DEFAULT 'Pendiente',
  motivo_rechazo    VARCHAR(200)  NULL,
  id_usuario_valida INT UNSIGNED  NULL,
  CONSTRAINT pk_comprobante PRIMARY KEY (id_comprobante),
  CONSTRAINT uk_comprobante_uuid UNIQUE (uuid_cfdi),
  CONSTRAINT fk_comprobante_autorizacion FOREIGN KEY (id_autorizacion) REFERENCES autorizacion_pago (id_autorizacion),
  CONSTRAINT fk_comprobante_usuario FOREIGN KEY (id_usuario_valida) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_comprobante_uuid CHECK (uuid_cfdi REGEXP '^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$'),
  CONSTRAINT ck_comprobante_rfc CHECK (rfc_emisor = 'TNM140723GFA'),
  CONSTRAINT ck_comprobante_estatus CHECK (estatus IN ('Pendiente','Validado','Rechazado')),
  CONSTRAINT ck_comprobante_motivo CHECK (estatus <> 'Rechazado' OR motivo_rechazo IS NOT NULL)
) ENGINE=InnoDB;
CREATE INDEX ix_comprobante_estatus ON comprobante_pago (estatus, fecha_carga);

-- ---------- Inscripciones y avance ----------
CREATE TABLE inscripcion (
  id_inscripcion     INT UNSIGNED     NOT NULL AUTO_INCREMENT,
  numero_control     VARCHAR(10)      NOT NULL,
  id_grupo           INT UNSIGNED     NOT NULL,
  id_comprobante     INT UNSIGNED     NOT NULL,
  oportunidad        CHAR(2)          NOT NULL DEFAULT '01',
  calificacion_final TINYINT UNSIGNED NULL,
  CONSTRAINT pk_inscripcion PRIMARY KEY (id_inscripcion),
  CONSTRAINT uk_inscripcion_alumno_grupo UNIQUE (numero_control, id_grupo),
  CONSTRAINT uk_inscripcion_comprobante UNIQUE (id_comprobante),
  CONSTRAINT fk_inscripcion_alumno FOREIGN KEY (numero_control) REFERENCES alumno (numero_control),
  CONSTRAINT fk_inscripcion_grupo FOREIGN KEY (id_grupo) REFERENCES grupo (id_grupo),
  CONSTRAINT fk_inscripcion_comprobante FOREIGN KEY (id_comprobante) REFERENCES comprobante_pago (id_comprobante),
  CONSTRAINT ck_inscripcion_oportunidad CHECK (oportunidad IN ('01','R1')),
  CONSTRAINT ck_inscripcion_calificacion CHECK (calificacion_final IS NULL OR calificacion_final <= 100)
) ENGINE=InnoDB;
CREATE INDEX ix_inscripcion_grupo ON inscripcion (id_grupo);

CREATE TABLE avance_nivel (
  numero_control VARCHAR(10)  NOT NULL,
  id_nivel       INT UNSIGNED NOT NULL,
  estatus        VARCHAR(12)  NOT NULL DEFAULT 'No permitido',
  origen         VARCHAR(30)  NULL,
  fecha_registro DATE         NOT NULL,
  CONSTRAINT pk_avance PRIMARY KEY (numero_control, id_nivel),
  CONSTRAINT fk_avance_alumno FOREIGN KEY (numero_control) REFERENCES alumno (numero_control) ON DELETE CASCADE,
  CONSTRAINT fk_avance_nivel FOREIGN KEY (id_nivel) REFERENCES nivel (id_nivel),
  CONSTRAINT ck_avance_estatus CHECK (estatus IN ('No permitido','Habilitado','Cursando','Acreditado','Reprobado','Exento')),
  CONSTRAINT ck_avance_origen CHECK (origen IS NULL OR origen IN ('Curso','Examen de colocación','Examen de cuatro habilidades'))
) ENGINE=InnoDB;

-- ---------- Exámenes ----------
CREATE TABLE examen (
  id_examen             INT UNSIGNED      NOT NULL AUTO_INCREMENT,
  numero_control        VARCHAR(10)       NOT NULL,
  tipo                  VARCHAR(20)       NOT NULL,
  fecha_hora_aplicacion DATETIME          NOT NULL,
  puntaje_total         SMALLINT UNSIGNED NULL,
  observaciones         TEXT              NULL,
  id_usuario_evaluador  INT UNSIGNED      NULL,
  firma_evaluador       VARCHAR(255)      NULL,
  id_nivel_asignado     INT UNSIGNED      NULL,
  CONSTRAINT pk_examen PRIMARY KEY (id_examen),
  CONSTRAINT fk_examen_alumno FOREIGN KEY (numero_control) REFERENCES alumno (numero_control),
  CONSTRAINT fk_examen_evaluador FOREIGN KEY (id_usuario_evaluador) REFERENCES usuario (id_usuario),
  CONSTRAINT fk_examen_nivel FOREIGN KEY (id_nivel_asignado) REFERENCES nivel (id_nivel),
  CONSTRAINT ck_examen_tipo CHECK (tipo IN ('Colocación','Cuatro habilidades'))
) ENGINE=InnoDB;
CREATE INDEX ix_examen_fecha ON examen (fecha_hora_aplicacion);

CREATE TABLE resultado_criterio (
  id_examen   INT UNSIGNED     NOT NULL,
  id_criterio INT UNSIGNED     NOT NULL,
  puntaje     TINYINT UNSIGNED NOT NULL,
  nota        VARCHAR(255)     NULL,
  CONSTRAINT pk_resultado PRIMARY KEY (id_examen, id_criterio),
  CONSTRAINT fk_resultado_examen FOREIGN KEY (id_examen) REFERENCES examen (id_examen) ON DELETE CASCADE,
  CONSTRAINT fk_resultado_criterio FOREIGN KEY (id_criterio) REFERENCES criterio_evaluacion (id_criterio),
  CONSTRAINT ck_resultado_puntaje CHECK (puntaje <= 6)
) ENGINE=InnoDB;

-- ---------- Constancias ----------
CREATE TABLE constancia (
  id_constancia       INT UNSIGNED      NOT NULL AUTO_INCREMENT,
  numero_control      VARCHAR(10)       NOT NULL,
  folio               VARCHAR(20)       NULL,
  fecha_solicitud     DATE              NOT NULL,
  fecha_expedicion    DATE              NULL,
  idioma              VARCHAR(30)       NOT NULL,
  niveles_acreditados TINYINT UNSIGNED  NOT NULL,
  total_horas         SMALLINT UNSIGNED NOT NULL,
  nivel_mcer          CHAR(2)           NOT NULL,
  estatus             VARCHAR(12)       NOT NULL DEFAULT 'Solicitada',
  id_usuario_autoriza INT UNSIGNED      NULL,
  archivo_pdf         VARCHAR(255)      NULL,
  CONSTRAINT pk_constancia PRIMARY KEY (id_constancia),
  CONSTRAINT uk_constancia_folio UNIQUE (folio),
  CONSTRAINT fk_constancia_alumno FOREIGN KEY (numero_control) REFERENCES alumno (numero_control),
  CONSTRAINT fk_constancia_usuario FOREIGN KEY (id_usuario_autoriza) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_constancia_niveles CHECK (niveles_acreditados BETWEEN 1 AND 10),
  CONSTRAINT ck_constancia_horas CHECK (total_horas > 0),
  CONSTRAINT ck_constancia_mcer CHECK (nivel_mcer IN ('A1','A2','B1','B2')),
  CONSTRAINT ck_constancia_estatus CHECK (estatus IN ('Solicitada','Autorizada','Rechazada','Emitida')),
  CONSTRAINT ck_constancia_folio CHECK (estatus IN ('Solicitada','Rechazada') OR folio IS NOT NULL),
  CONSTRAINT ck_constancia_pdf CHECK (estatus <> 'Emitida' OR (archivo_pdf IS NOT NULL AND fecha_expedicion IS NOT NULL))
) ENGINE=InnoDB;
CREATE INDEX ix_constancia_estatus ON constancia (estatus, fecha_solicitud);

-- ---------- Lenguas maternas ----------
CREATE TABLE registro_lengua_materna (
  id_registro       INT UNSIGNED NOT NULL AUTO_INCREMENT,
  numero_control    VARCHAR(10)  NOT NULL,
  id_lengua         INT UNSIGNED NOT NULL,
  nivel_dominio     VARCHAR(20)  NULL,
  documento         VARCHAR(255) NOT NULL,
  estatus           VARCHAR(10)  NOT NULL DEFAULT 'Pendiente',
  fecha_registro    DATE         NOT NULL,
  id_usuario_valida INT UNSIGNED NULL,
  CONSTRAINT pk_registro_lengua PRIMARY KEY (id_registro),
  CONSTRAINT uk_registro_lengua UNIQUE (numero_control, id_lengua),
  CONSTRAINT fk_registro_alumno FOREIGN KEY (numero_control) REFERENCES alumno (numero_control),
  CONSTRAINT fk_registro_lengua FOREIGN KEY (id_lengua) REFERENCES lengua_materna (id_lengua),
  CONSTRAINT fk_registro_usuario FOREIGN KEY (id_usuario_valida) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_registro_estatus CHECK (estatus IN ('Pendiente','Validado','Rechazado'))
) ENGINE=InnoDB;

-- ---------- Evaluación de diagnóstico y notificaciones ----------
CREATE TABLE evaluacion_diagnostica (
  id_diagnostico        INT UNSIGNED     NOT NULL AUTO_INCREMENT,
  numero_control        VARCHAR(10)      NOT NULL,
  fecha_solicitud       DATE             NOT NULL,
  fecha_hora_aplicacion DATETIME         NULL,
  enlace                VARCHAR(255)     NULL,
  puntaje               TINYINT UNSIGNED NULL,
  recomendacion         VARCHAR(20)      NULL,
  observaciones         TEXT             NULL,
  estatus               VARCHAR(12)      NOT NULL DEFAULT 'Solicitada',
  id_usuario_programa   INT UNSIGNED     NULL,
  CONSTRAINT pk_diagnostico PRIMARY KEY (id_diagnostico),
  CONSTRAINT fk_diagnostico_alumno FOREIGN KEY (numero_control) REFERENCES alumno (numero_control),
  CONSTRAINT fk_diagnostico_usuario FOREIGN KEY (id_usuario_programa) REFERENCES usuario (id_usuario),
  CONSTRAINT ck_diagnostico_puntaje CHECK (puntaje IS NULL OR puntaje <= 100),
  CONSTRAINT ck_diagnostico_recomendacion CHECK (recomendacion IS NULL OR recomendacion IN ('Básico 1','Colocación')),
  CONSTRAINT ck_diagnostico_estatus CHECK (estatus IN ('Solicitada','Programada','Concluida')),
  CONSTRAINT ck_diagnostico_programa CHECK (estatus = 'Solicitada' OR (fecha_hora_aplicacion IS NOT NULL AND enlace IS NOT NULL))
) ENGINE=InnoDB;
CREATE INDEX ix_diagnostico_estatus ON evaluacion_diagnostica (estatus);

CREATE TABLE notificacion (
  id_notificacion    INT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_usuario_destino INT UNSIGNED NOT NULL,
  id_diagnostico     INT UNSIGNED NULL,
  asunto             VARCHAR(100) NOT NULL,
  mensaje            VARCHAR(500) NOT NULL,
  fecha_envio        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  leida              BOOLEAN      NOT NULL DEFAULT FALSE,
  CONSTRAINT pk_notificacion PRIMARY KEY (id_notificacion),
  CONSTRAINT fk_notificacion_usuario FOREIGN KEY (id_usuario_destino) REFERENCES usuario (id_usuario),
  CONSTRAINT fk_notificacion_diagnostico FOREIGN KEY (id_diagnostico) REFERENCES evaluacion_diagnostica (id_diagnostico)
) ENGINE=InnoDB;
CREATE INDEX ix_notificacion_bandeja ON notificacion (id_usuario_destino, leida, fecha_envio);
