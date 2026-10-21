# Despliegue de SIGCLEM en un servidor

Guía para instalar SIGCLEM en un servidor del instituto (o en un VPS), operarlo y salir de la fase de pruebas.
Hay dos formas de instalarlo; el sistema y sus datos son los mismos en ambas:

| Forma | Cuándo usarla |
| --- | --- |
| **Con Docker** (secciones 1 a 11) | Recomendada: todo queda aislado y se actualiza con un comando. |
| **Sin Docker** (sección 12) | Si el servidor no permite Docker o el área de TI prefiere servicios instalados directamente. |

> Docker Desktop para Windows **no es necesario**: en un servidor Linux se usa Docker Engine, que se instala con un
> comando (sección 2). Si Docker Desktop falla en tu equipo de desarrollo, no afecta al servidor.

```
Internet ──► Nginx (web: 80/443) ──► /api ──► API (8080, interna) ──► MySQL (3306, interna)
                   └─ interfaz React                └─ volumen "archivos"    └─ volumen "mysql_datos"
```

## 1. Requisitos del servidor

| Recurso | Mínimo | Recomendado |
| --- | --- | --- |
| Sistema | Ubuntu Server 22.04/24.04 LTS (o Debian 12) de 64 bits | |
| CPU | 2 núcleos | 4 núcleos |
| RAM | 4 GB | 8 GB |
| Disco | 40 GB SSD | 80 GB SSD (comprobantes y respaldos crecen cada periodo) |
| Red | IP fija, puertos 80 y 443 abiertos | Dominio institucional (p. ej. `sigclem.chilpancingo.tecnm.mx`) |
| Salida a Internet | Hacia `consultaqr.facturaelectronica.sat.gob.mx:443` (consulta del CFDI) | |

Como referencia, la prueba de carga (1,560 alumnos, ~1,500 comprobantes y ~10,000 peticiones) se atendió sin
errores en un equipo de escritorio con 16 GB de RAM.

## 2. Preparar el servidor (una sola vez)

```bash
# Docker Engine y Compose
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER        # cierra y abre la sesión después

# Zona horaria y firewall
sudo timedatectl set-timezone America/Mexico_City
sudo ufw allow OpenSSH && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw enable
```

Copia el proyecto a `/opt/sigclem` (con `git clone`, `scp` o una memoria USB). Las carpetas `node_modules`,
`target` y `pruebas-carga` no son necesarias.

```bash
sudo mkdir -p /opt/sigclem && sudo chown $USER /opt/sigclem
cd /opt/sigclem
```

## 3. Configuración (`.env`)

```bash
cp .env.example .env
chmod 600 .env
nano .env
```

| Variable | Valor en producción |
| --- | --- |
| `DB_ROOT_PASS`, `DB_APP_PASS`, `DB_FLYWAY_PASS` | Tres contraseñas distintas: `openssl rand -base64 24` |
| `JWT_SECRET` | `openssl rand -base64 48` (64 caracteres) |
| `CORS_ORIGINS` | La dirección exacta del sistema, p. ej. `https://sigclem.chilpancingo.tecnm.mx` |
| `MODO_PRUEBAS` | `false` |
| `API_DOCS` | `false` |
| `COORDINADOR_CONTRASENA`, `JEFA_CONTRASENA` | Contraseñas iniciales (8+ caracteres, letras y números). Bórralas del `.env` después del primer arranque |
| `SAT_CONSULTA` | `true` (requiere salida a Internet hacia el SAT) |
| `RESPALDOS_DIAS` | Días que se conservan los respaldos (30) |

> **Importante:** `.env` contiene todas las contraseñas. No se comparte ni se sube a Git. Guarda una copia
> cifrada fuera del servidor: sin `DB_ROOT_PASS` no se pueden restaurar los respaldos.

## 4. HTTPS

Las contraseñas y los datos personales de los alumnos deben viajar cifrados. Coloca el certificado del dominio en
`certs/` con estos nombres:

