-- Regla de la coordinación: solo coordinación asigna o cambia la contraseña de los facilitadores.
-- Se retira la marca de contraseña temporal agregada en V15, que obligaba al facilitador a cambiarla.
ALTER TABLE usuario
  DROP COLUMN debe_cambiar_contrasena;
