# Prueba general y de carga

Recorre todo el sistema con volumen: registro de alumnos, facilitadores, grupos con validación de traslapes,
asignación (con carrera por cupo), recibos PDF con QR, verificación y validación de comprobantes, actas,
boletas, constancias de avance, progresión de 10 niveles hasta la constancia de acreditación, exámenes,
diagnósticos, lenguas maternas, mensajería, permisos entre roles, login con bloqueo y concurrencia
(UUID repetido, doble validación). Al final imprime tiempos por endpoint (p50/p95) y los hallazgos.

Se ejecuta contra una **instancia aparte** de la API con su propia base (`sigclem_carga`), para no tocar la
base de desarrollo. Las configuraciones `sigclem-api-carga` (puerto 8090, consulta al SAT desactivada) y
`sigclem-web-carga` (puerto 5175) están en `.claude/launch.json`.

```bash
cd sigclem-api && ./mvnw -q package -DskipTests && ./mvnw -q dependency:build-classpath -Dmdep.outputFile=../pruebas-carga/cp.txt
cd ../pruebas-carga && javac -proc:none -cp "$(cat cp.txt)" -d . Recibos.java
# con la API de carga corriendo en 8090:
python -I prueba_carga.py http://localhost:8090 . ".;$(cat cp.txt)" 1500
```

El último argumento es el número de alumnos (500 por omisión). Para repetir desde cero, borra la base
`sigclem_carga` antes de arrancar la API de carga. Los recibos generados usan UUID inventados.