```
certs/fullchain.pem   certificado + cadena intermedia
certs/privkey.pem     llave privada (chmod 600)
```

Opciones:

- **Certificado institucional:** lo entrega el área de TI del instituto.
- **Let's Encrypt (gratuito):** con el dominio apuntando al servidor y el puerto 80 libre:
  ```bash
  sudo apt install certbot
  sudo certbot certonly --standalone -d sigclem.chilpancingo.tecnm.mx
  sudo cp /etc/letsencrypt/live/sigclem.chilpancingo.tecnm.mx/fullchain.pem certs/
  sudo cp /etc/letsencrypt/live/sigclem.chilpancingo.tecnm.mx/privkey.pem certs/
  sudo chown $USER certs/*.pem && chmod 600 certs/privkey.pem
  ```
  El certificado dura 90 días. Para renovarlo: `docker compose stop web`, `sudo certbot renew`, copia los dos
  archivos otra vez y `sh deploy.sh`.

Cuando existen los dos archivos, `deploy.sh` agrega `docker-compose.https.yml`: Nginx escucha en 443 (TLS 1.2/1.3,
HSTS) y redirige el puerto 80 a HTTPS. Sin certificado el sistema funciona solo con HTTP (aceptable únicamente en
una red interna de pruebas).

## 5. Primer arranque

```bash
sh deploy.sh
```

El script revisa el `.env` (sin contraseñas de ejemplo, `JWT_SECRET` de 32+ caracteres), compila las imágenes,
levanta los contenedores, espera a que la API esté sana (Flyway crea las 30 tablas con las migraciones V1 a V21) y
ejecuta `scripts/verificar.sh`. La primera compilación tarda de 5 a 10 minutos.

Después:

1. Entra con el usuario del coordinador y la contraseña de `COORDINADOR_CONTRASENA`.
2. En **Cuentas y contraseñas** cambia tu contraseña por una personal.
3. Borra `COORDINADOR_CONTRASENA` y `JEFA_CONTRASENA` del `.env` (las cuentas ya existen y no se vuelven a crear).

## 6. Datos iniciales (coordinación)

Antes de abrir el sistema a los alumnos, la coordinación captura:

| Dónde | Qué |
| --- | --- |
| Grupos y horarios | El periodo vigente (fechas de inicio y fin) y las aulas con su capacidad |
| Facilitadores | Alta de cada facilitador con su contraseña (solo el coordinador la asigna o cambia) |
| Pagos y tarifas → Conceptos de pago | Montos vigentes de inscripción, examen, constancia, etc. |
| Firmas y membrete | Nombres y cargos de quienes firman; imágenes del membrete del año (encabezado, pie y logo) |
| Lenguas maternas | Catálogo de lenguas |
| Grupos y horarios | Apertura de grupos con horario, aula y facilitador |

Luego se anuncia a los alumnos la dirección del sistema para que se registren.

## 7. Verificación (salida de la fase de pruebas)

```bash
sh scripts/verificar.sh https://sigclem.chilpancingo.tecnm.mx
```

Revisa: contenedores sanos, API y web respondiendo, **modo pruebas desactivado**, **Swagger desactivado**, endpoints
protegidos con 401 sin sesión, cabeceras de seguridad y HSTS, `JWT_SECRET` válido, sin contraseñas de ejemplo,
último respaldo y espacio en disco. Debe terminar en `Verificación correcta.`

Lista de salida de la fase de pruebas:

- [ ] `scripts/verificar.sh` sin fallas.
- [ ] HTTPS con certificado válido (el navegador muestra el candado sin advertencias).
- [ ] Contraseña del coordinador cambiada y contraseñas iniciales borradas del `.env`.
- [ ] Datos iniciales capturados (sección 6) y membrete del año cargado.
- [ ] Recorrido completo con datos reales: registro de alumno → cargo → comprobante (QR + SAT + cotejo) →
      validación → inscripción → acta → boleta → constancia de avance.
