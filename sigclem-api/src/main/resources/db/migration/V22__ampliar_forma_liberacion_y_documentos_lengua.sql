-- V22: Permite formas libres de liberación en avance_nivel y soporte para los documentos de lengua materna.
ALTER TABLE avance_nivel DROP CHECK ck_avance_origen;
ALTER TABLE avance_nivel ADD CONSTRAINT ck_avance_origen CHECK (origen IS NULL OR LENGTH(origen) <= 60);

ALTER TABLE registro_lengua_materna
  MODIFY COLUMN documento VARCHAR(255) NULL,
  ADD COLUMN documento_curp VARCHAR(255) NULL,
  ADD COLUMN documento_domicilio VARCHAR(255) NULL,
  ADD COLUMN documento_ine VARCHAR(255) NULL,
  ADD COLUMN documento_intercultural VARCHAR(255) NULL,
  ADD COLUMN constancia_acreditacion VARCHAR(255) NULL;
