import { useCallback, useEffect, useState } from 'react';
import { ShieldCheck, Search } from 'lucide-react';
import { consultarAuditoria, listarAccionesAuditoria } from '../../api/auditoria';
import { mensajeError } from '../../api/cliente';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';

const FILTROS_VACIOS = { usuario: '', accion: '' };

function fechaHora(iso) {
  return iso ? new Date(iso).toLocaleString('es-MX', { dateStyle: 'short', timeStyle: 'medium' }) : '—';
}

/** CU-30 Auditoría: el administrador consulta la bitácora de acciones sensibles del sistema. */
export default function Auditoria() {
  const [busqueda, setBusqueda] = useState(FILTROS_VACIOS);
  const [filtro, setFiltro] = useState(FILTROS_VACIOS);
  const [pagina, setPagina] = useState(null);
  const [acciones, setAcciones] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  const cargar = useCallback(async (criterio, numeroPagina) => {
    setCargando(true);
    setError('');
    try {
      setPagina(await consultarAuditoria({ ...criterio, pagina: numeroPagina }));
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    listarAccionesAuditoria().then(setAcciones).catch(() => setAcciones([]));
    cargar(FILTROS_VACIOS, 0);
  }, [cargar]);

  const buscar = (e) => {
    e.preventDefault();
    const criterio = { ...busqueda, usuario: busqueda.usuario.trim() };
    setFiltro(criterio);
    cargar(criterio, 0);
  };
  const cambiar = (campo) => (e) => setBusqueda({ ...busqueda, [campo]: e.target.value });
  const hayFiltros = busqueda.usuario !== '' || busqueda.accion !== '';

  return (
    <div className="grid gap-[18px]">
      <Tarjeta titulo="Auditoría del sistema" icono={ShieldCheck}>
        <p className="mb-3 text-[13px] text-tenue">
          Registro de las acciones sensibles: inicios de sesión (incluidos los fallidos y los bloqueos), cambios de
          contraseña, cierres de acta por coordinación y asignaciones de nivel. La bitácora es solo de lectura: no se
          puede modificar ni borrar desde el sistema, para que sirva como evidencia.
        </p>
        <form className="grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end" onSubmit={buscar}>
          <Campo etiqueta="Usuario" id="filtroUsuario">
            <input id="filtroUsuario" className={claseEntrada} maxLength="50" placeholder="Usuario o parte de él"
              value={busqueda.usuario} onChange={cambiar('usuario')} />
          </Campo>
          <Campo etiqueta="Acción" id="filtroAccion">
            <select id="filtroAccion" className={claseEntrada} value={busqueda.accion} onChange={cambiar('accion')}>
              <option value="">Todas</option>
              {acciones.map((a) => <option key={a.codigo} value={a.codigo}>{a.descripcion}</option>)}
            </select>
          </Campo>
          <div className="flex gap-2">
            <Boton type="submit"><Search className="h-4 w-4" /> Buscar</Boton>
            {hayFiltros && (
              <Boton variante="secundario" type="button" onClick={() => {
                setBusqueda(FILTROS_VACIOS);
                setFiltro(FILTROS_VACIOS);
                cargar(FILTROS_VACIOS, 0);
              }}>Limpiar</Boton>
            )}
          </div>
        </form>
      </Tarjeta>

      {error && <Alerta tipo="error">{error}</Alerta>}

      <Tarjeta titulo="Movimientos registrados" icono={ShieldCheck}
        extra={pagina ? `${pagina.totalElements} en total` : ''}>
        {cargando ? <Cargando /> : !pagina?.content.length ? (
          <p className="text-sm text-tenue">No hay movimientos con esos criterios.</p>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[720px] text-left text-sm">
                <thead className="border-b border-linea text-xs text-tenue">
                  <tr>
                    <th className="px-2 py-2">Fecha y hora</th>
                    <th className="px-2 py-2">Usuario</th>
                    <th className="px-2 py-2">Acción</th>
                    <th className="px-2 py-2">Resultado</th>
                    <th className="px-2 py-2">Detalle</th>
                    <th className="px-2 py-2">IP</th>
                  </tr>
                </thead>
                <tbody>
                  {pagina.content.map((r) => (
                    <tr className="border-b border-linea last:border-0 align-top" key={r.id}>
                      <td className="whitespace-nowrap px-2 py-3">{fechaHora(r.fecha)}</td>
                      <td className="px-2 py-3">
                        {r.usuario || '—'}
                        {r.rol && <div className="text-[11px] text-tenue">{r.rol}</div>}
                      </td>
                      <td className="px-2 py-3">{r.accionDescripcion}</td>
                      <td className="px-2 py-3">
                        <Insignia estatus={r.resultado === 'OK' ? 'Validado' : 'Rechazado'}>{r.resultado}</Insignia>
                      </td>
                      <td className="px-2 py-3 text-tenue">{r.detalle || '—'}</td>
                      <td className="whitespace-nowrap px-2 py-3 text-tenue">{r.ip || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="mt-4 flex items-center justify-between gap-3">
              <span className="text-xs text-tenue">
                Página {pagina.number + 1} de {pagina.totalPages}
              </span>
              <div className="flex gap-2">
                <Boton variante="secundario" type="button" disabled={pagina.first}
                  onClick={() => cargar(filtro, pagina.number - 1)}>Anterior</Boton>
                <Boton variante="secundario" type="button" disabled={pagina.last}
                  onClick={() => cargar(filtro, pagina.number + 1)}>Siguiente</Boton>
              </div>
            </div>
          </>
        )}
      </Tarjeta>
    </div>
  );
}