- [ ] Respaldo automático programado (sección 8) y **una restauración de prueba** realizada.
- [ ] Copia del `.env` guardada fuera del servidor.
- [ ] Datos de prueba eliminados: si el servidor se usó para pruebas, empieza con una base limpia
      (`docker compose down -v` borra **todos** los datos; hazlo solo antes de capturar datos reales).

## 8. Respaldos

```bash
sh scripts/respaldo.sh
```

Crea `respaldos/AAAAMMDD-HHMMSS/` con `sigclem.sql.gz` (volcado completo de MySQL, comprobado) y
`archivos.tar.gz` (comprobantes, documentos de lengua materna, imágenes del membrete y constancias emitidas). Borra
los respaldos con más de `RESPALDOS_DIAS` días.

Programación diaria a las 02:00 con cron (`crontab -e`):

```
0 2 * * * cd /opt/sigclem && sh scripts/respaldo.sh >> respaldos/respaldo.log 2>&1
```

Copia la carpeta `respaldos/` a otro equipo o unidad de red (regla 3-2-1: tres copias, dos medios, una fuera del
servidor), por ejemplo con `rsync -a /opt/sigclem/respaldos/ usuario@otro-servidor:/respaldos/sigclem/`.

### Restaurar

```bash
sh scripts/restaurar.sh respaldos/20261018-020000
```

Pide escribir `RESTAURAR`, detiene la API, reemplaza la base de datos y los archivos, y vuelve a levantar el sistema.
En un servidor nuevo: instala Docker, copia el proyecto y el `.env` original, ejecuta `sh deploy.sh` y después la
restauración.

## 9. Operación diaria

| Tarea | Comando |
| --- | --- |
| Estado de los contenedores | `docker compose ps` |
| Registro de la API (últimas líneas) | `docker compose logs api --tail 100` |
| Seguir el registro en vivo | `docker compose logs -f api` |
| Reiniciar la API | `docker compose restart api` |
| Detener / iniciar todo | `docker compose stop` / `docker compose start` |
| Uso de disco de Docker | `docker system df` |

Con HTTPS, agrega `-f docker-compose.yml -f docker-compose.https.yml` después de `docker compose` (o usa
`sh deploy.sh`, que lo hace solo). Los registros rotan solos (5 archivos de 10 MB por contenedor). Docker reinicia
los contenedores tras una falla o un reinicio del servidor (`restart: unless-stopped`).

Tareas de la coordinación:

- **Contraseñas:** el coordinador asigna y cambia las contraseñas de facilitadores (Facilitadores), alumnos y
  jefatura del DGTV (Cuentas y contraseñas). Tras 5 intentos fallidos la cuenta se bloquea 15 minutos.
- **Cada año:** subir las imágenes del membrete nuevo en Firmas y membrete.
- **Cambio de personal:** actualizar nombres y cargos de quienes firman en Firmas y membrete.
- **Cada periodo:** crear el periodo, revisar tarifas y abrir grupos.

## 10. Actualizaciones

```bash
cd /opt/sigclem
# Copia los archivos nuevos (o git pull si es un repositorio; deploy.sh lo hace solo)
sh deploy.sh
```

`deploy.sh` hace un respaldo **antes** de actualizar, recompila, aplica las migraciones nuevas de Flyway al
arrancar la API y verifica. Reglas para los cambios de base de datos:

- Nunca se modifica una migración ya aplicada (`V1`…`V21`); los cambios van en una migración nueva (`V22__...sql`).
- Si una actualización falla, restaura el respaldo previo con `scripts/restaurar.sh` y vuelve a la versión anterior
  del código.

## 11. Solución de problemas

| Síntoma | Revisión |
| --- | --- |
| `api` en `unhealthy` | `docker compose logs api --tail 200`; casi siempre es una contraseña de base de datos distinta a la del volumen o una migración fallida |
| Cambié `DB_*_PASS` y la API no conecta | Las cuentas de MySQL se crean solo la primera vez; cambia la contraseña dentro de MySQL (`ALTER USER`) o restaura en un volumen nuevo |
| Comprobantes en "No disponible" | El servidor no llega al SAT: revisa salida a Internet / proxy institucional, o desactiva con `SAT_CONSULTA=false` |
| Error de CORS al iniciar sesión | `CORS_ORIGINS` debe coincidir exactamente con la dirección del navegador (protocolo, dominio y puerto) |
| "Archivo demasiado grande" | El límite es 5 MB por comprobante o documento |
| Disco lleno | `docker system df`, `docker image prune -a`, revisa `respaldos/` y `RESPALDOS_DIAS` |

