#!/bin/sh
# Crea las cuentas de MySQL de SIGCLEM (se ejecuta solo la primera vez que se crea el volumen):
#  - sigclem_flyway: aplica las migraciones (DDL).
#  - sigclem_app: la usa la aplicación, solo con permisos DML (Tabla 62).
set -e
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE USER IF NOT EXISTS 'sigclem_flyway'@'%' IDENTIFIED BY '${DB_FLYWAY_PASS}';
GRANT ALL PRIVILEGES ON \`${MYSQL_DATABASE}\`.* TO 'sigclem_flyway'@'%';
CREATE USER IF NOT EXISTS 'sigclem_app'@'%' IDENTIFIED BY '${DB_APP_PASS}';
GRANT SELECT, INSERT, UPDATE, DELETE ON \`${MYSQL_DATABASE}\`.* TO 'sigclem_app'@'%';
FLUSH PRIVILEGES;
SQL
