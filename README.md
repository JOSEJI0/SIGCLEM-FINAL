# SIGCLEM

Sistema web para la gestión de la información académica y administrativa de la Coordinación de Lenguas
Extranjeras y Maternas del Instituto Tecnológico de Chilpancingo.

Proyecto de residencia profesional. El diseño completo está en el documento de desarrollo (Fase 4).

## Estructura

```
sigclem/
├─ sigclem-api/   API REST · Java 21 · Spring Boot 4.1 · Spring Security + JWT · JPA · Flyway · MySQL 8
├─ sigclem-web/   SPA · React 19 · Vite · Tailwind CSS 4 · React Router · Axios · lucide-react
├─ db-init/       Script que crea las cuentas de MySQL (flyway con DDL, app solo con DML)
├─ scripts/       Respaldo, restauración y verificación del servidor (Docker)
├─ nativo/        Instalación sin Docker: paquete, instalador, servicio systemd y respaldos
├─ docker-compose.yml, docker-compose.https.yml, .env.example, deploy.sh, DESPLIEGUE.md
```

### Backend (paquetes por módulo, Tabla 57)

```
mx.edu.itch.sigclem
├─ config/          OpenAPI y endpoint de salud
├─ seguridad/       JWT, 4 roles, login con bloqueo tras 5 intentos, cuentas iniciales
├─ comun/excepcion  Excepciones del sistema y manejador global RFC 7807 (Tabla 61)
├─ catalogos/       Carreras, niveles, periodos, modalidades y aulas
├─ alumnos/         Registro de alumno (CU-02) y expediente con avance reticular (CU-03)
├─ examenes/        Solicitud, programación y evaluación de exámenes (CU-04 a CU-06)
├─ facilitadores/    Alta, consulta, edición y desactivación de facilitadores (CU-07)
├─ grupos/           Apertura, horarios, consulta y actas de calificaciones (CU-08 a CU-10)
├─ reportes/         Resumen académico por periodo y nivel (CU-17)
├─ diagnosticos/     Solicitud, programación y resultado de evaluación diagnóstica (CU-22)
├─ constancias/      Solicitud, autorización, folio único y emisión PDF (CU-14, CU-15)
├─ pagos/            Tarifas, autorizaciones, comprobantes e inscripción con pago validado (CU-11 a CU-13)
├─ boletas/          Consulta del historial de calificaciones por periodo (CU-20)
├─ lenguasmaternas/  Catálogo, registro con documento y revisión de lenguas maternas (CU-18, CU-19)
└─ notificaciones/   Mensajería entre coordinación y jefatura del DGTV (CU-25)
```

Cada módulo sigue las capas `controller → service → repository → domain`, con `dto` para entrada y salida.

## Qué está listo (incrementos 0 y 1)

