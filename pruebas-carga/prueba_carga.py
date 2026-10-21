"""Prueba general y de carga de SIGCLEM contra una instancia aislada (base sigclem_carga).

Uso: python -I prueba_carga.py <url_api> <dir_trabajo> <classpath_java>
Usa el modo pruebas (cabecera X-Usuario-Prueba) para actuar como cada rol, y además prueba el login JWT.
"""
import concurrent.futures as cf
import json
import random
import statistics
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid
from collections import defaultdict
from pathlib import Path

API = sys.argv[1].rstrip('/') + '/api/v1'
TRABAJO = Path(sys.argv[2])
CLASSPATH = sys.argv[3]
random.seed(20261017)

N_ALUMNOS = int(sys.argv[4]) if len(sys.argv) > 4 else 500          # alumnos que cursan Básico 1
N_EXTRA = 60             # sin grupo: 20 para la carrera por cupo y 40 para exámenes
N_PROGRESION = 25        # alumnos que recorren los 10 niveles hasta la constancia
N_FACILITADORES = 15
N_AULAS = 12
CUPO = 30
MONTO = '650.00'
HILOS = 16
CONTRASENA = 'Alumno2026!'

metricas = defaultdict(list)       # etiqueta -> [ms]
estados = defaultdict(lambda: defaultdict(int))
hallazgos = []                     # (severidad, área, descripción)
bloqueo = threading.Lock()


def hallazgo(severidad, area, texto):
    with bloqueo:
        hallazgos.append((severidad, area, texto))
    print(f'  [{severidad}] {area}: {texto}')


def api(metodo, ruta, usuario=None, cuerpo=None, archivos=None, campos=None, etiqueta=None, token=None, timeout=120):
    url = API + ruta
    cabeceras = {}
    datos = None
    if usuario:
        cabeceras['X-Usuario-Prueba'] = usuario
    if token:
        cabeceras['Authorization'] = 'Bearer ' + token
    if archivos is not None or campos is not None:
        limite = '----sigclem' + uuid.uuid4().hex
        partes = []
        for nombre, valor in (campos or {}).items():
            partes.append(f'--{limite}\r\nContent-Disposition: form-data; name="{nombre}"\r\n\r\n{valor}\r\n'.encode())
        for nombre, (archivo, contenido, tipo) in (archivos or {}).items():
            partes.append(f'--{limite}\r\nContent-Disposition: form-data; name="{nombre}"; filename="{archivo}"\r\n'
                          f'Content-Type: {tipo}\r\n\r\n'.encode() + contenido + b'\r\n')
        partes.append(f'--{limite}--\r\n'.encode())
        datos = b''.join(partes)
        cabeceras['Content-Type'] = 'multipart/form-data; boundary=' + limite
    elif cuerpo is not None:
        datos = json.dumps(cuerpo).encode('utf-8')
        cabeceras['Content-Type'] = 'application/json; charset=utf-8'
    peticion = urllib.request.Request(url, data=datos, method=metodo, headers=cabeceras)
    inicio = time.perf_counter()
    try:
        with urllib.request.urlopen(peticion, timeout=timeout) as r:
            estado, contenido, tipo = r.status, r.read(), r.headers.get('Content-Type', '')
    except urllib.error.HTTPError as e:
        estado, contenido, tipo = e.code, e.read(), e.headers.get('Content-Type', '')
    except Exception as e:  # conexión caída, tiempo agotado
        estado, contenido, tipo = 0, str(e).encode(), ''
    ms = (time.perf_counter() - inicio) * 1000
    clave = etiqueta or f'{metodo} {ruta}'
    with bloqueo:
        metricas[clave].append(ms)
        estados[clave][estado] += 1
    if estado >= 500 or estado == 0:
        hallazgo('ALTA', clave, f'HTTP {estado}: {contenido[:300].decode("utf-8", "replace")}')
    if 'json' in tipo and contenido:
        try:
            return estado, json.loads(contenido.decode('utf-8'))
        except ValueError:
            pass
    return estado, contenido


def en_paralelo(funcion, elementos, hilos=HILOS):
    with cf.ThreadPoolExecutor(hilos) as ex:
        return list(ex.map(funcion, elementos))


def esperar(estado, esperado, area, cuerpo):
    if estado not in (esperado if isinstance(esperado, tuple) else (esperado,)):
        detalle = cuerpo.get('detail') if isinstance(cuerpo, dict) else str(cuerpo)[:200]
        hallazgo('MEDIA', area, f'se esperaba {esperado} y llegó {estado}: {detalle}')
        return False
    return True


def fase(nombre):
    print(f'\n=== {nombre} ===', flush=True)
    return time.perf_counter()


NOMBRES = ['José', 'María', 'Juan', 'Guadalupe', 'Luis', 'Ana', 'Carlos', 'Rosa', 'Miguel', 'Fernanda', 'Jesús',
           'Daniela', 'Alejandro', 'Itzel', 'Ricardo', 'Ximena', 'Eduardo', 'Citlali', 'Iván', 'Yaretzi', 'Óscar',
           'Abril', 'Héctor', 'Ángeles', 'Raúl', 'Noemí', 'Saúl', 'Belén']
APELLIDOS = ['Hernández', 'García', 'Martínez', 'López', 'González', 'Pérez', 'Rodríguez', 'Sánchez', 'Ramírez',
             'Cruz', 'Flores', 'Gómez', 'Morales', 'Vázquez', 'Reyes', 'Jiménez', 'Torres', 'Díaz', 'Gutiérrez',
             'Ruiz', 'Mendoza', 'Aguilar', 'Ortiz', 'Moreno', 'Castillo', 'Romero', 'Álvarez', 'Chávez', 'Ramos',
             'Tepetitlán', 'Nava', 'Salgado', 'Bello', 'Astudillo', 'Abarca', 'Catalán']


