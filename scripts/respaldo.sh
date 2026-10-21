#!/bin/sh
# Respaldo de SIGCLEM: base de datos (mysqldump) y archivos subidos (comprobantes, documentos, logos).
# Uso: sh scripts/respaldo.sh
# Crea respaldos/AAAAMMDD-HHMMSS/ con sigclem.sql.gz y archivos.tar.gz y borra los respaldos con más de
# RESPALDOS_DIAS días (30 por omisión). Programarlo con cron (ver DESPLIEGUE.md).
set -eu
. "$(dirname "$0")/comun.sh"

DIAS=$(leer_env RESPALDOS_DIAS)
DIAS=${DIAS:-30}
DESTINO=$(leer_env RESPALDOS_DIR)
DESTINO=${DESTINO:-./respaldos}
CARPETA="$DESTINO/$(date +%Y%m%d-%H%M%S)"

if [ "$(salud_servicio db)" != healthy ]; then
    echo "La base de datos no está en ejecución; no se puede respaldar." >&2
    exit 1
fi

mkdir -p "$CARPETA"
echo "Respaldando base de datos en $CARPETA/sigclem.sql.gz ..."
dc exec -T db sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers --no-tablespaces --databases sigclem 2>/dev/null' \
    | gzip > "$CARPETA/sigclem.sql.gz"
# Un volcado válido termina con "Dump completed"; si no, el respaldo está incompleto.
if ! gzip -dc "$CARPETA/sigclem.sql.gz" | tail -n 1 | grep -q "Dump completed"; then
    echo "ERROR: el volcado de la base de datos está incompleto." >&2
    rm -rf "$CARPETA"
    exit 1
fi

if [ "$(salud_servicio api)" = healthy ]; then
    echo "Respaldando archivos en $CARPETA/archivos.tar.gz ..."
    dc exec -T api tar -C /srv/sigclem -czf - archivos > "$CARPETA/archivos.tar.gz"
else
    echo "AVISO: la API no está en ejecución; se respaldó solo la base de datos." >&2
fi

echo "Respaldo listo: $(du -sh "$CARPETA" | cut -f1) en $CARPETA"

# Retención
find "$DESTINO" -mindepth 1 -maxdepth 1 -type d -name '20*' -mtime +"$DIAS" -exec rm -rf {} + 2>/dev/null || true