## 12. Instalación sin Docker

Instala Java 21, MySQL 8, Nginx y Tesseract directamente en Ubuntu 22.04/24.04 (o Debian 12); la API corre como el
servicio `sigclem-api` de systemd. Los archivos están en la carpeta `nativo/`.

**1. En tu equipo de desarrollo** (Git Bash, con JDK 21 y Node 22), genera el paquete. Detén antes la API local si
la ejecutas desde `target/`:

```bash
sh nativo/empaquetar.sh
```

Se crea `sigclem-paquete.tar.gz` (API compilada, interfaz compilada y scripts de instalación).

**2. En el servidor**, cópialo (con `scp`, WinSCP o USB) e instala:

```bash
tar -xzf sigclem-paquete.tar.gz
cd sigclem-paquete
sudo sh instalar.sh
```

La primera vez el script instala los paquetes, crea la base `sigclem` con las cuentas de MySQL (`sigclem_flyway` para
las migraciones y `sigclem_app` solo con permisos de datos), crea `/etc/sigclem/sigclem.env` con contraseñas
aleatorias, instala el servicio y el sitio de Nginx, espera a la API y **muestra las contraseñas iniciales** del
coordinador y de la jefa del DGTV.

**3. Ajustes finales:**

```bash
sudo nano /etc/sigclem/sigclem.env      # CORS_ORIGINS=https://tu.dominio y borra las contraseñas iniciales
sudo systemctl restart sigclem-api
sudo apt install certbot python3-certbot-nginx
sudo certbot --nginx -d sigclem.chilpancingo.tecnm.mx    # HTTPS con renovación automática
```

| Tarea | Comando |
| --- | --- |
| Estado / reiniciar la API | `systemctl status sigclem-api` / `sudo systemctl restart sigclem-api` |
| Registro de la API | `journalctl -u sigclem-api -n 100` (en vivo: `-f`) |
| Respaldo (base y archivos) | `sudo sh /opt/sigclem/respaldo.sh` → `/var/backups/sigclem/AAAAMMDD-HHMMSS/` |
| Respaldo diario | `sudo crontab -e` → `0 2 * * * sh /opt/sigclem/respaldo.sh >> /var/log/sigclem-respaldo.log 2>&1` |
| Restaurar | `sudo sh /opt/sigclem/restaurar.sh /var/backups/sigclem/AAAAMMDD-HHMMSS` |
| Actualizar | Genera un paquete nuevo, cópialo y vuelve a ejecutar `sudo sh instalar.sh` (respalda antes, conserva la configuración y el sitio de Nginx, y Flyway aplica las migraciones nuevas) |

Ubicaciones: API en `/opt/sigclem/app.jar`, interfaz en `/var/www/sigclem`, archivos subidos en
`/srv/sigclem/archivos`, configuración en `/etc/sigclem/sigclem.env` y base de datos en MySQL local (puerto 3306,
solo `localhost`). La lista de salida de la fase de pruebas (sección 7) aplica igual; en lugar de
`scripts/verificar.sh`, revisa que `https://tu.dominio/api/v1/pruebas/estado` responda `"modoPruebas":false`.

### En un servidor Windows

También es posible, igual que en el equipo de desarrollo: MySQL 8 para Windows, `java -jar app.jar` registrado como
servicio (por ejemplo con NSSM) y Nginx o IIS sirviendo la carpeta `web/` y enviando `/api` al puerto 8080. Solo se
recomienda si el instituto no dispone de un servidor Linux, porque los scripts de instalación y respaldo de este
proyecto son para Linux.
