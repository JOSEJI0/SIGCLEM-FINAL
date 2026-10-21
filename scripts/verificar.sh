#!/bin/sh
# Verificación posterior al despliegue (lista de salida de la fase de pruebas).
# Uso: sh scripts/verificar.sh [URL base]   p. ej. sh scripts/verificar.sh https://sigclem.chilpancingo.tecnm.mx
# Sin URL usa http://localhost:WEB_PORT (o https://localhost:HTTPS_PORT si hay certificado).
set -u
. "$(dirname "$0")/comun.sh"

if [ -n "${1:-}" ]; then
    BASE=${1%/}
elif [ "$HTTPS" = 1 ]; then
    p=$(leer_env HTTPS_PORT); BASE="https://localhost:${p:-443}"
else
    p=$(leer_env WEB_PORT); BASE="http://localhost:${p:-80}"
fi

fallas=0
ok()    { echo "  [OK]    $1"; }
falla() { echo "  [FALLA] $1"; fallas=$((fallas + 1)); }
aviso() { echo "  [AVISO] $1"; }
obtener() { curl -ks --max-time 15 "$@"; }

echo "Verificando SIGCLEM en $BASE"

echo "Contenedores:"
for s in db api web; do
    e=$(salud_servicio "$s")
    case "$e" in
        healthy|running) ok "$s: $e" ;;
        *) falla "$s: ${e:-detenido}" ;;
    esac
done

echo "Aplicación:"
if obtener "$BASE/api/v1/salud" | grep -q '"ok"'; then ok "API responde (/api/v1/salud)"; else falla "La API no responde en $BASE/api/v1/salud"; fi

codigo=$(obtener -o /dev/null -w '%{http_code}' "$BASE/")
[ "$codigo" = 200 ] && ok "Interfaz web (HTTP 200)" || falla "Interfaz web devolvió HTTP $codigo"

if obtener "$BASE/api/v1/pruebas/estado" | grep -q '"modoPruebas":false'; then
    ok "Modo pruebas desactivado"
else
    falla "MODO PRUEBAS ACTIVO: la API acepta peticiones sin iniciar sesión (MODO_PRUEBAS=false en .env)"
fi

codigo=$(obtener -o /dev/null -w '%{http_code}' "$BASE/api/v1/docs")
[ "$codigo" = 404 ] && ok "Documentación Swagger desactivada" || falla "Swagger disponible (HTTP $codigo); poner API_DOCS=false"

codigo=$(obtener -o /dev/null -w '%{http_code}' "$BASE/api/v1/grupos")
[ "$codigo" = 401 ] && ok "Endpoints protegidos exigen sesión (HTTP 401)" || falla "GET /api/v1/grupos sin sesión devolvió HTTP $codigo (se esperaba 401)"

cabeceras=$(obtener -I "$BASE/")
echo "$cabeceras" | grep -qi '^x-content-type-options: nosniff' && ok "Cabeceras de seguridad" || falla "Faltan cabeceras de seguridad"
case "$BASE" in
    https://*) echo "$cabeceras" | grep -qi '^strict-transport-security' && ok "HSTS activo" || falla "Falta HSTS" ;;
    *) aviso "Sin HTTPS: las contraseñas viajan sin cifrar (ver DESPLIEGUE.md, sección HTTPS)" ;;
esac

echo "Configuración:"
jwt=$(leer_env JWT_SECRET)
[ "${#jwt}" -ge 32 ] && ok "JWT_SECRET de ${#jwt} caracteres" || falla "JWT_SECRET vacío o menor de 32 caracteres"
if grep -q 'cambia_esta' "$SIGCLEM_ENV" 2>/dev/null; then falla "Hay contraseñas de ejemplo en $SIGCLEM_ENV"; else ok "Sin contraseñas de ejemplo"; fi
[ -n "$(leer_env COORDINADOR_CONTRASENA)" ] && aviso "COORDINADOR_CONTRASENA sigue en $SIGCLEM_ENV; bórrala tras el primer arranque"

echo "Respaldos:"
ultimo=$(ls -1d "$(leer_env RESPALDOS_DIR | sed 's/^$/.\/respaldos/')"/20* 2>/dev/null | tail -n 1)
[ -n "$ultimo" ] && ok "Último respaldo: $ultimo" || aviso "Aún no hay respaldos (sh scripts/respaldo.sh)"

libre=$(df -Pk . | awk 'NR==2 {print int($4/1024/1024)}')
[ "${libre:-0}" -ge 5 ] && ok "Espacio libre en disco: ${libre} GB" || aviso "Poco espacio libre en disco: ${libre} GB"

echo
if [ "$fallas" -eq 0 ]; then
    echo "Verificación correcta."
else
    echo "$fallas verificación(es) fallaron."
    exit 1
fi
