#!/bin/sh
# Restaura un respaldo creado con scripts/respaldo.sh.
# Uso: sh scripts/restaurar.sh respaldos/AAAAMMDD-HHMMSS [--si]
# REEMPLAZA la base de datos y los archivos actuales. La API se detiene durante la restauración.
set -eu
. "$(dirname "$0")/comun.sh"

CARPETA=${1:-}
if [ -z "$CARPETA" ] || [ ! -f "$CARPETA/sigclem.sql.gz" ]; then
    echo "Uso: sh scripts/restaurar.sh <carpeta del respaldo> [--si]" >&2
    echo "Respaldos disponibles:" >&2
    ls -1d respaldos/20* 2>/dev/null >&2 || echo "  (ninguno)" >&2
    exit 1
fi

if [ "${2:-}" != "--si" ]; then
    echo "Se reemplazarán la base de datos y los archivos actuales con el respaldo $CARPETA."
    printf "Escribe RESTAURAR para continuar: "
    read -r respuesta
    [ "$respuesta" = RESTAURAR ] || { echo "Cancelado."; exit 1; }
fi

if [ "$(salud_servicio db)" != healthy ]; then
    echo "Iniciando la base de datos..."
    dc up -d db
    esperar_servicio db 180
fi

echo "Deteniendo la API..."
dc stop api web

echo "Restaurando base de datos..."
gzip -dc "$CARPETA/sigclem.sql.gz" | dc exec -T db sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" 2>/dev/null'

if [ -f "$CARPETA/archivos.tar.gz" ]; then
    echo "Restaurando archivos..."
    dc run --rm -T --no-deps --entrypoint sh api \
        -c 'find /srv/sigclem/archivos -mindepth 1 -delete && tar -C /srv/sigclem -xzf -' < "$CARPETA/archivos.tar.gz"
fi

echo "Iniciando la API..."
dc up -d api web
esperar_servicio api 300
echo "Restauración terminada."
