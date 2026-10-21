#!/bin/sh
# Funciones compartidas por los scripts de operación de SIGCLEM (se incluye con ". scripts/comun.sh").
# Variables opcionales:
#   SIGCLEM_ENV           archivo de configuración (por omisión .env en la raíz del proyecto)
#   COMPOSE_PROJECT_NAME  nombre del proyecto de Docker Compose (por omisión, el nombre de la carpeta)

RAIZ=$(cd "$(dirname "$0")" && pwd)
[ -f "$RAIZ/docker-compose.yml" ] || RAIZ=$(cd "$RAIZ/.." && pwd)
cd "$RAIZ" || exit 1
SIGCLEM_ENV=${SIGCLEM_ENV:-.env}

# Lee una variable del archivo .env sin ejecutarlo (los valores pueden tener espacios, p. ej. JAVA_OPTS).
leer_env() {
    [ -f "$SIGCLEM_ENV" ] || return 0
    sed -n "s/^$1=//p" "$SIGCLEM_ENV" | tail -n 1 | tr -d '\r' | sed 's/^"\(.*\)"$/\1/'
}

# Con certificado en CERTS_DIR se usa además la configuración HTTPS.
CERTS_DIR=$(leer_env CERTS_DIR)
CERTS_DIR=${CERTS_DIR:-./certs}
if [ -f "$CERTS_DIR/fullchain.pem" ] && [ -f "$CERTS_DIR/privkey.pem" ]; then
    HTTPS=1
else
    HTTPS=0
fi

dc() {
    if [ "$HTTPS" = 1 ]; then
        docker compose --env-file "$SIGCLEM_ENV" -f docker-compose.yml -f docker-compose.https.yml "$@"
    else
        docker compose --env-file "$SIGCLEM_ENV" -f docker-compose.yml "$@"
    fi
}

# Estado de salud de un servicio: healthy, starting, unhealthy o vacío si no está en ejecución.
salud_servicio() {
    id=$(dc ps -q "$1" 2>/dev/null)
    [ -n "$id" ] || return 0
    docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$id" 2>/dev/null
}

# Espera hasta que el servicio esté "healthy" (máximo $2 segundos).
esperar_servicio() {
    limite=${2:-300}
    t=0
    while [ "$t" -lt "$limite" ]; do
        estado=$(salud_servicio "$1")
        # "running" corresponde a un contenedor sin verificación de salud (web).
        case "$estado" in healthy|running) return 0 ;; esac
        if [ "$estado" = unhealthy ]; then
            echo "El servicio $1 quedó en estado unhealthy." >&2
            return 1
        fi
        sleep 5
        t=$((t + 5))
    done
    echo "El servicio $1 no estuvo listo en $limite s." >&2
    return 1
}
