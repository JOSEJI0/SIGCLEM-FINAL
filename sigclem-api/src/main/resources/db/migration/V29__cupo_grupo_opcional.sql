-- Historical groups may be imported before their capacity is known.
ALTER TABLE grupo
  MODIFY COLUMN cupo TINYINT UNSIGNED NULL;
