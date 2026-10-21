-- Contraseña temporal: las cuentas creadas o restablecidas por coordinación deben cambiarla al entrar.
ALTER TABLE usuario
  ADD COLUMN debe_cambiar_contrasena BOOLEAN NOT NULL DEFAULT FALSE;

-- Los facilitadores existentes recibieron su contraseña de coordinación.
UPDATE usuario u
JOIN facilitador f ON f.id_usuario = u.id_usuario
SET u.debe_cambiar_contrasena = TRUE;
