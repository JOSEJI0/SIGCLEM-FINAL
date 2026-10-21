#!/bin/sh
# Genera el paquete de instalación SIN Docker (ver DESPLIEGUE.md, sección "Instalación sin Docker").
# Se ejecuta en el equipo de desarrollo (Git Bash en Windows o Linux) con JDK 21 y Node 22:
#   sh nativo/empaquetar.sh
# Resultado: sigclem-paquete.tar.gz con la API (app.jar), la interfaz compilada (web/) y los scripts de instalación.
set -eu
RAIZ=$(cd "$(dirname "$0")/.." && pwd)
SALIDA="$RAIZ/sigclem-paquete"
rm -rf "$SALIDA" "$RAIZ/sigclem-paquete.tar.gz"
mkdir -p "$SALIDA"

echo "Compilando la API..."
cd "$RAIZ/sigclem-api"
if [ -f mvnw.cmd ] && [ "${OS:-}" = Windows_NT ]; then ./mvnw.cmd -q clean package -DskipTests; else sh ./mvnw -q clean package -DskipTests; fi
cp target/sigclem-api-*.jar "$SALIDA/app.jar"

echo "Compilando la interfaz..."
cd "$RAIZ/sigclem-web"
[ -d node_modules ] || npm ci
npm run build
cp -r dist "$SALIDA/web"

cp "$RAIZ"/nativo/instalar.sh "$RAIZ"/nativo/respaldo.sh "$RAIZ"/nativo/restaurar.sh \
   "$RAIZ"/nativo/sigclem-api.service "$RAIZ"/nativo/nginx-sigclem.conf "$RAIZ"/nativo/sigclem.env.ejemplo "$SALIDA/"
cd "$RAIZ"
tar -czf sigclem-paquete.tar.gz sigclem-paquete
rm -rf "$SALIDA"
echo "Paquete listo: $RAIZ/sigclem-paquete.tar.gz ($(du -h sigclem-paquete.tar.gz | cut -f1))"
echo "Cópialo al servidor y ejecuta: tar -xzf sigclem-paquete.tar.gz && cd sigclem-paquete && sudo sh instalar.sh"
