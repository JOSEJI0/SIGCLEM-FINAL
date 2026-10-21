#!/bin/sh
# Instala o actualiza SIGCLEM en el servidor (ver DESPLIEGUE.md).
# Uso: sh deploy.sh
#  1. Revisa el archivo .env.
#  2. Si es un repositorio Git, descarga la versión nueva.
#  3. Si el sistema ya estaba en ejecución, hace un respaldo antes de actualizar.
#  4. Compila y levanta los contenedores (con HTTPS si existe certs/fullchain.pem).
#  5. Espera a la API (las migraciones de Flyway se aplican al arrancar) y verifica la instalación.
set -eu
cd "$(dirname "$0")"
. ./scripts/comun.sh

echo "=== SIGCLEM: despliegue ==="
[ -f "$SIGCLEM_ENV" ] || { echo "Falta el archivo $SIGCLEM_ENV (copia .env.example y cambia los valores)"; exit 1; }
if grep -q 'cambia_esta' "$SIGCLEM_ENV"; then
    echo "El archivo $SIGCLEM_ENV aún tiene contraseñas de ejemplo (cambia_esta...)."; exit 1
fi
jwt=$(leer_env JWT_SECRET)
[ "${#jwt}" -ge 32 ] || { echo "JWT_SECRET debe tener al menos 32 caracteres (openssl rand -base64 48)."; exit 1; }
[ "$(leer_env MODO_PRUEBAS)" = true ] && echo "AVISO: MODO_PRUEBAS=true; la API acepta peticiones sin iniciar sesión."

if [ -d .git ]; then
    echo "Descargando cambios..."
    git pull --ff-only
fi

if [ "$(salud_servicio db)" = healthy ]; then
    echo "Respaldo previo a la actualización..."
    sh scripts/respaldo.sh
fi

[ "$HTTPS" = 1 ] && echo "Certificado encontrado: se publica HTTPS." || echo "Sin certificado en $CERTS_DIR: solo HTTP."
dc up -d --build --remove-orphans

echo "Esperando a la API (migraciones y arranque)..."
if ! esperar_servicio api 300; then
    dc logs api --tail 80
    exit 1
fi
esperar_servicio web 60 || true
dc ps

echo
sh scripts/verificar.sh || true
docker image prune -f > /dev/null 2>&1 || true
echo "=== Despliegue terminado ==="