| Área | Estado |
| --- | --- |
| Esquema de base de datos | Migraciones `V1` a `V9`: esquema inicial, catálogos, tarifas, notificaciones, constancias, cargos con saldo y conceptos de pago base |
| Seguridad | Login JWT (8 h), BCrypt, 4 roles, jefa del DGTV en solo lectura, bloqueo tras 5 intentos |
| Errores | Formato `application/problem+json` con campo `codigo` |
| Alumnos | Registro público (siempre rol Alumno) y consulta del expediente con validación de propiedad |
| Exámenes | El alumno solicita y consulta sus propios exámenes; coordinación programa y captura resultados por rúbrica (CU-04 a CU-06) |
| Lenguas maternas | Catálogo administrable por coordinación, registro y consulta del documento propio del alumno, y revisión/validación o rechazo (CU-18, CU-19) |
| Boletas | Historial de calificaciones por periodo y grupo; las calificaciones se muestran únicamente después de cerrar el acta (CU-20) |
| Constancias | Solicitud al acreditar los diez niveles de inglés, folio único, emisión PDF y notificaciones a coordinación, alumno y jefatura (CU-14, CU-15) |
| Reportes académicos | Resumen por periodo y nivel de grupos, inscripciones, actas cerradas, pendientes, promedios y resultados publicados, más la tabla de aprobación/reprobación por carrera y nivel (CU-17, RF-18) |
| Evaluaciones diagnósticas | Solicitud, programación con avisos a ambas partes, consulta del puntaje, observaciones y recomendación (CU-22 a CU-24); el diagnóstico no modifica el avance de nivel |
| Búsqueda de alumnos y supervisión | Consulta paginada por nombre, número de control, carrera o correo; consulta de expedientes y vistas de solo lectura de alumnos, facilitadores y grupos (CU-16, CU-21) |
| Mensajería | Coordinación envía mensajes a jefatura; jefatura consulta su bandeja y marca mensajes como leídos (CU-25) |
| Facilitadores | Alta con contraseña temporal, consulta, edición y desactivación lógica por el coordinador (CU-07) |
| Grupos y actas | Apertura con validación de traslapes, vista del facilitador, captura de calificaciones y cierre inmutable (CU-08 a CU-10) |
| Pagos e inscripciones | Conceptos y tarifas administrables, cargos emitidos por coordinación, abonos con saldo, evidencia JPG, captura manual de CFDI, validación/rechazo e inscripción al liquidar el curso (CU-11 a CU-13) |
| Frontend | Login, registro, rutas protegidas por rol, expediente, exámenes, pagos e inscripción, boletas, constancias con descarga de PDF, reportes, facilitadores, grupos, actas y gestión de lenguas maternas |
| Despliegue | Docker Compose con MySQL, API y Nginx; HTTPS, respaldos, restauración y verificación (DESPLIEGUE.md) |

**Verificación automática de comprobantes (CFDI).** El alumno sube el recibo oficial de cobro en PDF, JPG o PNG y el
servidor: (1) lee el QR con ZXing (con varias escalas y suavizado para escaneos con grano), (2) consulta el estado del
CFDI en el servicio público del SAT (`ConsultaCFDIService`, el mismo del verificador del QR; *Vigente*, *Cancelado*,
*No encontrado* o *No disponible*) y (3) coteja la descripción del recibo (número de control, nombre y concepto) con el
cargo, porque el CFDI se emite a público en general. El texto sale de la capa de texto del PDF o, en escaneos y fotos,
del OCR con Tesseract si `OCR_COMANDO` está configurado. Un CFDI cancelado se rechaza al subirlo y no puede validarse;
el resto queda como apoyo y coordinación confirma la validación (con aviso si hay diferencias). Si el QR no se lee,
coordinación captura UUID, RFC, total y, opcionalmente, los 8 caracteres finales del sello para consultar al SAT; el
botón *Reverificar* repite las tres comprobaciones.

**Constancia de avance por nivel.** Por cada nivel acreditado (acta cerrada, calificación ≥ 70) se genera el formato
del Foreign Languages Department con nivel, periodo, facilitador, calificación con letra y las tres firmas. El
alumno descarga las suyas; coordinación y jefatura buscan por número de control en *Constancias*.

**Gestión de alumnos por el coordinador.** Desde el cambio 15 ya no hay registro público: el coordinador crea las
cuentas de los alumnos, una por una o por **carga masiva de un archivo Excel** (.xlsx con un formato fijo; la
contraseña inicial de cada cuenta es su número de control, y al final se informa qué filas fallaron). Se agregó el
dato de **sexo** (Hombre/Mujer) y la **baja reversible** de alumnos (con motivo y fecha; desactiva la cuenta y los
saca de las listas y reportes activos, pero conserva el historial). Además de completar los diez niveles, el idioma
se puede **liberar** por **examen global** (lo paga y aprueba el alumno) o por **lengua materna** (documento oficial
validado); el expediente muestra con qué forma se liberó. Estas acciones quedan en la bitácora de auditoría.

