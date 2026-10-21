CREATE TABLE tarifa_pago (
  id_tarifa INT UNSIGNED NOT NULL AUTO_INCREMENT,
  concepto  VARCHAR(30)   NOT NULL,
  id_nivel  INT UNSIGNED  NULL,
  monto     DECIMAL(10,2) NOT NULL,
  activa    BOOLEAN       NOT NULL DEFAULT TRUE,
  CONSTRAINT pk_tarifa_pago PRIMARY KEY (id_tarifa),
  CONSTRAINT fk_tarifa_nivel FOREIGN KEY (id_nivel) REFERENCES nivel (id_nivel),
  CONSTRAINT ck_tarifa_concepto CHECK (concepto IN ('Curso de nivel','Examen de colocación','Examen de cuatro habilidades')),
  CONSTRAINT ck_tarifa_nivel CHECK ((concepto = 'Curso de nivel') = (id_nivel IS NOT NULL)),
  CONSTRAINT ck_tarifa_monto CHECK (monto > 0)
) ENGINE=InnoDB;

CREATE INDEX ix_tarifa_activa ON tarifa_pago (activa, concepto, id_nivel);
