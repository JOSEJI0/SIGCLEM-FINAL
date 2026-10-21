import { useEffect, useMemo, useState } from 'react';
import Paginador, { usePaginacion } from '../../components/Paginador';
import { CreditCard, Receipt, Search } from 'lucide-react';
import { listarComprobantesPendientes, listarTodosLosCargos } from '../../api/pagos';
import { mensajeError } from '../../api/cliente';
import { Alerta, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';
import { Indicador } from '../../components/Graficas';

const pesos = (v) => Number(v || 0).toLocaleString('es-MX', { style: 'currency', currency: 'MXN' });
const fecha = (iso) => (iso ? new Date(iso.length === 10 ? `${iso}T00:00:00` : iso).toLocaleDateString('es-MX') : '—');

/** Consulta de pagos para la jefa del DGTV: cargos emitidos, saldos y comprobantes en revisión (solo lectura). */
export default function PagosSupervision() {
  const [cargos, setCargos] = useState(null);
  const [pendientes, setPendientes] = useState([]);
  const [estatus, setEstatus] = useState('');
  const [texto, setTexto] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    Promise.all([listarTodosLosCargos(), listarComprobantesPendientes()])
      .then(([c, p]) => { setCargos(c); setPendientes(p); })
      .catch((e) => setError(mensajeError(e)));
  }, []);

  const visibles = useMemo(() => (cargos || []).filter((c) => {
    const t = texto.trim().toLowerCase();
    return (!estatus || c.estatus === estatus)
      && (!t || `${c.alumno} ${c.numeroControl} ${c.concepto}`.toLowerCase().includes(t));
  }), [cargos, estatus, texto]);

  const paginacion = usePaginacion(visibles, 50);

  if (!cargos && !error) return <Cargando />;
  const porCobrar = (cargos || []).reduce((s, c) => s + Number(c.saldoPendiente), 0);
  const cobrado = (cargos || []).reduce((s, c) => s + Number(c.montoEsperado) - Number(c.saldoPendiente), 0);

  return (
    <div className="grid gap-[18px]">
      <Alerta tipo="info">Consulta de solo lectura: la emisión de cargos y la validación de comprobantes las realiza la coordinación.</Alerta>
      {error && <Alerta tipo="error">{error}</Alerta>}
      <div className="grid gap-4 sm:grid-cols-3">
        <Indicador etiqueta="Cobrado" valor={pesos(cobrado)} icono={CreditCard} tono="ok" />
        <Indicador etiqueta="Por cobrar" valor={pesos(porCobrar)} icono={CreditCard} tono="aviso" />
        <Indicador etiqueta="Comprobantes en revisión" valor={pendientes.length} icono={Receipt} />
      </div>

      <Tarjeta titulo="Cargos emitidos" icono={CreditCard} extra={`${visibles.length} de ${(cargos || []).length}`}>
        <div className="mb-3 grid gap-2 sm:grid-cols-[1fr_200px]">
          <div className="relative">
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-tenue" />
            <input className={`${claseEntrada} pl-9`} placeholder="Buscar alumno, número de control o concepto" value={texto}
              onChange={(e) => setTexto(e.target.value)} aria-label="Buscar cargo" />
          </div>
          <select className={claseEntrada} value={estatus} onChange={(e) => setEstatus(e.target.value)} aria-label="Estatus del cargo">
            <option value="">Todos los estatus</option>
            <option>Pendiente</option><option>Parcial</option><option>Liquidada</option>
          </select>
        </div>
        {visibles.length === 0 ? <p className="text-sm text-tenue">No hay cargos que coincidan.</p> : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[820px] text-left text-[13px]">
              <thead className="border-b border-linea text-xs text-tenue">
                <tr>
                  <th className="px-2 py-2">Alumno</th><th className="px-2 py-2">Concepto</th><th className="px-2 py-2">Periodo</th>
                  <th className="px-2 py-2">Emisión</th><th className="px-2 py-2 text-right">Monto</th><th className="px-2 py-2 text-right">Saldo</th>
                  <th className="px-2 py-2">Estatus</th><th className="px-2 py-2">Comprobantes</th>
                </tr>
              </thead>
              <tbody>
                {paginacion.visibles.map((c) => (
                  <tr key={c.id} className="border-b border-linea last:border-0">
                    <td className="px-2 py-2.5"><div className="font-semibold">{c.alumno}</div><div className="text-xs text-tenue">{c.numeroControl}</div></td>
                    <td className="px-2 py-2.5">{c.concepto}{c.nivel ? ` · ${c.nivel}` : ''}</td>
                    <td className="px-2 py-2.5">{c.periodo}</td>
                    <td className="px-2 py-2.5">{fecha(c.fechaEmision)}</td>
                    <td className="px-2 py-2.5 text-right">{pesos(c.montoEsperado)}</td>
                    <td className="px-2 py-2.5 text-right font-semibold">{pesos(c.saldoPendiente)}</td>
                    <td className="px-2 py-2.5"><Insignia estatus={c.estatus} /></td>
                    <td className="px-2 py-2.5">{c.comprobantes}{c.comprobantesPendientes ? ` (${c.comprobantesPendientes} en revisión)` : ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Paginador paginacion={paginacion} />
          </div>
        )}
      </Tarjeta>
    </div>
  );
}
