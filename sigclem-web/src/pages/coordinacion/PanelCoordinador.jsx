import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Link } from 'react-router-dom';
import { BarChart3, Bell, CalendarDays, ClipboardCheck, FileCheck2, Mail, Search } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { Alerta, Campo, Cargando, Tarjeta, claseEntrada } from '../../components/ui';
import { listarComprobantesPendientes } from '../../api/pagos';
import { listarRecibidos, marcarLeido } from '../../api/mensajes';
import { obtenerPeriodos } from '../../api/catalogos';
import { listarGrupos } from '../../api/grupos';
import { mensajeError } from '../../api/cliente';
import { obtenerReporteAcademico } from '../../api/reportes';
import { listarExamenes } from '../../api/examenes';
import { listarDiagnosticos } from '../../api/diagnosticos';
import { listarConstancias } from '../../api/constancias';
import { BarrasHorizontales, Indicador } from '../../components/Graficas';

const numero = (valor) => new Intl.NumberFormat('es-MX', { maximumFractionDigits: 1 }).format(valor);

function hoyLocal() {
  const hoy = new Date();
  return `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
}

function fechaCorta(fecha) {
  if (!fecha) return '';
  return new Date(`${String(fecha).slice(0, 10)}T00:00:00`).toLocaleDateString('es-MX');
}

function fechaHoraCorta(fecha) {
  if (!fecha) return '';
  return new Date(fecha).toLocaleString('es-MX', {
    day: 'numeric',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function PanelCoordinador() {
  const { perfil } = useAuth();
  const [periodos, setPeriodos] = useState([]);
  const [periodoId, setPeriodoId] = useState('');
  const [datos, setDatos] = useState(null);
  const [mensajes, setMensajes] = useState([]);
  const [cargandoMensajes, setCargandoMensajes] = useState(true);
  const [errorMensajes, setErrorMensajes] = useState('');
  const [cargandoPeriodos, setCargandoPeriodos] = useState(true);
  const [cargandoDatos, setCargandoDatos] = useState(false);
  const [error, setError] = useState('');
  const [tareas, setTareas] = useState([]);

useEffect(() => {
  Promise.all([
    listarExamenes({ pendientes: true }),
    listarComprobantesPendientes(),
    listarDiagnosticos(),
    listarConstancias('Solicitada'),
  ]).then(([examenes, comprobantes, diagnosticos, constancias]) => setTareas([
    ...examenes.map((x) => ({
      clave: `ex-${x.id}`, tipo: 'Solicitud de examen', ruta: '/coordinacion/diagnosticos',
      titulo: `${x.nombreAlumno} · Examen de ${x.tipo}`, detalle: `Control ${x.numeroControl} · sin resultado`,
    })),
    ...comprobantes.map((c) => ({
      clave: `cp-${c.id}`, tipo: 'Pago por validar', ruta: '/coordinacion/comprobantes',
      titulo: `${c.alumno} · ${c.concepto}`, detalle: `Control ${c.numeroControl}`,
    })),
    ...diagnosticos.filter((d) => d.estatus === 'Solicitada').map((d) => ({
      clave: `dg-${d.id}`, tipo: 'Diagnóstico por programar', ruta: '/coordinacion/evaluaciones-diagnosticas',
      titulo: d.nombreAlumno, detalle: `Control ${d.numeroControl}`,
    })),
    ...constancias.map((k) => ({
      clave: `co-${k.id}`, tipo: 'Constancia solicitada', ruta: '/coordinacion/constancias',
      titulo: k.nombreAlumno, detalle: `Control ${k.numeroControl}`,
    })),
  ])).catch(() => {});
}, []);

const avisos = [
  ...mensajes.filter((m) => !m.leido && m.idRemitente).map((m) => ({
    clave: `m-${m.id}`, tipo: `Mensaje · ${m.rolRemitente}`, ruta: '/coordinacion/mensajes', mensaje: m,
    titulo: m.asunto, detalle: `${m.remitente} · ${fechaHoraCorta(m.fechaEnvio)}`,
  })),
  ...tareas,
];

  useEffect(() => {
    let activo = true;
    listarRecibidos()
      .then((lista) => {
        if (activo) setMensajes(lista);
      })
      .catch((e) => {
        if (activo) setErrorMensajes(mensajeError(e));
      })
      .finally(() => {
        if (activo) setCargandoMensajes(false);
      });
    return () => { activo = false; };
  }, []);

  useEffect(() => {
    obtenerPeriodos()
      .then((lista) => {
        setPeriodos(lista);
        const hoy = hoyLocal();
        const periodoActual = lista.find((periodo) => periodo.fechaInicio <= hoy && periodo.fechaFin >= hoy);
        if (lista.length) setPeriodoId(String((periodoActual || lista[0]).id));
      })
      .catch((e) => setError(mensajeError(e)))
      .finally(() => setCargandoPeriodos(false));
  }, []);

  useEffect(() => {
    if (!periodoId) {
      setDatos(null);
      return undefined;
    }

    let activo = true;
    setCargandoDatos(true);
    setError('');
    Promise.all([
      obtenerReporteAcademico(periodoId),
      listarGrupos(periodoId),
      listarComprobantesPendientes(),
    ])
      .then(([reporte, grupos, comprobantes]) => {
        if (!activo) return;
        setDatos({
          reporte,
          grupos,
          comprobantes: comprobantes.filter((comprobante) => String(comprobante.idPeriodo) === periodoId),
        });
      })
      .catch((e) => {
        if (activo) {
          setDatos(null);
          setError(mensajeError(e));
        }
      })
      .finally(() => {
        if (activo) setCargandoDatos(false);
      });
    return () => { activo = false; };
  }, [periodoId]);

  const periodo = periodos.find((item) => String(item.id) === periodoId);
  const notificaciones = datos ? [
    ...datos.grupos.filter((grupo) => grupo.estatusActa !== 'Cerrada').map((grupo) => ({
      clave: `acta-${grupo.id}`,
      tipo: 'Acta abierta',
      titulo: `${grupo.clave} · ${grupo.nivel}`,
      detalle: `${grupo.alumnosInscritos} inscritos · facilitador: ${grupo.facilitador}`,
      ruta: '/coordinacion/grupos',
    })),
    ...datos.comprobantes.map((comprobante) => ({
      clave: `comprobante-${comprobante.id}`,
      tipo: 'Comprobante por validar',
      titulo: `${comprobante.alumno} · ${comprobante.concepto}${comprobante.nivel ? ` ${comprobante.nivel}` : ''}`,
      detalle: `Control ${comprobante.numeroControl} · recibido ${fechaCorta(comprobante.fechaCarga)}`,
      ruta: '/coordinacion/comprobantes',
    })),
  ] : [];
  const reporte = datos?.reporte;
  const resultadosPublicados = reporte ? reporte.acreditados + reporte.noAcreditados : 0;
  const porcentajeAcreditacion = resultadosPublicados
    ? Math.round((reporte.acreditados / resultadosPublicados) * 100)
    : null;
  const mensajesSinLeer = mensajes.filter((mensaje) => !mensaje.leido).length;

  const navigate = useNavigate();

  return (
    <div className="grid gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="flex items-center gap-2 text-xl font-bold">
            <CalendarDays className="h-5 w-5" /> Panel de coordinación
          </h1>
          <p className="mt-1 text-sm text-tenue">Consulta académica y notificaciones del periodo seleccionado.</p>
        </div>
        {cargandoPeriodos ? <Cargando texto="Cargando periodos…" /> : periodos.length > 0 && (
          <Campo etiqueta="Periodo académico" id="periodoCoordinacion">
            <select id="periodoCoordinacion" className={claseEntrada} value={periodoId}
              onChange={(evento) => setPeriodoId(evento.target.value)}>
              {periodos.map((item) => (
                <option key={item.id} value={item.id}>{item.nombre}</option>
              ))}
            </select>
          </Campo>
        )}
      </div>

      <Alerta tipo="info">Bienvenido, {perfil.nombre}. La información de este panel corresponde únicamente al periodo seleccionado.</Alerta>
      <Link to="/coordinacion/alumnos"
        className="flex max-w-[620px] items-center gap-2 rounded-lg border border-[#C9D1DD] bg-white px-3 py-2 text-tenue hover:border-marino/40">
        <Search className="h-4 w-4" /> Buscar alumno por nombre, número de control, carrera o correo…
      </Link>

      {error && <Alerta tipo="error">{error}</Alerta>}
      {!cargandoPeriodos && periodos.length === 0 && !error && (
        <Alerta tipo="info">No hay periodos académicos registrados para consultar.</Alerta>
      )}
      {periodo && (
        <p className="text-sm font-medium text-tenue">
          Periodo: {periodo.nombre} · {fechaCorta(periodo.fechaInicio)} al {fechaCorta(periodo.fechaFin)}
        </p>
      )}
      {cargandoDatos && <Cargando texto="Consultando información del periodo…" />}

<Tarjeta titulo="Notificaciones" icono={Bell} extra={`${avisos.length} pendientes`}>
  {errorMensajes ? <Alerta tipo="error">{errorMensajes}</Alerta> : cargandoMensajes ? (
    <Cargando texto="Cargando notificaciones…" />
  ) : avisos.length === 0 ? (
    <p className="text-sm text-tenue">No tienes notificaciones pendientes.</p>
  ) : (
    <ul className="max-h-[216px] divide-y divide-linea overflow-y-auto rounded-lg border border-linea">
      {avisos.map((a) => (
        <li key={a.clave} className="h-[72px]">
          <Link to={a.ruta} className="flex h-full flex-col justify-center px-3 hover:bg-fondo"
            onClick={async (e) => {
              if (!a.mensaje) return;            // los avisos del sistema se quitan al cumplir la tarea
              e.preventDefault();
              try { await marcarLeido(a.mensaje.id); } catch { /* se reintenta al abrir la bandeja */ }
              setMensajes((prev) => prev.map((m) => (m.id === a.mensaje.id ? { ...m, leido: true } : m)));
              window.dispatchEvent(new Event('sigclem:mensajes-leidos'));
              navigate(a.ruta);
            }}>
            <span className="text-[11px] font-semibold uppercase text-tenue">{a.tipo}</span>
            <span className="truncate text-sm font-semibold">{a.titulo}</span>
            <span className="truncate text-xs text-tenue">{a.detalle}</span>
          </Link>
        </li>
      ))}
    </ul>
  )}
</Tarjeta>

      {reporte && !cargandoDatos && (
        <>
          <section aria-labelledby="indicadores-periodo">
            <h2 id="indicadores-periodo" className="mb-3 flex items-center gap-2 text-base font-semibold">
              <BarChart3 className="h-4 w-4" /> Indicadores académicos · {reporte.periodo.nombre}
            </h2>
            <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-5">
              <Indicador etiqueta="Alumnado inscrito" valor={reporte.alumnosUnicos}
                detalle={`${reporte.alumnosInscritos} inscripciones en cursos`} icono={FileCheck2} tono="info" />
              <Indicador etiqueta="Grupos del periodo" valor={reporte.grupos}
                detalle={`${reporte.actasCerradas} actas cerradas · ${reporte.grupos - reporte.actasCerradas} abiertas`}
                icono={CalendarDays} />
              <Indicador etiqueta="Calificaciones pendientes" valor={reporte.calificacionesPendientes}
                detalle="Inscripciones sin calificación publicada o en acta abierta" icono={ClipboardCheck} tono="aviso" />
              <Indicador etiqueta="Promedio publicado" valor={reporte.promedioPublicado == null ? '—' : numero(reporte.promedioPublicado)}
                detalle="Promedio de calificaciones con acta cerrada" icono={BarChart3} tono="info" />
              <Indicador etiqueta="Acreditación de resultados publicados"
                valor={porcentajeAcreditacion == null ? '—' : `${porcentajeAcreditacion}%`}
                detalle={`${reporte.acreditados} acreditados · ${reporte.noAcreditados} no acreditados; no incluye pendientes`}
                icono={ClipboardCheck} tono="ok" />
            </div>
          </section>

          <BarrasHorizontales
            titulo={`Inscripciones por nivel · ${reporte.periodo.nombre}`}
            etiquetaColumna="Nivel"
            unidad="inscripciones"
            datos={reporte.porNivel.map((nivel) => ({
              etiqueta: `${nivel.numeroNivel}. ${nivel.nivel}`,
              valor: nivel.alumnosInscritos,
            }))}
          />

          <Tarjeta titulo="Pendientes del periodo" extra={`${notificaciones.length} elementos por revisar`}>
            {notificaciones.length === 0 ? (
              <p className="text-sm text-tenue">No hay actas abiertas ni comprobantes por validar en este periodo.</p>
            ) : (
              <div className="max-h-48 overflow-y-auto"><ul className="divide-y divide-linea">
                {notificaciones.map((notificacion) => (
                  <li key={notificacion.clave}>
                    <Link to={notificacion.ruta} className="flex flex-wrap items-start gap-x-3 gap-y-1 py-3 hover:text-marino">
                      <span className="w-44 shrink-0 text-xs font-semibold uppercase text-tenue">{notificacion.tipo}</span>
                      <span className="min-w-0 flex-1">
                        <span className="block text-sm font-medium">{notificacion.titulo}</span>
                        <span className="block text-xs text-tenue">{notificacion.detalle}</span>
                      </span>
                    </Link>
                  </li>
                ))}
              </ul></div>
            )}
          </Tarjeta>
        </>
      )}
    </div>
  );
}
