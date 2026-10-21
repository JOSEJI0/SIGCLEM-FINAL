ALTER TABLE notificacion
  ADD COLUMN id_usuario_origen INT UNSIGNED NULL AFTER id_usuario_destino,
  ADD CONSTRAINT fk_notificacion_origen FOREIGN KEY (id_usuario_origen) REFERENCES usuario (id_usuario);
