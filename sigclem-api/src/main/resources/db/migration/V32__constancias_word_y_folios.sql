ALTER TABLE constancia
  ADD COLUMN archivo_word VARCHAR(255) NULL;

ALTER TABLE registro_lengua_materna
  ADD COLUMN folio_constancia VARCHAR(20) NULL,
  ADD COLUMN constancia_word VARCHAR(255) NULL,
  ADD UNIQUE KEY uq_registro_lengua_folio_constancia (folio_constancia);

CREATE TABLE folio_constancia_usado (
  folio VARCHAR(20) NOT NULL,
  fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (folio)
);

INSERT INTO folio_constancia_usado (folio)
SELECT UPPER(folio)
FROM constancia
WHERE folio IS NOT NULL AND estatus = 'Emitida';
