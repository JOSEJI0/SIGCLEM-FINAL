#!/bin/sh
# Instala o actualiza SIGCLEM SIN Docker en Ubuntu 22.04/24.04 o Debian 12 (ver DESPLIEGUE.md).
# Uso, dentro de la carpeta del paquete (generado con nativo/empaquetar.sh):
#   sudo sh instalar.sh
# Primera vez: instala Java 21, MySQL 8, Nginx y Tesseract; crea la base, las cuentas de MySQL, el usuario del
# sistema, /etc/sigclem/sigclem.env con contraseñas aleatorias, el servicio sigclem-api y el sitio de Nginx.
# Siguientes veces: respalda, reemplaza la API y la interfaz, y reinicia (Flyway aplica las migraciones nuevas).
set -eu
[ "$(id -u)" = 0 ] || { echo "Ejecuta con sudo."; exit 1; }
cd "$(dirname "$0")"
[ -f app.jar ] && [ -d web ] || { echo "Falta app.jar o web/: ejecuta este script dentro del paquete."; exit 1; }

ENV=/etc/sigclem/sigclem.env
CON_SYSTEMD=0
[ -d /run/systemd/system ] && CON_SYSTEMD=1

servicio() {  # servicio <nombre> <start|restart|enable>
    if [ "$CON_SYSTEMD" = 1 ]; then
        if [ "$2" = enable ]; then systemctl enable --now "$1"; else systemctl "$2" "$1"; fi
    else
        [ "$2" = enable ] && set -- "$1" start
        service "$1" "$2" || true
    fi
}

aleatoria() { echo "Sg$(openssl rand -hex 10)9"; }

echo "=== SIGCLEM: instalación sin Docker ==="
if ! command -v java >/dev/null || ! command -v mysqld >/dev/null || ! command -v nginx >/dev/null; then
    echo "Instalando Java 21, MySQL, Nginx y Tesseract..."
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    apt-get install -y -qq openjdk-21-jre-headless mysql-server nginx tesseract-ocr tesseract-ocr-spa \
        fontconfig fonts-dejavu-core curl openssl >/dev/null
fi
servicio mysql enable
servicio nginx enable

id sigclem >/dev/null 2>&1 || useradd --system --home /opt/sigclem --shell /usr/sbin/nologin sigclem
mkdir -p /opt/sigclem /srv/sigclem/archivos /etc/sigclem /var/www/sigclem
chown -R sigclem:sigclem /srv/sigclem

PRIMERA=0
if [ ! -f "$ENV" ]; then
    PRIMERA=1
    echo "Creando $ENV con contraseñas aleatorias..."
    COORD=$(aleatoria); JEFA=$(aleatoria)
    sed -e "s#__DB_APP_PASS__#$(aleatoria)#" -e "s#__DB_FLYWAY_PASS__#$(aleatoria)#" \
        -e "s#__JWT_SECRET__#$(openssl rand -base64 48 | tr -d '\n')#" \
        -e "s#__COORDINADOR_CONTRASENA__#$COORD#" -e "s#__JEFA_CONTRASENA__#$JEFA#" sigclem.env.ejemplo > "$ENV"
    chmod 600 "$ENV"
fi
leer() { sed -n "s/^$1=//p" "$ENV" | tail -n 1; }

echo "Preparando la base de datos..."
mysql <<SQL
CREATE DATABASE IF NOT EXISTS sigclem CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'sigclem_flyway'@'localhost' IDENTIFIED BY '$(leer FLYWAY_PASS)';
CREATE USER IF NOT EXISTS 'sigclem_flyway'@'127.0.0.1' IDENTIFIED BY '$(leer FLYWAY_PASS)';
GRANT ALL PRIVILEGES ON sigclem.* TO 'sigclem_flyway'@'localhost', 'sigclem_flyway'@'127.0.0.1';
CREATE USER IF NOT EXISTS 'sigclem_app'@'localhost' IDENTIFIED BY '$(leer DB_PASS)';
CREATE USER IF NOT EXISTS 'sigclem_app'@'127.0.0.1' IDENTIFIED BY '$(leer DB_PASS)';
GRANT SELECT, INSERT, UPDATE, DELETE ON sigclem.* TO 'sigclem_app'@'localhost', 'sigclem_app'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL

if [ "$PRIMERA" = 0 ] && [ -f /opt/sigclem/app.jar ]; then
    echo "Respaldo previo a la actualización..."
    sh ./respaldo.sh
fi

echo "Instalando la API y la interfaz..."
install -m 644 app.jar /opt/sigclem/app.jar
for s in respaldo.sh restaurar.sh; do install -m 755 "$s" /opt/sigclem/"$s"; done
rm -rf /var/www/sigclem/*
cp -r web/. /var/www/sigclem/

# El sitio se instala solo la primera vez: certbot lo modifica para HTTPS y una actualización no debe borrarlo.
[ -f /etc/nginx/sites-available/sigclem ] || install -m 644 nginx-sigclem.conf /etc/nginx/sites-available/sigclem
ln -sf /etc/nginx/sites-available/sigclem /etc/nginx/sites-enabled/sigclem
rm -f /etc/nginx/sites-enabled/default
nginx -t -q
servicio nginx reload

if [ "$CON_SYSTEMD" = 1 ]; then
    install -m 644 sigclem-api.service /etc/systemd/system/sigclem-api.service
    systemctl daemon-reload
    systemctl enable sigclem-api >/dev/null 2>&1
    systemctl restart sigclem-api
else
    # Sin systemd (contenedor o WSL sin systemd): se ejecuta en segundo plano.
    pkill -f "/opt/sigclem/app.jar" 2>/dev/null || true
    # La configuración la lee root (sigclem.env es 600) y la API corre como el usuario sigclem.
    (set -a; . "$ENV"; set +a; cd /opt/sigclem
     nohup runuser -u sigclem -- java $JAVA_OPTS -jar /opt/sigclem/app.jar > /srv/sigclem/api.log 2>&1 &)
fi

echo "Esperando a la API (migraciones y arranque)..."
t=0
until curl -fs http://127.0.0.1:8080/api/v1/salud >/dev/null 2>&1; do
    t=$((t + 5))
    if [ "$t" -gt 300 ]; then
        echo "La API no respondió en 5 minutos. Revisa: journalctl -u sigclem-api -n 100"
        exit 1
    fi
    sleep 5
done
echo "API lista."
curl -fs http://127.0.0.1/api/v1/pruebas/estado | grep -q '"modoPruebas":false' && echo "Modo pruebas: desactivado" \
    || echo "AVISO: modo pruebas activo"

if [ "$PRIMERA" = 1 ]; then
    cat <<MSG

Instalación terminada. Cuentas iniciales (anótalas y cámbialas al entrar, en «Cuentas y contraseñas»):
  coordinador / $COORD
  jefa.dgtv   / $JEFA
Después borra COORDINADOR_CONTRASENA y JEFA_CONTRASENA de $ENV.
Edita CORS_ORIGINS en $ENV con la dirección del sistema y reinicia: sudo systemctl restart sigclem-api
HTTPS: sudo apt install certbot python3-certbot-nginx && sudo certbot --nginx -d tu.dominio
Respaldo diario (sudo crontab -e):  0 2 * * * sh /opt/sigclem/respaldo.sh >> /var/log/sigclem-respaldo.log 2>&1
MSG
else
    echo "Actualización terminada."
fi
