import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Award, CalendarDays, ClipboardCheck, Receipt, Search } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { Alerta, Cargando, Tarjeta } from '../../components/ui';
import { listarComprobantesPendientes } from '../../api/pagos';
import { listarConstancias } from '../../api/constancias';
import { listarDiagnosticos } from '../../api/diagnosticos';
import { listarGrupos } from '../../api/grupos';
import PanelIndicadores from '../../components/PanelIndicadores';

const RUTAS_COORDINACION = {
  alumnos: '/coordinacion/alumnos', facilitadores: '/coordinacion/facilitadores', grupos: '/coordinacion/grupos',
  reportes: '/coordinacion/reportes', pagos: '/coordinacion/comprobantes', constancias: '/coordinacion/constancias',
  'lenguas-maternas': '/coordinacion/lenguas-maternas', examenes: '/coordinacion/diagnosticos',
  diagnosticos: '/coordinacion/evaluaciones-diagnosticas',
};

const ACCESOS = [
  { clave: 'comprobantes', a: '/coordinacion/comprobantes', texto: 'Comprobantes por validar', icono: Receipt, tono: 'bg-aviso-fondo text-aviso' },
  { clave: 'constancias', a: '/coordinacion/constancias', texto: 'Solicitudes de constancia', icono: Award, tono: 'bg-info-fondo text-info' },
  { clave: 'diagnosticos', a: '/coordinacion/evaluaciones-diagnosticas', texto: 'Diagnósticos por programar', icono: ClipboardCheck, tono: 'bg-[#EFE9F8] text-[#6B46C1]' },
  { clave: 'grupos', a: '/coordinacion/grupos', texto: 'Grupos del periodo', icono: CalendarDays, tono: 'bg-ok-fondo text-ok' },
];

/** Resultado de una consulta del panel; si un módulo falla, el resto del panel se sigue mostrando. */
const seguro = (promesa) => promesa.catch(() => null);

/** Panel principal del coordinador (Figura 40): contadores y pendientes ordenados por antigüedad. */
export default function PanelCoordinador() {
  const { perfil } = useAuth();
  const [datos, setDatos] = useState(null);

  useEffect(() => {
    let activo = true;
    Promise.all([
      seguro(listarComprobantesPendientes()),
      seguro(listarConstancias('Solicitada')),
      seguro(listarDiagnosticos()),
      seguro(listarGrupos()),
    ]).then(([comprobantes, constancias, diagnosticos, grupos]) => {
      if (!activo) return;
      setDatos({
        comprobantes,
        constancias,
        diagnosticos: diagnosticos && diagnosticos.filter((d) => d.estatus === 'Solicitada'),
        grupos,
      });
    });
    return () => { activo = false; };
  }, []);

  const pendientes = datos ? [
    ...(datos.comprobantes ?? []).map((c) => ({
      clave: `c-${c.id}`, fecha: c.fechaCarga, tipo: 'Comprobante', a: '/coordinacion/comprobantes',
      texto: `${c.alumno} (${c.numeroControl}) · ${c.concepto}${c.nivel ? ` ${c.nivel}` : ''}`,
    })),
    ...(datos.constancias ?? []).map((c) => ({
      clave: `k-${c.id}`, fecha: c.fechaSolicitud, tipo: 'Constancia', a: '/coordinacion/constancias',
      texto: `${c.nombreAlumno} (${c.numeroControl}) · constancia de ${c.idioma}`,
    })),
    ...(datos.diagnosticos ?? []).map((d) => ({
      clave: `d-${d.id}`, fecha: d.fechaSolicitud, tipo: 'Diagnóstico', a: '/coordinacion/evaluaciones-diagnosticas',
      texto: `${d.nombreAlumno} (${d.numeroControl}) · evaluación diagnóstica por programar`,
    })),
  ].sort((x, y) => String(x.fecha).localeCompare(String(y.fecha))) : [];

  return (
    <>
      <Alerta tipo="info">Bienvenido, {perfil.nombre}. Estos son los trámites que requieren tu atención.</Alerta>
      <Link to="/coordinacion/alumnos"
        className="flex max-w-[620px] items-center gap-2 rounded-lg border border-[#C9D1DD] bg-white px-3 py-2 text-tenue hover:border-marino/40">
        <Search className="h-4 w-4" /> Buscar alumno por nombre, número de control, carrera o correo…
      </Link>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {ACCESOS.map((x) => {
          const lista = datos?.[x.clave];
          return (
            <Link key={x.a} to={x.a} className="flex items-center gap-3.5 rounded-[10px] border border-linea bg-white px-[18px] py-4 hover:border-marino/40">
              <span className={`flex h-[42px] w-[42px] items-center justify-center rounded-[10px] ${x.tono}`}><x.icono className="h-5 w-5" /></span>
              <span>
                <span className="block text-2xl font-bold leading-none">{lista ? lista.length : '—'}</span>
                <span className="mt-1 block text-xs text-tenue">{x.texto}</span>
              </span>
            </Link>
          );
        })}
      </div>
      <Tarjeta titulo="Pendientes" extra={datos ? `${pendientes.length} por atender` : undefined}>
        {!datos ? <Cargando /> : pendientes.length === 0 ? (
          <p className="text-tenue">No hay comprobantes, constancias ni diagnósticos pendientes.</p>
        ) : (
          <ul className="divide-y divide-linea">
            {pendientes.map((p) => (
              <li key={p.clave}>
                <Link to={p.a} className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2.5 hover:text-marino">
                  <span className="w-24 text-xs font-semibold uppercase text-tenue">{p.tipo}</span>
                  <span className="flex-1 text-sm">{p.texto}</span>
                  <span className="text-xs text-tenue">{String(p.fecha ?? '').slice(0, 10)}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Tarjeta>
      <PanelIndicadores rutas={RUTAS_COORDINACION} />
    </>
  );
}