**Rol de administrador y auditoría.** Opcionalmente se crea una cuenta de administrador (variables `ADMIN_USUARIO`
y `ADMIN_CONTRASENA`): es el usuario de mayor privilegio —por ejemplo, quien desarrolla o mantiene el sistema—, hace
todo lo del coordinador (mediante una jerarquía de roles de Spring Security, `ROLE_ADMIN > ROLE_COORDINADOR`) y además
ve la **bitácora de auditoría** en *Administración → Auditoría*. No hay accesos ocultos: el administrador entra por el
login normal (contraseña con BCrypt) y cada acción sensible —inicios de sesión y sus fallos, bloqueos, cambios de
contraseña, cierres de acta por coordinación y asignaciones de nivel— queda registrada en la tabla `registro_auditoria`
(migración V18) con usuario, fecha, resultado e IP. La bitácora es solo de lectura; nadie la modifica o borra desde el
sistema, para que sirva como evidencia.

**Cierre del acta por coordinación y nivel pendiente.** El facilitador solo cierra el acta cuando todos liquidaron su
curso. Si alguien no ha pagado, coordinación la cierra desde *Grupos y horarios → Alumnos* con una observación
obligatoria: a quienes pagaron se les aplica el nivel y los demás conservan su calificación con el **nivel pendiente**
(su boleta dice «Nivel pendiente de pago» y no obtienen constancia de avance). Cuando presentan el pago, coordinación
pulsa *Asignar nivel* en ese alumno, con otra observación. Todo queda en la bitácora del grupo (tabla
`ajuste_avance`, migración V17) y la observación del cierre se imprime en el acta en PDF.

**Semestre del alumno.** Como el sistema no está conectado con control escolar, cada alumno actualiza su semestre en
*Mi expediente*; si no lo ha confirmado en el periodo vigente, ve un aviso para hacerlo. Coordinación filtra la lista de
alumnos por semestre y ve la fecha de la última actualización.

**Firmas y membrete.** En *Coordinación → Firmas y membrete* se editan las personas que firman (con cargo en
femenino o masculino: Subdirectora/Subdirector, Jefa/Jefe, Coordinadora/Coordinador), el lema, el registro de la
coordinación, el prefijo del número de oficio (`PREFIJO/FOLIO/AÑO`) y el bloque c.c.p., y se suben las imágenes del
membrete del año (encabezado, pie de página y logo de la coordinación). La constancia de acreditación usa el formato
de oficio del DGTV. Los cambios aplican a las constancias que se emitan después; las emitidas conservan su PDF.
Las constancias solo se solicitan cuando el alumno acredita los diez niveles de inglés. Los flujos CU-11 a CU-13 incorporan cargos emitidos por coordinación y abonos parciales; antes de producción, aplica y verifica las migraciones pendientes en una base de datos MySQL respaldada.

## Modo pruebas (sin login) y ventana de endpoints

**El sistema exige iniciar sesión** (`MODO_PRUEBAS=false` por omisión). Solo en un equipo de pruebas se puede activar
`MODO_PRUEBAS=true` en `.env` para usar la API **sin iniciar sesión** (la prueba de carga lo activa en su instancia
aislada del puerto 8090):

- **Ventana con todos los endpoints:** con `API_DOCS=true`, abre `http://localhost:8080/` (redirige a Swagger UI,
  `/api/v1/docs/ui`).
  Con Docker Compose también está en `http://localhost/api/v1/docs/ui`. Los endpoints están agrupados por módulo,
  indican el rol requerido y se pueden ejecutar con **Try it out**.
- **Identidad:** cada petición sin token se atiende como el **coordinador**. Para actuar con otro rol pulsa
  **Authorize** y escribe en `usuarioPrueba` la cuenta (número de control del alumno, correo del facilitador o
  `jefa.dgtv`), o envía la cabecera `X-Usuario-Prueba` desde Postman/curl:
  ```bash
  curl http://localhost:8080/api/v1/boletas/mias -H "X-Usuario-Prueba: 22520001"
  ```
- **Datos de ejemplo:** `POST /api/v1/pruebas/datos-demo` crea (sin duplicar) un periodo vigente, un aula, el
  facilitador `facilitador.demo@itch.edu.mx`, los alumnos `22520001` y `22520002`, tarifas y un grupo de Básico 1.
  `GET /api/v1/pruebas/usuarios` lista las cuentas disponibles.
