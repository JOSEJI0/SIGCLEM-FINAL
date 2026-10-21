#!/bin/sh
# Restaura un respaldo de SIGCLEM sin Docker. Uso: sudo sh /opt/sigclem/restaurar.sh <carpeta> [--si]
set -eu
CARPETA=${1:-}
[ -n "$CARPETA" ] && [ -f "$CARPETA/sigclem.sql.gz" ] || {
    echo "Uso: sudo sh restaurar.sh <carpeta del respaldo> [--si]"; ls -1d /var/backups/sigclem/20* 2>/dev/null; exit 1; }
if [ "${2:-}" != "--si" ]; then
    printf "Se reemplazarán la base de datos y los archivos actuales. Escribe RESTAURAR para continuar: "
    read -r r; [ "$r" = RESTAURAR ] || { echo "Cancelado."; exit 1; }
fi

if [ -d /run/systemd/system ]; then systemctl stop sigclem-api; else pkill -f /opt/sigclem/app.jar || true; fi
gzip -dc "$CARPETA/sigclem.sql.gz" | mysql
if [ -f "$CARPETA/archivos.tar.gz" ]; then
    find /srv/sigclem/archivos -mindepth 1 -delete
    tar -C /srv/sigclem -xzf "$CARPETA/archivos.tar.gz"
    chown -R sigclem:sigclem /srv/sigclem/archivos
fi
if [ -d /run/systemd/system ]; then
    systemctl start sigclem-api
else
    (set -a; . /etc/sigclem/sigclem.env; set +a; cd /opt/sigclem
     nohup runuser -u sigclem -- java $JAVA_OPTS -jar /opt/sigclem/app.jar > /srv/sigclem/api.log 2>&1 &)
fi
echo "Restauración terminada; la API tarda alrededor de un minuto en arrancar."
