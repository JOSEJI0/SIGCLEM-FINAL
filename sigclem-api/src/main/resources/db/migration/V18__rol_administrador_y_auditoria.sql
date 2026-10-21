-- V18 (cambio 14): rol de administrador y bitácora de auditoría.
-- El administrador es el usuario de mayor privilegio (p. ej. quien desarrolló o mantiene el sistema). Entra por el
-- login normal (contraseña con BCrypt) y, mediante la jerarquía de roles, puede hacer todo lo del coordinador;
-- además, ve la bitácora de auditoría. No hay accesos ocultos: cada acción sensible queda registrada.

INSERT INTO rol (nombre) VALUES ('Administrador');

-- Bitácora de acciones sensibles (inicios de sesión, cambios de contraseña, cierres de acta, cambios de nivel…).
-- Es de solo inserción desde la aplicación; se consulta en la pantalla de Auditoría.
CREATE TABLE registro_auditoria (
  id_registro  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  fecha        DATETIME        NOT NULL,
  usuario      VARCHAR(50)     NULL,        -- nombre de usuario; NULL si la cuenta no existe (login fallido)
  rol          VARCHAR(30)     NULL,
  accion       VARCHAR(40)     NOT NULL,    -- código de la acción (INICIO_SESION, CONTRASENA_ASIGNADA, …)
  resultado    VARCHAR(12)     NOT NULL,    -- OK o RECHAZADO
  detalle      VARCHAR(500)    NULL,
  ip           VARCHAR(45)     NULL,
  CONSTRAINT pk_registro_auditoria PRIMARY KEY (id_registro),
  CONSTRAINT ck_auditoria_resultado CHECK (resultado IN ('OK','RECHAZADO'))
) ENGINE=InnoDB;
CREATE INDEX ix_auditoria_fecha ON registro_auditoria (fecha);
CREATE INDEX ix_auditoria_usuario ON registro_auditoria (usuario, fecha);
CREATE INDEX ix_auditoria_accion ON registro_auditoria (accion, fecha);