- **Frontend:** la pantalla de inicio muestra el panel *Modo pruebas* para entrar con cualquier cuenta sin
  contraseña, cargar los datos de ejemplo y abrir la ventana de endpoints.
- Los roles y las reglas de negocio se validan igual que con login. El login JWT sigue funcionando.

> Antes de producción: `MODO_PRUEBAS=false` y `API_DOCS=false` en `.env`.

## Desarrollo local

Requisitos: JDK 21, Node 22 y MySQL 8.0. En la fase de pruebas se usa el **MySQL 8.0 instalado en Windows (puerto 3306), sin Docker**.

**Arranque rápido (fase de pruebas):**

1. En el archivo `.env` de la raíz escribe la contraseña de `root` de tu MySQL en `DB_PASS` y `FLYWAY_PASS`.
   `DB_URL` ya incluye `createDatabaseIfNotExist=true`: la base `sigclem` se crea sola y Flyway crea las tablas.
2. API: `cd sigclem-api` y `mvnw.cmd spring-boot:run` (o F5 en VS Code). La API lee `../.env` automáticamente.
3. Abre `http://localhost:8080/`: es la ventana con todos los endpoints (Swagger UI), sin login.
4. (Opcional) Frontend: `cd sigclem-web`, `npm install` y `npm run dev` → `http://localhost:5173`.

Instalación manual, si prefieres otras credenciales:

1. Crea la base de datos:
   ```sql
   CREATE DATABASE sigclem CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   ```
2. Backend (Flyway crea las tablas al arrancar):
   ```bash
   cd sigclem-api
   export DB_USER=root DB_PASS=tu_contrasena
   export JWT_SECRET="una-cadena-aleatoria-de-al-menos-32-caracteres"
   export COORDINADOR_USUARIO=coordinador COORDINADOR_CONTRASENA=Coordinador2026
   ./mvnw spring-boot:run          # En Windows: mvnw.cmd spring-boot:run
   ```
   Swagger UI: http://localhost:8080/api/v1/docs/ui
3. Frontend:
   ```bash
   cd sigclem-web
   npm install
   npm run dev                      # http://localhost:5173 (redirige /api al puerto 8080)
   ```
4. Pruebas: `./mvnw test` (backend) y `npm test` (frontend).

## Despliegue en servidor

La guía completa está en [DESPLIEGUE.md](DESPLIEGUE.md): requisitos, `.env` de producción, HTTPS, primer arranque,
datos iniciales, respaldos, actualizaciones y la lista para salir de la fase de pruebas. En resumen:

```bash
cp .env.example .env     # contraseñas, JWT_SECRET y CORS_ORIGINS; MODO_PRUEBAS=false y API_DOCS=false
# (opcional) certs/fullchain.pem y certs/privkey.pem para HTTPS
sh deploy.sh             # respaldo previo, compilación, migraciones y verificación
sh scripts/respaldo.sh   # respaldo de base de datos y archivos (programarlo con cron)
```

Nginx sirve la SPA y redirige `/api` al contenedor de la API; MySQL solo escucha en `127.0.0.1`. Con certificado,
`docker-compose.https.yml` publica el puerto 443 y redirige HTTP a HTTPS.

**Sin Docker:** `sh nativo/empaquetar.sh` genera un paquete que se instala en Ubuntu con `sudo sh instalar.sh`
(Java 21, MySQL, Nginx y un servicio systemd); ver la sección 12 de DESPLIEGUE.md.

**Cuentas y contraseñas.** El coordinador cambia su contraseña y asigna las de alumnos y de la jefatura del DGTV en
*Coordinación → Cuentas y contraseñas*; las de facilitadores, en *Facilitadores*. Los facilitadores no cambian su
propia contraseña.

## Trabajo pendiente

- Ampliar las pruebas de integración con MySQL y verificar el flujo completo de solicitudes, emisión y descarga de constancias.
