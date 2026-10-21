#!/bin/sh
# Respaldo de SIGCLEM sin Docker: base de datos y archivos. Uso: sudo sh /opt/sigclem/respaldo.sh
set -eu
ENV=/etc/sigclem/sigclem.env
leer() { sed -n "s/^$1=//p" "$ENV" | tail -n 1; }
DESTINO=$(leer RESPALDOS_DIR); DESTINO=${DESTINO:-/var/backups/sigclem}
DIAS=$(leer RESPALDOS_DIAS); DIAS=${DIAS:-30}
CARPETA="$DESTINO/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$CARPETA"
chmod 700 "$DESTINO"

mysqldump --single-transaction --routines --triggers --no-tablespaces --databases sigclem | gzip > "$CARPETA/sigclem.sql.gz"
if ! gzip -dc "$CARPETA/sigclem.sql.gz" | tail -n 1 | grep -q "Dump completed"; then
    echo "ERROR: el volcado de la base de datos está incompleto." >&2
    rm -rf "$CARPETA"
    exit 1
fi
tar -C /srv/sigclem -czf "$CARPETA/archivos.tar.gz" archivos
echo "Respaldo listo: $(du -sh "$CARPETA" | cut -f1) en $CARPETA"
find "$DESTINO" -mindepth 1 -maxdepth 1 -type d -name '20*' -mtime +"$DIAS" -exec rm -rf {} + 2>/dev/null || true