def main():
    t_total = time.perf_counter()
    coord = None  # sin cabecera: modo pruebas atiende como coordinador

    # ---------------------------------------------------------------- catálogos y personal
    t = fase('1. Catálogos, tarifa, periodo, aulas y facilitadores')
    s, conceptos = api('GET', '/pagos/conceptos?incluirInactivos=true')
    curso = next(c for c in conceptos if c['nombre'] == 'Curso de nivel')
    s, b = api('PUT', f'/pagos/conceptos/{curso["id"]}', cuerpo={'nombre': 'Curso de nivel', 'monto': 650, 'requiereNivel': True})
    esperar(s, 200, 'Monto del curso', b)
    s, carreras = api('GET', '/catalogos/carreras')
    s, niveles = api('GET', '/catalogos/niveles')
    niveles = sorted(niveles, key=lambda n: n['numero'])
    s, modalidades = api('GET', '/catalogos/modalidades')
    entre_semana = next(m['id'] for m in modalidades if m['nombre'] == 'Entre semana')
    s, periodo = api('POST', '/catalogos/periodos', cuerpo={'nombre': 'AGO-DIC 2026 CARGA', 'fechaInicio': '2026-08-10', 'fechaFin': '2026-12-18'})
    if s not in (200, 201):
        s, periodos = api('GET', '/catalogos/periodos')
        periodo = next(p for p in periodos if p['nombre'] == 'AGO-DIC 2026 CARGA')
    id_periodo = periodo['id']
    aulas = []
    for i in range(N_AULAS):
        s, a = api('POST', '/catalogos/aulas', cuerpo={'nombre': f'Aula C-{i + 1:02d}'})
        if s in (200, 201):
            aulas.append(a['id'])
    if len(aulas) < N_AULAS:
        s, todas = api('GET', '/catalogos/aulas')
        aulas = [a['id'] for a in todas if a['nombre'].startswith('Aula C-')]

    def crear_facilitador(i):
        nombre = NOMBRES[i % len(NOMBRES)]
        return api('POST', '/facilitadores', cuerpo={'nombre': nombre, 'apellidoPaterno': APELLIDOS[i % len(APELLIDOS)],
                                                     'apellidoMaterno': APELLIDOS[(i * 7) % len(APELLIDOS)],
                                                     'correo': f'facilitador{i + 1:02d}@carga.itch.edu.mx',
                                                     'telefono': '7471234567', 'contrasena': 'Facilitador2026!'})
    en_paralelo(crear_facilitador, range(N_FACILITADORES), hilos=5)
    s, facs = api('GET', '/facilitadores')
    facilitadores = [f for f in facs if str(f.get('correo', '')).endswith('@carga.itch.edu.mx')]
    print(f'  periodo {id_periodo}, {len(aulas)} aulas, {len(facilitadores)} facilitadores ({time.perf_counter() - t:.1f} s)')

    # ---------------------------------------------------------------- registro de alumnos
    t = fase(f'2. Registro público de {N_ALUMNOS + N_EXTRA} alumnos (concurrente)')
    alumnos = []
    for i in range(N_ALUMNOS + N_EXTRA):
        alumnos.append({'numeroControl': f'2552{i:04d}', 'nombre': random.choice(NOMBRES) + (' ' + random.choice(NOMBRES) if i % 4 == 0 else ''),
                        'apellidoPaterno': random.choice(APELLIDOS), 'apellidoMaterno': random.choice(APELLIDOS),
                        'idCarrera': carreras[i % len(carreras)]['id'], 'semestre': 1 + i % 9,
                        'correo': f'al{i:04d}@carga.itch.edu.mx', 'contrasena': CONTRASENA,
                        'hablaLenguaMaterna': i % 10 == 0})

    def registrar(a):
        s, b = api('POST', '/alumnos/registro', cuerpo=a, etiqueta='POST /alumnos/registro')
        return s
    resultados = en_paralelo(registrar, alumnos)
    ok = sum(1 for r in resultados if r in (200, 201))
    print(f'  registrados {ok}/{len(alumnos)} ({time.perf_counter() - t:.1f} s)')
    if ok < len(alumnos):
        hallazgo('ALTA', 'Registro', f'{len(alumnos) - ok} registros fallaron: {sorted(set(resultados))}')
    # Casos inválidos: duplicado, correo mal formado, número de control con minúsculas
    s, b = api('POST', '/alumnos/registro', cuerpo=alumnos[0], etiqueta='registro duplicado')
    esperar(s, 409, 'Registro duplicado', b)
    s, b = api('POST', '/alumnos/registro', cuerpo={**alumnos[1], 'numeroControl': '99990001', 'correo': 'no-es-correo'}, etiqueta='registro inválido')
    esperar(s, 400, 'Registro con correo inválido', b)
    s, b = api('POST', '/alumnos/registro', cuerpo={**alumnos[1], 'numeroControl': '9999000a'}, etiqueta='registro inválido')
    esperar(s, 400, 'Registro con número de control en minúsculas', b)

    # ---------------------------------------------------------------- grupos
    t = fase('3. Apertura de grupos de Básico 1 y validación de traslapes')
    n_grupos = -(-N_ALUMNOS // CUPO)
    claves = [chr(65 + i) if i < 26 else chr(65 + i // 26 - 1) + chr(65 + i % 26) for i in range(60)]
    grupos = []
    for i in range(n_grupos):
        bloque = i // N_AULAS
        horarios = [{'diaSemana': d, 'horaInicio': f'{7 + 2 * bloque:02d}:00', 'horaFin': f'{9 + 2 * bloque:02d}:00'} for d in (1, 3)]
        s, g = api('POST', '/grupos', cuerpo={'clave': claves[i], 'idNivel': niveles[0]['id'], 'idPeriodo': id_periodo,
                                              'idModalidad': entre_semana, 'idFacilitador': facilitadores[i % len(facilitadores)]['id'],
                                              'idAula': aulas[i % len(aulas)], 'cupo': CUPO, 'horarios': horarios}, etiqueta='POST /grupos')
        if esperar(s, (200, 201), f'Apertura del grupo {claves[i]}', g):
            grupos.append({**g, 'facilitador': facilitadores[i % len(facilitadores)]})
    # Traslape: mismo facilitador y horario del grupo A
    s, b = api('POST', '/grupos', cuerpo={'clave': 'ZZ', 'idNivel': niveles[1]['id'], 'idPeriodo': id_periodo, 'idModalidad': entre_semana,
                                          'idFacilitador': facilitadores[0]['id'], 'idAula': aulas[-1], 'cupo': 10,
                                          'horarios': [{'diaSemana': 1, 'horaInicio': '07:30', 'horaFin': '08:30'}]}, etiqueta='grupo con traslape')
    esperar(s, 422, 'Traslape de horario', b)
    s, b = api('POST', '/grupos', cuerpo={'clave': 'A', 'idNivel': niveles[0]['id'], 'idPeriodo': id_periodo, 'idModalidad': entre_semana,
                                          'idFacilitador': facilitadores[1]['id'], 'idAula': aulas[1], 'cupo': 10,
                                          'horarios': [{'diaSemana': 6, 'horaInicio': '08:00', 'horaFin': '10:00'}]}, etiqueta='grupo clave repetida')
    esperar(s, 409, 'Clave de grupo repetida', b)
    print(f'  {len(grupos)} grupos abiertos ({time.perf_counter() - t:.1f} s)')

    # ---------------------------------------------------------------- asignación
    t = fase(f'4. Asignación de {N_ALUMNOS} alumnos a grupos (genera la papeleta de pago)')
    asignaciones = []
    for i, a in enumerate(alumnos[:N_ALUMNOS]):
        asignaciones.append((grupos[i // CUPO]['id'], a['numeroControl']))

    def asignar(par):
        s, b = api('POST', f'/grupos/{par[0]}/alumnos', cuerpo={'numeroControl': par[1]}, etiqueta='POST /grupos/{id}/alumnos')
        return s
    res = en_paralelo(asignar, asignaciones)
    print(f'  asignados {sum(1 for r in res if r in (200, 201))}/{len(res)} ({time.perf_counter() - t:.1f} s)')
    s, b = api('POST', f'/grupos/{grupos[0]["id"]}/alumnos', cuerpo={'numeroControl': alumnos[0]['numeroControl']}, etiqueta='asignación duplicada')
    esperar(s, 409, 'Asignar dos veces al mismo grupo', b)
    s, b = api('POST', f'/grupos/{grupos[1]["id"]}/alumnos', cuerpo={'numeroControl': alumnos[0]['numeroControl']}, etiqueta='asignación duplicada')
    esperar(s, 409, 'Asignar a dos grupos del mismo periodo', b)

    # Carrera por cupo: 20 asignaciones simultáneas a un grupo con cupo 5
    s, g_cupo = api('POST', '/grupos', cuerpo={'clave': 'CU', 'idNivel': niveles[0]['id'], 'idPeriodo': id_periodo, 'idModalidad': entre_semana,
                                               'idFacilitador': facilitadores[-1]['id'], 'idAula': aulas[-1], 'cupo': 5,
                                               'horarios': [{'diaSemana': 6, 'horaInicio': '09:00', 'horaFin': '13:00'}]})
    if s in (200, 201):
        extra_cupo = alumnos[N_ALUMNOS:N_ALUMNOS + 20]
        res = en_paralelo(lambda a: asignar((g_cupo['id'], a['numeroControl'])), extra_cupo, hilos=20)
        s, det = api('GET', f'/grupos/{g_cupo["id"]}')
        inscritos = det.get('alumnosInscritos', det.get('inscritos', len(det.get('alumnos', [])))) if isinstance(det, dict) else '?'
        exitos = sum(1 for r in res if r in (200, 201))
        print(f'  carrera por cupo: {exitos} asignaciones exitosas, inscritos según el grupo: {inscritos}')
        if exitos != 5:
            hallazgo('ALTA', 'Cupo', f'con cupo 5 se aceptaron {exitos} asignaciones simultáneas')

    # ---------------------------------------------------------------- recibos y pagos
    t = fase('5. Generación de recibos PDF con QR y carga de comprobantes (concurrente)')
    # Alumnos sin recibos sembrados con diferencias ni falta de pago (esos no acreditan Básico 1)
    progresion = [a for i, a in enumerate(alumnos[:N_ALUMNOS]) if i % 50 not in (7, 13) and i % 40 != 39][:N_PROGRESION]
    filas, recibos = [], {}
    for i, a in enumerate(alumnos[:N_ALUMNOS]):
        u = str(uuid.uuid4()).upper()
        nombre = f'{a["apellidoPaterno"]} {a["apellidoMaterno"]} {a["nombre"]}'.upper()
        if i % 50 == 7:      # recibo de otro concepto (reinscripción)
            l1 = 'REINSCRIPCION LICENCIATURA'
        else:
            l1 = 'CURSO DE LENGUA EXTRANJERA MODALIDAD PRESENCIAL'
        otro = alumnos[(i + 1) % N_ALUMNOS] if i % 50 == 13 else a   # recibo de otro alumno
        l2 = f'ISIC {otro["numeroControl"]} {otro["apellidoPaterno"].upper()} {otro["nombre"].upper()}'
        filas.append(f'r-{a["numeroControl"]}-1.pdf|{u}|{MONTO}|{l1}|{l2}')
        recibos[(a['numeroControl'], 1)] = (f'r-{a["numeroControl"]}-1.pdf', u)
    for a in progresion:
        for nivel in range(2, 11):
            u = str(uuid.uuid4()).upper()
            filas.append(f'r-{a["numeroControl"]}-{nivel}.pdf|{u}|{MONTO}|CURSO DE LENGUA EXTRANJERA|ISIC {a["numeroControl"]} {a["apellidoPaterno"].upper()} {a["nombre"].upper()}')
            recibos[(a['numeroControl'], nivel)] = (f'r-{a["numeroControl"]}-{nivel}.pdf', u)
    csv = TRABAJO / 'recibos.csv'
    csv.write_text('\n'.join(filas), encoding='utf-8')
    tg = time.perf_counter()
    subprocess.run(['java', '-cp', CLASSPATH, 'Recibos', str(TRABAJO / 'recibos'), str(csv)], check=True)
    print(f'  {len(filas)} recibos generados en {time.perf_counter() - tg:.1f} s')

    no_pagan = {a['numeroControl'] for i, a in enumerate(alumnos[:N_ALUMNOS]) if i % 40 == 39}   # 2.5 % no paga

    def cargo_abierto(nc, nivel_nombre=None):
        s, cargos = api('GET', '/pagos/autorizaciones', usuario=nc, etiqueta='GET /pagos/autorizaciones (alumno)')
        for c in cargos if isinstance(cargos, list) else []:
            if c['concepto'] == 'Curso de nivel' and c['estatusCargo'] != 'Liquidada' and (nivel_nombre is None or c.get('nivel') == nivel_nombre):
                return c
        return None

    def subir(nc, nivel=1):
        cargo = cargo_abierto(nc, niveles[nivel - 1]['nombre'])
        if not cargo:
            hallazgo('MEDIA', 'Pagos', f'{nc} no tiene cargo abierto de nivel {nivel}')
            return None
        archivo, u = recibos[(nc, nivel)]
        contenido = (TRABAJO / 'recibos' / archivo).read_bytes()
        s, b = api('POST', f'/pagos/autorizaciones/{cargo["id"]}/comprobante', usuario=nc,
                   archivos={'archivo': (archivo, contenido, 'application/pdf')}, etiqueta='POST comprobante (lectura QR)')
        if s != 201:
            hallazgo('MEDIA', 'Carga de comprobante', f'{nc}: HTTP {s} {b.get("detail") if isinstance(b, dict) else b}')
            return None
        if b.get('uuidCfdi') != u:
            hallazgo('ALTA', 'Lectura de QR', f'{nc}: UUID leído {b.get("uuidCfdi")} distinto de {u}')
        return b
    pagan = [a['numeroControl'] for a in alumnos[:N_ALUMNOS] if a['numeroControl'] not in no_pagan]
    tc = time.perf_counter()
    subidos = [c for c in en_paralelo(subir, pagan) if c]
    seg = time.perf_counter() - tc
    print(f'  {len(subidos)}/{len(pagan)} comprobantes subidos en {seg:.1f} s ({len(subidos) / seg:.1f}/s)')
    cotejo = defaultdict(int)
    for c in subidos:
        cotejo[c.get('cotejoDescripcion')] += 1
    print(f'  cotejo de descripción: {dict(cotejo)}  (se sembraron {sum(1 for i in range(N_ALUMNOS) if i % 50 in (7, 13))} recibos con diferencias)')

    # Mismo UUID: reutilizar el recibo de un alumno en el cargo de otro y en paralelo
    a1, a2 = alumnos[N_ALUMNOS + 20], alumnos[N_ALUMNOS + 21]
    nc_dup = no_pagan.pop()
    archivo_otro, _ = recibos[(pagan[0], 1)]
    cargo = cargo_abierto(nc_dup)
    s, b = api('POST', f'/pagos/autorizaciones/{cargo["id"]}/comprobante', usuario=nc_dup,
               archivos={'archivo': (archivo_otro, (TRABAJO / 'recibos' / archivo_otro).read_bytes(), 'application/pdf')}, etiqueta='UUID repetido')
    esperar(s, 409, 'Reutilizar el comprobante de otro alumno', b)
    no_pagan.add(nc_dup)
    # Carrera: dos alumnos sin pago suben el mismo recibo nuevo a la vez
    dos = list(no_pagan)[:2]
    filas_r = [f'r-carrera.pdf|{str(uuid.uuid4()).upper()}|{MONTO}|CURSO DE LENGUA EXTRANJERA|ISIC {dos[0]}']
    (TRABAJO / 'carrera.csv').write_text('\n'.join(filas_r), encoding='utf-8')
    subprocess.run(['java', '-cp', CLASSPATH, 'Recibos', str(TRABAJO / 'recibos'), str(TRABAJO / 'carrera.csv')], check=True)
    contenido_carrera = (TRABAJO / 'recibos' / 'r-carrera.pdf').read_bytes()

    def subir_carrera(nc):
        cargo = cargo_abierto(nc)
        return api('POST', f'/pagos/autorizaciones/{cargo["id"]}/comprobante', usuario=nc,
                   archivos={'archivo': ('r-carrera.pdf', contenido_carrera, 'application/pdf')}, etiqueta='UUID repetido simultáneo')
    res = en_paralelo(subir_carrera, dos, hilos=2)
    codigos = sorted(r[0] for r in res)
    print(f'  mismo recibo subido a la vez por dos alumnos: {codigos}')
    if codigos.count(201) != 1:
        hallazgo('ALTA', 'UUID duplicado', f'carrera de dos cargas del mismo CFDI terminó con {codigos}')
    for nc, (s, b) in zip(dos, res):   # se rechaza para que no afecte el resto
        if s == 201:
            api('POST', f'/pagos/comprobantes/{b["id"]}/rechazar', cuerpo={'motivo': 'Prueba de carrera'})

    # Archivo inválido y archivo enorme
    cargo = cargo_abierto(list(no_pagan)[2])
    s, b = api('POST', f'/pagos/autorizaciones/{cargo["id"]}/comprobante', usuario=list(no_pagan)[2],
               archivos={'archivo': ('virus.exe', b'MZ\x90\x00' * 100, 'application/pdf')}, etiqueta='comprobante inválido')
    esperar(s, 400, 'Archivo que no es PDF/JPG/PNG', b)
    s, b = api('POST', f'/pagos/autorizaciones/{cargo["id"]}/comprobante', usuario=list(no_pagan)[2],
               archivos={'archivo': ('grande.pdf', b'%PDF-1.4\n' + b'0' * (6 * 1024 * 1024), 'application/pdf')}, etiqueta='comprobante enorme')
    esperar(s, (400, 413), 'Archivo de más de 5 MB', b)
    s, b = api('POST', f'/pagos/autorizaciones/{cargo["id"]}/comprobante', usuario=list(no_pagan)[2],
               archivos={'archivo': ('roto.pdf', b'%PDF-1.4\nbasura sin estructura', 'application/pdf')}, etiqueta='comprobante PDF dañado')
    esperar(s, (400, 201), 'PDF dañado', b)
    if s == 201:
        api('POST', f'/pagos/comprobantes/{b["id"]}/rechazar', cuerpo={'motivo': 'PDF dañado'})

    # ---------------------------------------------------------------- consultas de coordinación con volumen
    t = fase('6. Consultas de coordinación con volumen')
    for _ in range(3):
        s, pendientes = api('GET', '/pagos/comprobantes/pendientes', etiqueta='GET /pagos/comprobantes/pendientes')
        api('GET', '/pagos/cargos', etiqueta='GET /pagos/cargos')
        api('GET', f'/grupos?periodoId={id_periodo}', etiqueta='GET /grupos')
        api('GET', '/alumnos?page=0&size=20', etiqueta='GET /alumnos (pagina)')
        api('GET', '/alumnos?busqueda=Hern&page=0&size=20', etiqueta='GET /alumnos (búsqueda)')
        api('GET', '/reportes/indicadores', etiqueta='GET /reportes/indicadores')
        api('GET', f'/reportes/academico?periodoId={id_periodo}', etiqueta='GET /reportes/academico')
        api('GET', '/facilitadores', etiqueta='GET /facilitadores')
        api('GET', '/pagos/cargos', usuario='jefa.dgtv', etiqueta='GET /pagos/cargos (jefa)')
    print(f'  {len(pendientes)} comprobantes pendientes listados')

    # ---------------------------------------------------------------- validación
    t = fase('7. Validación de comprobantes por coordinación')
    def validar(c):
        s, b = api('POST', f'/pagos/comprobantes/{c["id"]}/validar', cuerpo={'idGrupo': None}, etiqueta='POST validar comprobante')
        return s
    rechazados = set()
    aceptables = [c for c in pendientes if c.get('cotejoDescripcion') in ('Coincide', 'Sin texto')]
    sospechosos = [c for c in pendientes if c not in aceptables]
    res = en_paralelo(validar, aceptables, hilos=8)
    print(f'  validados {sum(1 for r in res if r == 200)}/{len(aceptables)}; con diferencias en cotejo (se rechazan): {len(sospechosos)}')
    for c in sospechosos:
        rechazados.add(c['numeroControl'])
        api('POST', f'/pagos/comprobantes/{c["id"]}/rechazar', cuerpo={'motivo': 'La descripción del recibo no corresponde al cargo.'}, etiqueta='POST rechazar comprobante')
    # Doble validación simultánea del mismo comprobante
    if sospechosos:
        nc_s = sospechosos[0]['numeroControl']
        archivo, _ = recibos[(nc_s, 1)]
        filas_r = [f'r-revalida.pdf|{str(uuid.uuid4()).upper()}|{MONTO}|CURSO DE LENGUA EXTRANJERA|ISIC {nc_s}']
        (TRABAJO / 'revalida.csv').write_text('\n'.join(filas_r), encoding='utf-8')
        subprocess.run(['java', '-cp', CLASSPATH, 'Recibos', str(TRABAJO / 'recibos'), str(TRABAJO / 'revalida.csv')], check=True)
        cargo = cargo_abierto(nc_s)
        s, nuevo = api('POST', f'/pagos/autorizaciones/{cargo["id"]}/comprobante', usuario=nc_s,
                       archivos={'archivo': ('r-revalida.pdf', (TRABAJO / 'recibos' / 'r-revalida.pdf').read_bytes(), 'application/pdf')})
        if s == 201:
            res = en_paralelo(lambda _: api('POST', f'/pagos/comprobantes/{nuevo["id"]}/validar', cuerpo={'idGrupo': None}, etiqueta='validación simultánea')[0], range(4), hilos=4)
            s, cargos = api('GET', f'/pagos/alumnos/{nc_s}/cargos')
            saldo = [c['saldoPendiente'] for c in cargos if c['concepto'] == 'Curso de nivel'][0]
            print(f'  4 validaciones simultáneas del mismo comprobante: {sorted(res)}; saldo final {saldo}')
            if res.count(200) != 1 or float(saldo) != 0.0:
                hallazgo('ALTA', 'Validación', f'validación simultánea: {sorted(res)}, saldo {saldo}')
            rechazados.discard(nc_s)

    # ---------------------------------------------------------------- actas
    t = fase('8. Captura y cierre de actas por los facilitadores')
    calificaciones = {}

    def acta(g):
        correo = g['facilitador']['correo']
        s, a = api('GET', f'/grupos/{g["id"]}/acta', usuario=correo, etiqueta='GET acta') if False else (None, None)
        s, det = api('GET', f'/grupos/{g["id"]}', usuario=correo, etiqueta='GET /grupos/{id} (facilitador)')
        lista = det.get('alumnos', []) if isinstance(det, dict) else []
        if not lista:
            return ('sin alumnos', g['clave'])
        cal = []
        for x in lista:
            nc = x['numeroControl']
            valor = 95 if nc in {p['numeroControl'] for p in progresion} else random.choice([55, 62, 70, 75, 80, 85, 90, 95, 100])
            calificaciones[nc] = valor
            cal.append({'numeroControl': nc, 'calificacion': valor})
        s, b = api('PUT', f'/grupos/{g["id"]}/acta', usuario=correo, cuerpo={'calificaciones': cal}, etiqueta='PUT acta')
        if s != 200:
            return ('captura', s, b.get('detail') if isinstance(b, dict) else b)
        s, b = api('POST', f'/grupos/{g["id"]}/acta/cierre', usuario=correo, etiqueta='POST cierre de acta')
        if s == 200:
            return ('cerrada',)
        # Coordinación quita a quien no pagó y se vuelve a intentar
        sin_pago = [x['numeroControl'] for x in lista if x['numeroControl'] in no_pagan | rechazados]
        for nc in sin_pago:
            api('DELETE', f'/grupos/{g["id"]}/alumnos/{nc}', etiqueta='DELETE alumno del grupo')
        cal = [c for c in cal if c['numeroControl'] not in sin_pago]
        api('PUT', f'/grupos/{g["id"]}/acta', usuario=correo, cuerpo={'calificaciones': cal}, etiqueta='PUT acta')
        s2, b2 = api('POST', f'/grupos/{g["id"]}/acta/cierre', usuario=correo, etiqueta='POST cierre de acta')
        return ('cerrada tras quitar sin pago' if s2 == 200 else f'no cerró: {b2.get("detail") if isinstance(b2, dict) else b2}', s, len(sin_pago))
    resultados_actas = en_paralelo(acta, grupos, hilos=8)
    resumen = defaultdict(int)
    for r in resultados_actas:
        resumen[r[0] if isinstance(r[0], str) and not r[0].startswith('no cerró') else 'no cerró'] += 1
        if isinstance(r[0], str) and r[0].startswith('no cerró'):
            hallazgo('MEDIA', 'Cierre de acta', r[0])
    print(f'  actas: {dict(resumen)}')
    # Facilitador ajeno intentando capturar
    s, b = api('PUT', f'/grupos/{grupos[0]["id"]}/acta', usuario=grupos[1]['facilitador']['correo'],
               cuerpo={'calificaciones': [{'numeroControl': alumnos[0]['numeroControl'], 'calificacion': 100}]}, etiqueta='acta ajena')
    esperar(s, (403, 422), 'Facilitador modifica el acta de otro', b)
    s, b = api('PUT', f'/grupos/{grupos[0]["id"]}/acta', usuario=grupos[0]['facilitador']['correo'],
               cuerpo={'calificaciones': [{'numeroControl': alumnos[0]['numeroControl'], 'calificacion': 100}]}, etiqueta='acta cerrada')
    esperar(s, (409, 422), 'Modificar un acta cerrada', b)

    # ---------------------------------------------------------------- boletas, avance y constancias de avance
    t = fase('9. Boletas, avance y constancias de avance de nivel')
    muestra = random.sample([a['numeroControl'] for a in alumnos[:N_ALUMNOS] if a['numeroControl'] not in no_pagan], 60)

    def revisar(nc):
        s, bol = api('GET', '/boletas/mias', usuario=nc, etiqueta='GET /boletas/mias')
        s, av = api('GET', f'/alumnos/{nc}/avance', usuario=nc, etiqueta='GET avance')
        s, cons = api('GET', '/constancias/avance/mias', usuario=nc, etiqueta='GET constancias avance')
        cal = calificaciones.get(nc)
        esperado = 1 if cal is not None and cal >= 70 else 0
        if isinstance(cons, list) and len(cons) != esperado:
            hallazgo('ALTA', 'Constancia de avance', f'{nc} con calificación {cal}: {len(cons)} constancias')
        if isinstance(bol, list) and bol and cal is not None and bol[0].get('calificacion') != cal:
            hallazgo('ALTA', 'Boletas', f'{nc}: boleta {bol[0].get("calificacion")} ≠ acta {cal}')
        if cons and isinstance(cons, list):
            s, pdf = api('GET', f'/constancias/avance/{cons[0]["idInscripcion"]}/pdf', usuario=nc, etiqueta='GET PDF constancia avance')
            if s != 200 or not bytes(pdf[:5]) == b'%PDF-':
                hallazgo('ALTA', 'Constancia de avance', f'{nc}: PDF HTTP {s}')
        return True
    en_paralelo(revisar, muestra)

    # ---------------------------------------------------------------- progresión de 10 niveles
    t = fase(f'10. {N_PROGRESION} alumnos recorren los 10 niveles hasta la constancia de acreditación')
    fac_p = facilitadores[2]
    for nivel in range(2, 11):
        s, g = api('POST', '/grupos', cuerpo={'clave': 'P', 'idNivel': niveles[nivel - 1]['id'], 'idPeriodo': id_periodo,
                                              'idModalidad': entre_semana, 'idFacilitador': fac_p['id'], 'idAula': aulas[0], 'cupo': 30,
                                              'horarios': [{'diaSemana': 5, 'horaInicio': f'{6 + nivel:02d}:00', 'horaFin': f'{7 + nivel:02d}:00'}]},
                   etiqueta='POST /grupos')
        if not esperar(s, (200, 201), f'Grupo de nivel {nivel}', g):
            break
        en_paralelo(lambda a: asignar((g['id'], a['numeroControl'])), progresion)
        comps = [c for c in en_paralelo(lambda a: subir(a['numeroControl'], nivel), progresion) if c]
        en_paralelo(validar, comps, hilos=8)
        s, b = api('PUT', f'/grupos/{g["id"]}/acta', usuario=fac_p['correo'],
                   cuerpo={'calificaciones': [{'numeroControl': a['numeroControl'], 'calificacion': 90 + nivel % 10} for a in progresion]}, etiqueta='PUT acta')
        s, b = api('POST', f'/grupos/{g["id"]}/acta/cierre', usuario=fac_p['correo'], etiqueta='POST cierre de acta')
        if not esperar(s, 200, f'Cierre del acta de nivel {nivel}', b):
            break
    folios = 0

    def constancia(i_a):
        i, a = i_a
        nc = a['numeroControl']
        s, eleg = api('GET', '/constancias/disponibilidad', usuario=nc, etiqueta='GET disponibilidad constancia')
        s, sol = api('POST', '/constancias', usuario=nc, etiqueta='POST solicitar constancia')
        if s not in (200, 201):
            hallazgo('ALTA', 'Constancia', f'{nc} no pudo solicitar: {sol.get("detail") if isinstance(sol, dict) else sol}')
            return None
        s, em = api('POST', f'/constancias/{sol["id"]}/autorizacion', cuerpo={'folio': f'{1600 + i}'}, etiqueta='POST autorizar constancia')
        if not esperar(s, 200, 'Autorizar constancia', em):
            return None
        s, pdf = api('GET', f'/constancias/{sol["id"]}/pdf', usuario=nc, etiqueta='GET PDF constancia acreditación')
        return s == 200 and bytes(pdf[:5]) == b'%PDF-'
    res = en_paralelo(constancia, list(enumerate(progresion)), hilos=6)
    print(f'  constancias de acreditación emitidas y descargadas: {sum(1 for r in res if r)}/{len(progresion)}')
    s, b = api('POST', '/constancias/emision', cuerpo={'numeroControl': alumnos[200]['numeroControl'], 'folio': 'X1'}, etiqueta='emisión sin requisitos')
    esperar(s, (400, 422), 'Emitir constancia a quien no acreditó 10 niveles', b)
    s, b = api('POST', '/constancias', usuario=progresion[0]['numeroControl'], etiqueta='constancia duplicada')
    esperar(s, (400, 409, 422), 'Solicitar una segunda constancia', b)
    s, sol = api('GET', '/constancias?estatus=Emitida', etiqueta='GET /constancias')

    # ---------------------------------------------------------------- exámenes y diagnósticos
    t = fase('11. Exámenes de colocación y evaluaciones diagnósticas')
    examen = alumnos[N_ALUMNOS + 20:N_ALUMNOS + 60]

    def flujo_examen(a):
        nc = a['numeroControl']
        s, e = api('POST', '/examenes', usuario=nc, cuerpo={'tipo': 'Colocación'}, etiqueta='POST solicitar examen')
        if s not in (200, 201):
            return ('solicitud', s, e.get('detail') if isinstance(e, dict) else e)
        s, e2 = api('PUT', f'/examenes/{e["id"]}/programacion', cuerpo={'fechaHoraAplicacion': '2026-11-20T10:00:00'}, etiqueta='PUT programar examen')
        if s != 200:
            return ('programación', s, e2.get('detail') if isinstance(e2, dict) else e2)
        s, e3 = api('PUT', f'/examenes/{e["id"]}/resultado', cuerpo={'idNivelAsignado': niveles[3]['id'], 'puntaje': 72, 'observaciones': 'Prueba de carga'}, etiqueta='PUT resultado examen')
        if s != 200:
            return ('resultado', s, e3.get('detail') if isinstance(e3, dict) else e3)
        s, av = api('GET', f'/alumnos/{nc}/avance', usuario=nc)
        return ('ok', av)
    res = en_paralelo(flujo_examen, examen, hilos=8)
    pasos = defaultdict(int)
    for r in res:
        pasos[r[0]] += 1
    print(f'  exámenes: {dict(pasos)}')
    fallas = [r for r in res if r[0] != 'ok']
    if fallas:
        hallazgo('MEDIA', 'Exámenes', f'{len(fallas)} flujos no terminaron; primero: {fallas[0]}')

    def flujo_diag(a):
        nc = a['numeroControl']
        s, d = api('POST', '/diagnosticos', usuario=nc, etiqueta='POST solicitar diagnóstico')
        if s not in (200, 201):
            return ('solicitud', s, d.get('detail') if isinstance(d, dict) else d)
        s, d2 = api('PUT', f'/diagnosticos/{d["id"]}/programacion', cuerpo={'fechaHoraAplicacion': '2026-11-21T09:00:00', 'enlace': 'https://meet.example.org/diag'}, etiqueta='PUT programar diagnóstico')
        if s != 200:
            return ('programación', s, d2.get('detail') if isinstance(d2, dict) else d2)
        s, d3 = api('PUT', f'/diagnosticos/{d["id"]}/resultado', cuerpo={'puntaje': 64, 'recomendacion': 'Colocación', 'observaciones': 'Prueba'}, etiqueta='PUT resultado diagnóstico')
        return ('ok',) if s == 200 else ('resultado', s, d3.get('detail') if isinstance(d3, dict) else d3)
    res = en_paralelo(flujo_diag, alumnos[300:330], hilos=8)
    pasos = defaultdict(int)
    for r in res:
        pasos[r[0]] += 1
    print(f'  diagnósticos: {dict(pasos)}')
    fallas = [r for r in res if r[0] != 'ok']
    if fallas:
        hallazgo('MEDIA', 'Diagnósticos', f'{len(fallas)} flujos no terminaron; primero: {fallas[0]}')

    # ---------------------------------------------------------------- lenguas maternas
    t = fase('12. Lenguas maternas')
    for nombre in ('Náhuatl', "Mixteco (Tu'un Savi)", "Tlapaneco (Me'phaa)", 'Amuzgo'):
        api('POST', '/lenguas-maternas/catalogo', cuerpo={'nombre': nombre}, etiqueta='POST lengua')
    s, catalogo = api('GET', '/lenguas-maternas/catalogo', usuario=alumnos[0]['numeroControl'])
    hablantes = [a for a in alumnos if a['hablaLenguaMaterna']][:40]
    documento = (TRABAJO / 'recibos' / recibos[(alumnos[0]['numeroControl'], 1)][0]).read_bytes()

    def registrar_lengua(a):
        lengua = random.choice(catalogo)
        return api('POST', f'/lenguas-maternas/registros?idLengua={lengua["id"]}&nivelDominio=Intermedio', usuario=a['numeroControl'],
                   archivos={'documento': ('constancia.pdf', documento, 'application/pdf')}, etiqueta='POST registro lengua')[0]
    res = en_paralelo(registrar_lengua, hablantes)
    s, regs = api('GET', '/lenguas-maternas/registros', etiqueta='GET registros lengua')
    for r in (regs if isinstance(regs, list) else [])[:20]:
        api('PATCH', f'/lenguas-maternas/registros/{r["id"]}/estatus', cuerpo={'estatus': 'Validado'}, etiqueta='PATCH validar lengua')
    print(f'  registros: {sum(1 for r in res if r in (200, 201))}/{len(hablantes)}; listados {len(regs) if isinstance(regs, list) else regs}')
    s, b = api('POST', f'/lenguas-maternas/registros?idLengua={catalogo[0]["id"]}&nivelDominio=Alto', usuario=alumnos[1]['numeroControl'],
               archivos={'documento': ('c.pdf', documento, 'application/pdf')}, etiqueta='lengua sin declararla')
    esperar(s, (403, 422), 'Registrar lengua materna sin haberla declarado', b)

    # ---------------------------------------------------------------- mensajería
    t = fase('13. Mensajería y notificaciones con volumen')
    s, dest = api('GET', '/mensajes/destinatarios', etiqueta='GET destinatarios')
    difusiones = dest.get('difusiones', []) if isinstance(dest, dict) else []
    clave_alumnos = next((d.get('clave') or d.get('id') for d in difusiones if 'lumno' in json.dumps(d, ensure_ascii=False)), None)
    if clave_alumnos:
        s, b = api('POST', '/mensajes', cuerpo={'difusion': clave_alumnos, 'asunto': 'Aviso general de carga', 'mensaje': 'Mensaje de difusión a todos los alumnos.'}, etiqueta='POST difusión a alumnos')
        esperar(s, (200, 201), 'Difusión a todos los alumnos', b)
    else:
        hallazgo('BAJA', 'Mensajería', f'no se encontró la difusión a alumnos en {difusiones}')

    def leer(nc):
        s, rec = api('GET', '/mensajes/recibidos', usuario=nc, etiqueta='GET mensajes recibidos')
        api('GET', '/notificaciones/recibidas', usuario=nc, etiqueta='GET notificaciones (alumno)')
        if isinstance(rec, list) and rec:
            api('PATCH', f'/mensajes/{rec[0]["id"]}/leido', usuario=nc, etiqueta='PATCH mensaje leído')
        return len(rec) if isinstance(rec, list) else -1
    res = en_paralelo(leer, [a['numeroControl'] for a in alumnos[:120]])
    print(f'  alumnos que recibieron la difusión: {sum(1 for r in res if r > 0)}/120')
    s, b = api('POST', '/mensajes', usuario='jefa.dgtv', cuerpo={'asunto': 'x', 'mensaje': 'x', 'difusion': clave_alumnos}, etiqueta='difusión jefa')
    esperar(s, (403, 422, 400), 'Jefa envía difusión (solo lectura)', b)

    # ---------------------------------------------------------------- seguridad
    t = fase('14. Permisos entre roles y datos ajenos')
    a, otro = alumnos[3]['numeroControl'], alumnos[4]['numeroControl']
    casos = [
        ('GET', f'/alumnos/{otro}', a, None, 'Alumno consulta expediente ajeno'),
        ('GET', f'/alumnos/{otro}/avance', a, None, 'Alumno consulta avance ajeno'),
        ('GET', f'/pagos/alumnos/{otro}/cargos', a, None, 'Alumno consulta cargos ajenos'),
        ('GET', '/pagos/comprobantes/pendientes', a, None, 'Alumno lista comprobantes'),
        ('GET', '/alumnos?page=0&size=5', a, None, 'Alumno busca alumnos'),
        ('POST', f'/pagos/comprobantes/{subidos[5]["id"]}/validar', a, {'idGrupo': None}, 'Alumno valida comprobante'),
        ('GET', f'/pagos/comprobantes/{subidos[5]["id"]}/archivo', a, None, 'Alumno descarga comprobante ajeno'),
        ('POST', '/grupos', 'jefa.dgtv', {'clave': 'J', 'idNivel': 1, 'idPeriodo': id_periodo, 'idModalidad': 1, 'idFacilitador': 1, 'idAula': 1, 'cupo': 1, 'horarios': []}, 'Jefa abre grupo'),
        ('PUT', '/constancias/configuracion', facilitadores[0]['correo'], {}, 'Facilitador cambia firmantes'),
        ('GET', '/reportes/indicadores', facilitadores[0]['correo'], None, 'Facilitador consulta reportes'),
        ('POST', f'/grupos/{grupos[2]["id"]}/alumnos', facilitadores[0]['correo'], {'numeroControl': a}, 'Facilitador asigna alumnos'),
    ]
    for metodo, ruta, usuario, cuerpo, nombre in casos:
        s, b = api(metodo, ruta, usuario=usuario, cuerpo=cuerpo, etiqueta='seguridad')
        if s in (200, 201):
            hallazgo('ALTA', 'Permisos', f'{nombre}: permitido (HTTP {s})')
        elif s not in (400, 401, 403, 404, 422):
            hallazgo('MEDIA', 'Permisos', f'{nombre}: HTTP {s}')
    # Login real (JWT) y bloqueo tras 5 intentos fallidos
    s, b = api('POST', '/auth/login', cuerpo={'usuario': alumnos[10]['numeroControl'], 'contrasena': CONTRASENA}, etiqueta='POST login')
    if esperar(s, 200, 'Login correcto', b):
        token = b.get('token')
        s, perfil = api('GET', '/auth/perfil', token=token, etiqueta='GET perfil (JWT)')
        esperar(s, 200, 'Perfil con JWT', perfil)
        s, x = api('GET', f'/alumnos/{otro}', token=token, etiqueta='seguridad')
        if s == 200:
            hallazgo('ALTA', 'Permisos', 'con JWT de alumno se lee el expediente de otro')
    victima = alumnos[11]['numeroControl']
    codigos = [api('POST', '/auth/login', cuerpo={'usuario': victima, 'contrasena': 'incorrecta'}, etiqueta='login fallido')[0] for _ in range(6)]
    s, b = api('POST', '/auth/login', cuerpo={'usuario': victima, 'contrasena': CONTRASENA}, etiqueta='login tras bloqueo')
    print(f'  intentos fallidos: {codigos}; login correcto después: {s}')
    if s == 200:
        hallazgo('ALTA', 'Seguridad', 'tras 6 intentos fallidos el login correcto sigue entrando (no hay bloqueo)')
    s, b = api('POST', '/auth/login', cuerpo={'usuario': "' OR '1'='1", 'contrasena': "' OR '1'='1"}, etiqueta='login inyección')
    if s == 200:
        hallazgo('ALTA', 'Seguridad', 'login aceptó una inyección SQL')

    # ---------------------------------------------------------------- consultas finales
    t = fase('15. Consultas finales con todo el volumen')
    for _ in range(3):
        api('GET', '/pagos/cargos', etiqueta='GET /pagos/cargos')
        api('GET', '/pagos/comprobantes?estatus=Validado', etiqueta='GET comprobantes validados')
        api('GET', f'/reportes/academico?periodoId={id_periodo}', etiqueta='GET /reportes/academico')
        api('GET', '/reportes/indicadores', etiqueta='GET /reportes/indicadores')
        api('GET', f'/alumnos/{progresion[0]["numeroControl"]}/integral', etiqueta='GET expediente integral')
        api('GET', '/examenes', etiqueta='GET /examenes')
        api('GET', '/diagnosticos', etiqueta='GET /diagnosticos')
        api('GET', '/notificaciones', etiqueta='GET /notificaciones')
        api('GET', '/mensajes/enviados', etiqueta='GET mensajes enviados')
        api('GET', '/constancias?estatus=Emitida', etiqueta='GET /constancias')

    # ---------------------------------------------------------------- informe
    print(f'\n=== Resumen ({time.perf_counter() - t_total:.0f} s en total) ===')
    filas_m = []
    for clave, tiempos in metricas.items():
        ordenados = sorted(tiempos)
        p95 = ordenados[min(len(ordenados) - 1, int(len(ordenados) * 0.95))]
        filas_m.append({'endpoint': clave, 'n': len(tiempos), 'p50_ms': round(statistics.median(ordenados)),
                        'p95_ms': round(p95), 'max_ms': round(ordenados[-1]), 'estados': dict(estados[clave])})
    filas_m.sort(key=lambda f: -f['p95_ms'])
    for f in filas_m[:25]:
        print(f"  {f['endpoint'][:55]:55} n={f['n']:5} p50={f['p50_ms']:6} p95={f['p95_ms']:6} max={f['max_ms']:6} {f['estados']}")
    total = sum(len(v) for v in metricas.values())
    print(f'  peticiones: {total}; hallazgos: {len(hallazgos)}')
    (TRABAJO / 'resultado.json').write_text(json.dumps({'metricas': filas_m, 'hallazgos': hallazgos}, ensure_ascii=False, indent=1), encoding='utf-8')


if __name__ == '__main__':
    main()
