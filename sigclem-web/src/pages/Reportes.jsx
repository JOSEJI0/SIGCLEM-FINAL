import { useEffect, useState } from 'react';
import { BarChart3, FileDown, FileSpreadsheet } from 'lucide-react';
import { obtenerPeriodos } from '../api/catalogos';
import { obtenerReporteAcademico } from '../api/reportes';
import { mensajeError } from '../api/cliente';
import { Alerta, Boton, Campo, Cargando, Tarjeta, claseEntrada } from '../components/ui';

const numero = (valor) => new Intl.NumberFormat('es-MX', { maximumFractionDigits: 1 }).format(valor);

function hoyLocal() {
  const hoy = new Date();
  return `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
}

/** Celda CSV: se encierra entre comillas y se duplican las comillas internas. */
const celda = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;

/** Descarga el reporte como CSV (se abre directamente en Excel; el BOM conserva los acentos). */
function descargarExcel(reporte) {
  const filas = [
    ['Reporte académico detallado', reporte.periodo.nombre],
    ['Periodo del', reporte.periodo.fechaInicio, 'al', reporte.periodo.fechaFin],
    [],
    ['Resumen'],
    ['Grupos', reporte.grupos], ['Actas cerradas', reporte.actasCerradas], ['Inscripciones', reporte.alumnosInscritos],
    ['Alumnos distintos', reporte.alumnosUnicos], ['Calificaciones pendientes', reporte.calificacionesPendientes],
    ['Promedio publicado', reporte.promedioPublicado == null ? '' : reporte.promedioPublicado.toFixed(1)],
    ['Acreditados', reporte.acreditados], ['No acreditados', reporte.noAcreditados],
    [],
    ['Detalle por nivel'],
    ['Nivel', 'Grupos', 'Inscripciones', 'Promedio publicado', 'Acreditados', 'No acreditados', 'Pendientes'],
    ...reporte.porNivel.map((n) => [`${n.numeroNivel}. ${n.nivel}`, n.grupos, n.alumnosInscritos,
      n.promedioPublicado == null ? '' : n.promedioPublicado.toFixed(1), n.acreditados, n.noAcreditados, n.calificacionesPendientes]),
    [],
    ['Aprobación por carrera y nivel'],
    ['Carrera', 'Nivel', 'Inscripciones', 'Promedio publicado', 'Acreditados', 'No acreditados', 'Pendientes'],
    ...(reporte.porCarreraYNivel || []).map((f) => [f.carrera, `${f.numeroNivel}. ${f.nivel}`, f.alumnosInscritos,
      f.promedioPublicado == null ? '' : f.promedioPublicado.toFixed(1), f.acreditados, f.noAcreditados, f.calificacionesPendientes]),
    [],
    ['Detalle por grupo y alumno'],
    ['Grupo', 'Curso', 'Modalidad', 'Aula', 'Facilitador', 'Horario', 'Cupo', 'Inscritos', 'Acta',
      'Inicio captura', 'Fin captura', 'Fecha de cierre',
      'Control', 'Alumno', 'Carrera', 'Semestre', 'Sexo', 'Estatus alumno', 'Calificación', 'Resultado',
      'Calificación nivel secundario', 'Resultado nivel secundario'],
    ...(reporte.porGrupo || []).flatMap((grupo) => grupo.alumnos.length
      ? grupo.alumnos.map((alumno) => [
        grupo.clave, grupo.curso, grupo.modalidad, grupo.aula, grupo.facilitador, grupo.horarios.join('; '),
        grupo.cupo ?? '', grupo.alumnosInscritos, grupo.estatusActa,
        grupo.fechaInicioCaptura ?? '', grupo.fechaFinCaptura ?? '', grupo.fechaCierreActa ?? '',
        alumno.numeroControl, alumno.nombreCompleto, alumno.carrera, alumno.semestre,
        alumno.sexo === 'H' ? 'Hombre' : alumno.sexo === 'M' ? 'Mujer' : '',
        alumno.estatusAlumno,
        alumno.calificacion ?? '', alumno.resultado,
        alumno.calificacionSecundaria ?? '', alumno.resultadoSecundario ?? '',
      ])
      : [[grupo.clave, grupo.curso, grupo.modalidad, grupo.aula, grupo.facilitador, grupo.horarios.join('; '),
        grupo.cupo ?? '', 0, grupo.estatusActa, grupo.fechaInicioCaptura ?? '', grupo.fechaFinCaptura ?? '',
        grupo.fechaCierreActa ?? '', '', 'Sin alumnos inscritos']]),
    [],
    ['Estudiantes con lengua materna registrados en el periodo'],
    ['Número de control', 'Nombre', 'Carrera', 'Semestre', 'Sexo', 'Estatus del alumno', 'Lengua',
      'Nivel de dominio', 'Estatus del registro', 'Fecha de registro'],
    ...(reporte.alumnosLenguaMaterna || []).map((alumno) => [
      alumno.numeroControl, alumno.nombreCompleto, alumno.carrera, alumno.semestre ?? '',
      alumno.sexo === 'H' ? 'Hombre' : alumno.sexo === 'M' ? 'Mujer' : '',
      alumno.estatusAlumno, alumno.lengua, alumno.nivelDominio ?? '', alumno.estatusRegistro, alumno.fechaRegistro,
    ]),
  ];
  const csv = '\uFEFF' + filas.map((f) => f.map(celda).join(',')).join('\r\n');
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = `reporte-academico-${reporte.periodo.nombre.replace(/\s+/g, '-')}.csv`;
  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

function imprimirReporte() {
  const detalles = [...document.querySelectorAll('details')];
  const cerrados = detalles.filter((detalle) => !detalle.open);
  detalles.forEach((detalle) => { detalle.open = true; });
  window.addEventListener('afterprint', () => {
    cerrados.forEach((detalle) => { detalle.open = false; });
  }, { once: true });
  window.print();
}

export default function Reportes() {
  const [periodos, setPeriodos] = useState([]);
  const [periodoId, setPeriodoId] = useState('');
  const [reporte, setReporte] = useState(null);
  const [cargandoPeriodos, setCargandoPeriodos] = useState(true);
  const [cargandoReporte, setCargandoReporte] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    obtenerPeriodos()
      .then((data) => {
        setPeriodos(data);
        const hoy = hoyLocal();
        const periodoActual = data.find((periodo) => periodo.fechaInicio <= hoy && periodo.fechaFin >= hoy);
        if (data.length) setPeriodoId(String((periodoActual || data[0]).id));
      })
      .catch((e) => setError(mensajeError(e)))
      .finally(() => setCargandoPeriodos(false));
  }, []);

  useEffect(() => {
    if (!periodoId) {
      setReporte(null);
      return;
    }
    setCargandoReporte(true);
    obtenerReporteAcademico(periodoId)
      .then((data) => {
        setReporte(data);
        setError('');
      })
      .catch((e) => setError(mensajeError(e)))
      .finally(() => setCargandoReporte(false));
  }, [periodoId]);

  if (cargandoPeriodos) return <Cargando texto="Cargando periodos…" />;

  return (
    <div className="grid gap-[18px]">
      <div className="flex flex-wrap items-end justify-between gap-3 print:hidden">
        <div>
          <h1 className="flex items-center gap-2 text-xl font-bold"><BarChart3 className="h-5 w-5" /> Reporte académico</h1>
          <p className="mt-1 text-sm text-tenue">Resumen, resultados y detalle de grupos y alumnos por periodo académico.</p>
        </div>
        {periodos.length > 0 && (
          <div className="flex items-end gap-2">
            <Campo etiqueta="Periodo" id="periodoReporte">
              <select id="periodoReporte" className={claseEntrada} value={periodoId}
                onChange={(evento) => setPeriodoId(evento.target.value)}>
                {periodos.map((periodo) => (
                  <option key={periodo.id} value={periodo.id}>{periodo.nombre}</option>
                ))}
              </select>
            </Campo>
            <Boton variante="secundario" type="button" className="no-imprimir" disabled={!reporte} onClick={() => descargarExcel(reporte)}>
              <FileSpreadsheet className="h-4 w-4" /> Excel
            </Boton>
            <Boton variante="secundario" type="button" className="no-imprimir" disabled={!reporte} onClick={imprimirReporte}
              title="Abre la impresión: elige «Guardar como PDF»">
              <FileDown className="h-4 w-4" /> PDF
            </Boton>
          </div>
        )}
      </div>

      {error && <Alerta tipo="error">{error}</Alerta>}
      {!error && periodos.length === 0 && (
        <Alerta tipo="info">No hay periodos académicos registrados para consultar.</Alerta>
      )}
      {cargandoReporte && <Cargando texto="Generando reporte…" />}
      {reporte && !cargandoReporte && (
        <>
          <Alerta tipo="info">
            El resumen muestra alumnos únicos por separado de las inscripciones. En grupos de dos niveles, una misma
            inscripción cuenta una vez en cada nivel. Las calificaciones y promedios solo se publican al cerrar el acta;
            una calificación aprobatoria puede seguir pendiente de aplicar al avance si falta liquidar el curso.
            El listado de lengua materna incluye los registros presentados dentro de las fechas del periodo, tengan o no
            inscripción a un grupo de inglés.
          </Alerta>
          <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
            <Tarjeta titulo="Grupos">
              <p className="text-2xl font-semibold">{reporte.grupos}</p>
              <p className="mt-1 text-xs text-tenue">
                {reporte.actasCerradas} actas cerradas · {reporte.grupos - reporte.actasCerradas} por cerrar
              </p>
            </Tarjeta>
            <Tarjeta titulo="Alumnado inscrito">
              <p className="text-2xl font-semibold">{reporte.alumnosInscritos}</p>
              <p className="mt-1 text-xs text-tenue">{reporte.alumnosUnicos} alumnos distintos</p>
            </Tarjeta>
            <Tarjeta titulo="Promedio publicado">
              <p className="text-2xl font-semibold">
                {reporte.promedioPublicado == null ? '—' : numero(reporte.promedioPublicado)}
              </p>
              <p className="mt-1 text-xs text-tenue">Solo considera actas cerradas</p>
            </Tarjeta>
            <Tarjeta titulo="Resultados publicados">
              <p className="text-2xl font-semibold">{reporte.acreditados + reporte.noAcreditados}</p>
              <p className="mt-1 text-xs text-tenue">
                {reporte.acreditados} acreditados · {reporte.noAcreditados} no acreditados
              </p>
            </Tarjeta>
          </div>

          <Tarjeta titulo={`Detalle de grupos y alumnado · ${reporte.periodo.nombre}`} icono={FileSpreadsheet}
            extra={`${(reporte.porGrupo || []).length} grupos · ${reporte.periodo.fechaInicio} al ${reporte.periodo.fechaFin}`}>
            {!reporte.porGrupo?.length ? (
              <p className="text-sm text-tenue">No hay grupos registrados en este periodo.</p>
            ) : (
              <div className="grid gap-2">
                {reporte.porGrupo.map((grupo) => (
                  <details key={grupo.id} className="rounded-lg border border-linea bg-white">
                    <summary className="cursor-pointer list-none p-3 hover:bg-fondo">
                      <span className="font-semibold">{grupo.clave} · {grupo.curso}</span>
                      <span className="ml-2 text-sm text-tenue">{grupo.alumnosInscritos} alumnos · Acta: {grupo.estatusActa}</span>
                    </summary>
                    <div className="grid gap-3 border-t border-linea p-3">
                      <dl className="grid gap-x-4 gap-y-2 text-sm sm:grid-cols-2 lg:grid-cols-4">
                        <div><dt className="text-xs text-tenue">Modalidad</dt><dd>{grupo.modalidad}</dd></div>
                        <div><dt className="text-xs text-tenue">Aula / plataforma</dt><dd>{grupo.aula}</dd></div>
                        <div><dt className="text-xs text-tenue">Facilitador</dt><dd>{grupo.facilitador}</dd></div>
                        <div><dt className="text-xs text-tenue">Cupo</dt><dd>{grupo.cupo ?? 'No especificado'} · {grupo.alumnosInscritos} inscritos</dd></div>
                        <div><dt className="text-xs text-tenue">Horario</dt><dd>{grupo.horarios.length ? grupo.horarios.join(' · ') : 'Sin horario registrado'}</dd></div>
                        <div><dt className="text-xs text-tenue">Periodo de captura</dt>
                          <dd>{grupo.fechaInicioCaptura || '—'} al {grupo.fechaFinCaptura || '—'}</dd>
                        </div>
                        <div><dt className="text-xs text-tenue">Cierre de acta</dt>
                          <dd>{grupo.fechaCierreActa || 'Sin cerrar'}{grupo.cerradaPor ? ` · ${grupo.cerradaPor}` : ''}</dd>
                        </div>
                        {grupo.observacionCierre && (
                          <div className="sm:col-span-2 lg:col-span-4">
                            <dt className="text-xs text-tenue">Observación de cierre</dt><dd>{grupo.observacionCierre}</dd>
                          </div>
                        )}
                      </dl>
                      {grupo.alumnos.length === 0 ? (
                        <p className="text-sm text-tenue">Grupo sin alumnos inscritos.</p>
                      ) : (
                        <div className="overflow-x-auto">
                          <table className="w-full min-w-[850px] text-left text-sm">
                            <thead><tr className="border-b border-linea text-xs text-tenue">
                              <th className="py-2 pr-3">Núm. control</th><th className="py-2 pr-3">Alumno</th>
                              <th className="py-2 pr-3">Carrera</th><th className="py-2 pr-3">Sem.</th>
                              <th className="py-2 pr-3">Sexo</th><th className="py-2 pr-3">Estatus</th>
                              <th className="py-2 pr-3">Calificación</th><th className="py-2 pr-3">Resultado</th>
                              {grupo.curso.includes(' + ') && <><th className="py-2 pr-3">Calif. secundaria</th><th className="py-2">Resultado secundario</th></>}
                            </tr></thead>
                            <tbody>{grupo.alumnos.map((alumno) => (
                              <tr key={alumno.numeroControl} className="border-b border-linea last:border-0">
                                <td className="py-2 pr-3">{alumno.numeroControl}</td>
                                <td className="py-2 pr-3 font-medium">{alumno.nombreCompleto}</td>
                                <td className="py-2 pr-3">{alumno.carrera}</td>
                                <td className="py-2 pr-3">{alumno.semestre ?? '—'}</td>
                                <td className="py-2 pr-3">{alumno.sexo === 'H' ? 'Hombre' : alumno.sexo === 'M' ? 'Mujer' : '—'}</td>
                                <td className="py-2 pr-3">{alumno.estatusAlumno}</td>
                                <td className="py-2 pr-3">{alumno.calificacion ?? '—'}</td>
                                <td className="py-2 pr-3">{alumno.resultado}</td>
                                {grupo.curso.includes(' + ') && <>
                                  <td className="py-2 pr-3">{alumno.calificacionSecundaria ?? '—'}</td>
                                  <td className="py-2">{alumno.resultadoSecundario ?? '—'}</td>
                                </>}
                              </tr>
                            ))}</tbody>
                          </table>
                        </div>
                      )}
                    </div>
                  </details>
                ))}
              </div>
            )}
          </Tarjeta>

          <Tarjeta titulo={`Estudiantes con lengua materna · ${reporte.periodo.nombre}`} icono={FileSpreadsheet}
            extra={`${(reporte.alumnosLenguaMaterna || []).length} registros`}>
            {!reporte.alumnosLenguaMaterna?.length ? (
              <p className="text-sm text-tenue">No hay registros de lengua materna dentro de las fechas de este periodo.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[850px] text-left text-sm">
                  <thead><tr className="border-b border-linea text-xs text-tenue">
                    <th className="py-2 pr-3">Núm. control</th><th className="py-2 pr-3">Alumno</th>
                    <th className="py-2 pr-3">Carrera</th><th className="py-2 pr-3">Sem.</th>
                    <th className="py-2 pr-3">Sexo</th><th className="py-2 pr-3">Lengua</th>
                    <th className="py-2 pr-3">Nivel de dominio</th><th className="py-2 pr-3">Registro</th>
                    <th className="py-2">Estatus</th>
                  </tr></thead>
                  <tbody>{reporte.alumnosLenguaMaterna.map((alumno, indice) => (
                    <tr key={`${alumno.numeroControl}-${alumno.lengua}-${indice}`} className="border-b border-linea last:border-0">
                      <td className="py-2 pr-3">{alumno.numeroControl}</td>
                      <td className="py-2 pr-3 font-medium">{alumno.nombreCompleto}</td>
                      <td className="py-2 pr-3">{alumno.carrera}</td>
                      <td className="py-2 pr-3">{alumno.semestre ?? '—'}</td>
                      <td className="py-2 pr-3">{alumno.sexo === 'H' ? 'Hombre' : alumno.sexo === 'M' ? 'Mujer' : '—'}</td>
                      <td className="py-2 pr-3">{alumno.lengua}</td>
                      <td className="py-2 pr-3">{alumno.nivelDominio || '—'}</td>
                      <td className="py-2 pr-3">{alumno.fechaRegistro}</td>
                      <td className="py-2">{alumno.estatusRegistro}</td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            )}
          </Tarjeta>

          <Tarjeta titulo={`Detalle por nivel · ${reporte.periodo.nombre}`} icono={BarChart3}
            extra={`${reporte.calificacionesPendientes} calificaciones pendientes`}>
            {reporte.porNivel.length === 0 ? (
              <p className="text-sm text-tenue">No hay grupos ni inscripciones en este periodo.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[760px] text-left">
                  <thead><tr className="border-b border-linea text-xs text-tenue">
                    <th className="py-2 pr-3">Nivel</th><th className="py-2 pr-3">Grupos</th>
                    <th className="py-2 pr-3">Inscripciones</th><th className="py-2 pr-3">Promedio publicado</th>
                    <th className="py-2 pr-3">Acreditados</th><th className="py-2 pr-3">No acreditados</th>
                    <th className="py-2">Pendientes</th>
                  </tr></thead>
                  <tbody>{reporte.porNivel.map((nivel) => (
                    <tr className="border-b border-linea last:border-0" key={nivel.numeroNivel}>
                      <td className="py-3 pr-3 font-medium">{nivel.numeroNivel}. {nivel.nivel}</td>
                      <td className="py-3 pr-3">{nivel.grupos}</td>
                      <td className="py-3 pr-3">{nivel.alumnosInscritos}</td>
                      <td className="py-3 pr-3">
                        {nivel.promedioPublicado == null ? '—' : numero(nivel.promedioPublicado)}
                      </td>
                      <td className="py-3 pr-3">{nivel.acreditados}</td>
                      <td className="py-3 pr-3">{nivel.noAcreditados}</td>
                      <td className="py-3">{nivel.calificacionesPendientes}</td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            )}
          </Tarjeta>
          {reporte.porCarreraYNivel?.length > 0 && (
            <Tarjeta titulo="Aprobación por carrera y nivel" icono={BarChart3}>
              <div className="overflow-x-auto">
                <table className="w-full min-w-[760px] text-left">
                  <thead><tr className="border-b border-linea text-xs text-tenue">
                    <th className="py-2 pr-3">Carrera</th><th className="py-2 pr-3">Nivel</th>
                    <th className="py-2 pr-3">Inscripciones</th><th className="py-2 pr-3">Promedio publicado</th>
                    <th className="py-2 pr-3">Acreditados</th><th className="py-2 pr-3">No acreditados</th>
                    <th className="py-2">Pendientes</th>
                  </tr></thead>
                  <tbody>{reporte.porCarreraYNivel.map((fila) => (
                    <tr className="border-b border-linea last:border-0" key={`${fila.carrera}-${fila.numeroNivel}`}>
                      <td className="py-3 pr-3 font-medium">{fila.carrera}</td>
                      <td className="py-3 pr-3">{fila.numeroNivel}. {fila.nivel}</td>
                      <td className="py-3 pr-3">{fila.alumnosInscritos}</td>
                      <td className="py-3 pr-3">
                        {fila.promedioPublicado == null ? '—' : numero(fila.promedioPublicado)}
                      </td>
                      <td className="py-3 pr-3">{fila.acreditados}</td>
                      <td className="py-3 pr-3">{fila.noAcreditados}</td>
                      <td className="py-3">{fila.calificacionesPendientes}</td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            </Tarjeta>
          )}
          <p className="text-xs text-tenue">
            Las calificaciones y promedios se calculan únicamente con actas cerradas. La calificación mínima aprobatoria es 70.
          </p>
        </>
      )}
    </div>
  );
}
