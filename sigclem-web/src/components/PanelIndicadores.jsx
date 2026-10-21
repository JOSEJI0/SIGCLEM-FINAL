import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Award, CalendarDays, CircleDollarSign, ClipboardCheck, ClipboardList, GraduationCap, Languages, Receipt, UserSquare, Users,
} from 'lucide-react';
import { obtenerIndicadores } from '../api/reportes';
import { mensajeError } from '../api/cliente';
import { Alerta, Cargando } from './ui';
import { BarrasHorizontales, Indicador } from './Graficas';

const pesos = (v) => Number(v || 0).toLocaleString('es-MX', { style: 'currency', currency: 'MXN', maximumFractionDigits: 0 });

/**
 * Indicadores generales con gráficas, compartidos por el panel del coordinador y el de la jefa del DGTV.
 * {@code rutas} indica, por indicador, la pantalla de detalle de cada rol (por ejemplo { pagos: '/coordinacion/comprobantes' }).
 */
export default function PanelIndicadores({ rutas = {} }) {
  const [datos, setDatos] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    obtenerIndicadores().then(setDatos).catch((e) => setError(mensajeError(e)));
  }, []);

  if (error) return <Alerta tipo="error">{error}</Alerta>;
  if (!datos) return <Cargando texto="Calculando indicadores…" />;

  const enlace = (clave, contenido) => (rutas[clave]
    ? <Link to={rutas[clave]} className="block rounded-[10px] hover:ring-2 hover:ring-marino/15">{contenido}</Link>
    : contenido);
  const resultados = datos.acreditadosPeriodo + datos.noAcreditadosPeriodo;
  const aprobacion = resultados ? Math.round((datos.acreditadosPeriodo / resultados) * 100) : null;

  return (
    <div className="grid gap-4">
      <h2 className="text-base font-semibold">Indicadores generales{datos.periodo ? ` · ${datos.periodo}` : ''}</h2>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {enlace('alumnos', <Indicador etiqueta="Alumnos registrados" valor={datos.alumnos} icono={Users} tono="info" />)}
        {enlace('facilitadores', <Indicador etiqueta="Facilitadores activos" valor={datos.facilitadoresActivos} icono={UserSquare} tono="ok" />)}
        {enlace('grupos', <Indicador etiqueta="Grupos del periodo" valor={datos.gruposPeriodo} detalle={`${datos.inscritosPeriodo} alumnos inscritos`} icono={CalendarDays} />)}
        {enlace('reportes', <Indicador etiqueta="Aprobación del periodo" valor={aprobacion == null ? '—' : `${aprobacion}%`}
          detalle={`${datos.acreditadosPeriodo} acreditados · ${datos.noAcreditadosPeriodo} no acreditados`} icono={GraduationCap} tono="ok" />)}
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <BarrasHorizontales titulo="Alumnos por nivel actual" etiquetaColumna="Nivel" datos={datos.alumnosPorNivel.map((c) => ({ etiqueta: c.etiqueta, valor: c.valor }))} />
        <BarrasHorizontales titulo="Alumnos por carrera" etiquetaColumna="Carrera" datos={datos.alumnosPorCarrera.map((c) => ({ etiqueta: c.etiqueta, valor: c.valor }))} />
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {enlace('pagos', <Indicador etiqueta="Por cobrar" valor={pesos(datos.montoPorCobrar)}
          detalle={`${datos.cargosPendientes} pendientes · ${datos.cargosParciales} parciales`} icono={CircleDollarSign} tono="aviso" />)}
        {enlace('pagos', <Indicador etiqueta="Cobrado" valor={pesos(datos.montoCobrado)} detalle={`${datos.cargosLiquidados} cargos liquidados`} icono={Receipt} tono="ok" />)}
        {enlace('constancias', <Indicador etiqueta="Constancias emitidas" valor={datos.constanciasEmitidas}
          detalle={`${datos.constanciasSolicitadas} solicitudes por atender`} icono={Award} tono="info" />)}
        {enlace('lenguas-maternas', <Indicador etiqueta="Lenguas maternas por revisar" valor={datos.lenguasPorRevisar} icono={Languages} />)}
      </div>
      <div className="grid gap-4 sm:grid-cols-3">
        {enlace('pagos', <Indicador etiqueta="Comprobantes por validar" valor={datos.comprobantesPorValidar} icono={Receipt} tono="aviso" />)}
        {enlace('examenes', <Indicador etiqueta="Exámenes sin resultado" valor={datos.examenesPendientes} icono={ClipboardList} />)}
        {enlace('diagnosticos', <Indicador etiqueta="Diagnósticos por programar" valor={datos.diagnosticosPorProgramar} icono={ClipboardCheck} />)}
      </div>
    </div>
  );
}
