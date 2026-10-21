ALTER TABLE constancia
  ADD COLUMN idioma_en_tramite VARCHAR(30)
    GENERATED ALWAYS AS (
      CASE
        WHEN estatus IN ('Solicitada', 'Autorizada', 'Emitida') THEN LOWER(idioma)
        ELSE NULL
      END
    ) STORED;

CREATE UNIQUE INDEX uk_constancia_alumno_idioma_activo
  ON constancia (numero_control, idioma_en_tramite);
