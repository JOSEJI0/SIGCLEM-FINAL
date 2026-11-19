import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  CreditCard, Eye, Pencil, Plus, Power, Receipt, Search, Tags, Trash2,
} from 'lucide-react';
import { buscarAlumnos } from '../../api/alumnos';
import { mensajeError } from '../../api/cliente';
import { obtenerPeriodos } from '../../api/catalogos';
import { listarGrupos } from '../../api/grupos';
import {
  activarConcepto, crearAutorizacion, crearConcepto, desactivarConcepto, editarConcepto, eliminarConcepto,
  listarCargos, listarComprobantes, listarComprobantesPendientes, listarConceptos, listarTodosLosCargos,
} from '../../api/pagos';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';
import { ConfirmacionDoble, Modal } from '../../components/Modal';
import VerificacionComprobante, { tieneAlertaVerificacion } from '../../components/VerificacionComprobante';
import RevisionComprobante from '../../components/RevisionComprobante';
import Paginador, { usePaginacion } from '../../components/Paginador';
import { useAuth } from '../../auth/AuthContext';
import { esSoloConsulta } from '../../auth/roles';
import { Indicador } from '../../components/Graficas';

function moneda(valor) {
  return new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(valor || 0);
}

const fecha = (iso) => (iso ? new Date(iso.length === 10 ? `${iso}T00:00:00` : iso).toLocaleDateString('es-MX') : '—');

export default function Pagos({ soloLectura = false }) {
  const { perfil } = useAuth();
  const esSoloLectura = soloLectura || esSoloConsulta(perfil?.rol);

  const [edicionConcepto, setEdicionConcepto] = useState(null);
  const [borrarConcepto, setBorrarConcepto] = useState(null);
  const [conceptos, setConceptos] = useState([]);
  const [periodos, setPeriodos] = useState([]);
  const [comprobantes, setComprobantes] = useState([]);
  const [grupos, setGrupos] = useState([]);
  const [nombreConcepto, setNombreConcepto] = useState('');
  const [idTarifa, setIdTarifa] = useState('');
  const [monto, setMonto] = useState('');

  // Búsqueda individual de alumno
  const [busquedaAlumno, setBusquedaAlumno] = useState('');
  const [alumnos, setAlumnos] = useState([]);
  const [alumno, setAlumno] = useState(null);
  const [cargos, setCargos] = useState([]);
  const [idPeriodo, setIdPeriodo] = useState('');

  // Listado general de cargos emitidos
  const [cargosEmitidos, setCargosEmitidos] = useState(null);
  const [filtroEstatusCargo, setFiltroEstatusCargo] = useState('');
  const [filtroTextoCargo, setFiltroTextoCargo] = useState('');

  // Comprobante abierto en el panel de revisión (documento + verificación + validar/rechazar)
  const [revision, setRevision] = useState(null);
  // Estatus de la lista de comprobantes: los pendientes llegan con la carga inicial; validados y rechazados, al elegirlos
  const [estatusLista, setEstatusLista] = useState('Pendiente');
  const [comprobantesResueltos, setComprobantesResueltos] = useState([]);
  const [cargandoLista, setCargandoLista] = useState(false);
  const [guardando, setGuardando] = useState('');
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      const [conceptosCargados, periodosCargados, pendientes, cargosGlobales] = await Promise.all([
        listarConceptos(true),
        obtenerPeriodos(),
        listarComprobantesPendientes(),
        listarTodosLosCargos().catch(() => []),
      ]);
      const periodosPendientes = [...new Set(pendientes.map((c) => c.idPeriodo))];
      const gruposPorPeriodo = await Promise.all(periodosPendientes.map((periodoId) => listarGrupos(periodoId)));
      setConceptos(conceptosCargados);
      setPeriodos(periodosCargados);
      setComprobantes(pendientes);
      setGrupos(gruposPorPeriodo.flat());
      setCargosEmitidos(cargosGlobales);
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => { cargar(); }, [cargar]);

  const conceptosActivos = useMemo(() => conceptos.filter((item) => item.activo && item.idTarifa), [conceptos]);
  const [filtroPendientes, setFiltroPendientes] = useState('');
  const [soloAlertas, setSoloAlertas] = useState(false);
  useEffect(() => {
    if (estatusLista === 'Pendiente') return undefined;
    let cancelado = false;
    setCargandoLista(true);
    listarComprobantes(estatusLista)
      .then((lista) => { if (!cancelado) setComprobantesResueltos(lista); })
      .catch((e) => { if (!cancelado) setError(mensajeError(e)); })
      .finally(() => { if (!cancelado) setCargandoLista(false); });
    return () => { cancelado = true; };
  }, [estatusLista]);
  const listaComprobantes = estatusLista === 'Pendiente' ? comprobantes : comprobantesResueltos;
  const pendientesFiltrados = useMemo(() => {
    const texto = filtroPendientes.trim().toLowerCase();
    // Los resueltos se muestran del más reciente al más antiguo; los pendientes, en orden de llegada.
    const base = estatusLista === 'Pendiente' ? listaComprobantes : [...listaComprobantes].reverse();
    return base.filter((c) => (estatusLista !== 'Pendiente' || !soloAlertas || tieneAlertaVerificacion(c) || c.uuidCfdi == null)
      && (!texto || `${c.alumno} ${c.numeroControl} ${c.concepto}`.toLowerCase().includes(texto)));
  }, [listaComprobantes, estatusLista, filtroPendientes, soloAlertas]);
  // Al inicio del semestre puede haber cientos de comprobantes: se muestran de 10 en 10.
  const paginacionPendientes = usePaginacion(pendientesFiltrados, 10);

  // Cargos generales filtrados
  const cargosVisibles = useMemo(() => (cargosEmitidos || []).filter((c) => {
    const t = filtroTextoCargo.trim().toLowerCase();
    return (!filtroEstatusCargo || c.estatus === filtroEstatusCargo)
      && (!t || `${c.alumno} ${c.numeroControl} ${c.concepto}`.toLowerCase().includes(t));
  }), [cargosEmitidos, filtroEstatusCargo, filtroTextoCargo]);
  const paginacionCargos = usePaginacion(cargosVisibles, 20);

  const cobrado = (cargosEmitidos || []).reduce((s, c) => s + Number(c.montoEsperado) - Number(c.saldoPendiente), 0);
  const porCobrar = (cargosEmitidos || []).reduce((s, c) => s + Number(c.saldoPendiente), 0);

  async function buscar(evento) {
    evento.preventDefault();
    setError('');
    setAlumno(null);
    setCargos([]);
    try {
      const resultado = await buscarAlumnos({ busqueda: busquedaAlumno.trim(), pagina: 0, tamano: 10 });
      setAlumnos(resultado.content || []);
      setAviso(resultado.content?.length ? '' : 'No se encontraron alumnos con ese criterio.');
    } catch (e) {
      setError(mensajeError(e));
    }
  }

  async function seleccionarAlumno(seleccionado) {
    setAlumno(seleccionado);
    setAlumnos([]);
    setError('');
    setAviso('');
    try {
      setCargos(await listarCargos(seleccionado.numeroControl));
    } catch (e) {
      setError(mensajeError(e));
    }
  }

  async function emitirCargo(evento) {
    evento.preventDefault();
    if (!alumno || esSoloLectura) return;
    setGuardando('cargo');
    setError('');
    setAviso('');
    try {
      const cargo = await crearAutorizacion({
        numeroControl: alumno.numeroControl,
        idTarifa: Number(idTarifa),
        idPeriodo: Number(idPeriodo),
      });
      setAviso(`Cargo #${cargo.id} emitido o ya existente para ${alumno.nombreCompleto}.`);
      setCargos(await listarCargos(alumno.numeroControl));
      listarTodosLosCargos().then(setCargosEmitidos).catch(() => {});
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando('');
    }
  }

  async function guardarConcepto(evento) {
    evento.preventDefault();
    if (esSoloLectura) return;
    setGuardando('concepto');
    setError('');
    setAviso('');
    try {
      const creado = await crearConcepto({ nombre: nombreConcepto.trim(), monto: Number(monto) });
      setNombreConcepto('');
      setMonto('');
      setAviso(`Concepto «${creado.nombre}» creado con monto de ${moneda(creado.monto)}.`);
      await cargar();
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando('');
    }
  }

  async function activarConceptoExistente(id) {
    if (esSoloLectura) return;
    setGuardando(`concepto-${id}`);
    setError('');
    setAviso('');
    try {
      await activarConcepto(id);
      setAviso('Concepto activado: ya se puede usar para emitir cargos.');
      await cargar();
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando('');
    }
  }

  async function desactivarConceptoExistente(id) {
    if (esSoloLectura) return;
    setGuardando(`concepto-${id}`);
    setError('');
    setAviso('');
    try {
      await desactivarConcepto(id);
      setAviso('Concepto desactivado: ya no se podrá usar en nuevos cargos.');
      await cargar();
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando('');
    }
  }

  /** Recarga comprobantes, cargos y grupos sin ocultar la pantalla (el panel de revisión puede seguir abierto). */
  async function refrescar() {
    try {
      const [pendientes, cargosGlobales] = await Promise.all([
        listarComprobantesPendientes(),
        listarTodosLosCargos().catch(() => null),
      ]);
      setComprobantes(pendientes);
      if (cargosGlobales) setCargosEmitidos(cargosGlobales);
      const periodosPendientes = [...new Set(pendientes.map((c) => c.idPeriodo))];
      const gruposPorPeriodo = await Promise.all(periodosPendientes.map((periodoId) => listarGrupos(periodoId)));
      setGrupos(gruposPorPeriodo.flat());
      if (estatusLista !== 'Pendiente') setComprobantesResueltos(await listarComprobantes(estatusLista));
      if (alumno) setCargos(await listarCargos(alumno.numeroControl));
    } catch (e) {
      setError(mensajeError(e));
    }
  }

  function revisar(comprobante) {
    setAviso('');
    // Desde los cargos del alumno no llega el grupo asignado: se toma de la lista de pendientes, que sí lo trae.
    setRevision(comprobantes.find((c) => c.id === comprobante.id) || comprobante);
  }

  async function alCambiarRevision(mensaje) {
    if (mensaje) {
      setError('');
      setAviso(mensaje);
    }
    await refrescar();
  }

  if (cargando) return <Cargando />;

  return (
    <div className="grid gap-[18px]">
      <div>
        <h1 className="flex items-center gap-2 text-xl font-bold"><Receipt className="h-5 w-5" /> Pagos y comprobantes</h1>
        <p className="mt-1 text-sm text-tenue">
          {esSoloLectura
            ? 'Supervisión y consulta de cargos, saldos, conceptos y comprobantes de pago de los alumnos.'
            : 'Emite cargos, registra conceptos y revisa los abonos enviados por los alumnos.'}
        </p>
      </div>

      {esSoloLectura && (
        <Alerta tipo="info">
          <b>Modo de solo consulta.</b> Puedes consultar la información de pagos, revisar si los alumnos ya pagaron o tienen adeudo, y ver los comprobantes enviados. La emisión de cargos y validación corresponden a la coordinación.
        </Alerta>
      )}

      {error && <Alerta tipo="error">{error}</Alerta>}
      {aviso && <Alerta tipo="ok">{aviso}</Alerta>}

      {cargosEmitidos && (
        <div className="grid gap-4 sm:grid-cols-3">
          <Indicador etiqueta="Cobrado" valor={moneda(cobrado)} icono={CreditCard} tono="ok" />
          <Indicador etiqueta="Por cobrar" valor={moneda(porCobrar)} icono={CreditCard} tono="aviso" />
          <Indicador etiqueta="Comprobantes en revisión" valor={comprobantes.length} icono={Receipt} />
        </div>
      )}

      {/* Listado general de cargos emitidos */}
      {cargosEmitidos && (
        <Tarjeta titulo="Cargos emitidos" icono={CreditCard} extra={`${cargosVisibles.length} de ${cargosEmitidos.length}`}>
          <div className="mb-3 grid gap-2 sm:grid-cols-[1fr_200px]">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-tenue" />
              <input className={`${claseEntrada} pl-9`} placeholder="Buscar alumno, número de control o concepto" value={filtroTextoCargo}
                onChange={(e) => setFiltroTextoCargo(e.target.value)} aria-label="Buscar cargo" />
            </div>
            <select className={claseEntrada} value={filtroEstatusCargo} onChange={(e) => setFiltroEstatusCargo(e.target.value)} aria-label="Estatus del cargo">
              <option value="">Todos los estatus</option>
              <option value="Pendiente">Pendiente</option>
              <option value="Parcial">Parcial</option>
              <option value="Liquidada">Liquidada</option>
            </select>
          </div>
          {cargosVisibles.length === 0 ? <p className="text-sm text-tenue">No hay cargos que coincidan con la búsqueda.</p> : (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[820px] text-left text-[13px]">
                <thead className="border-b border-linea text-xs text-tenue">
                  <tr>
                    <th className="px-2 py-2">Alumno</th>
                    <th className="px-2 py-2">Concepto</th>
                    <th className="px-2 py-2">Periodo</th>
                    <th className="px-2 py-2">Emisión</th>
                    <th className="px-2 py-2 text-right">Monto</th>
                    <th className="px-2 py-2 text-right">Saldo</th>
                    <th className="px-2 py-2">Estatus</th>
                    <th className="px-2 py-2">Comprobantes</th>
                  </tr>
                </thead>
                <tbody>
                  {paginacionCargos.visibles.map((c) => (
                    <tr key={c.id} className="border-b border-linea last:border-0">
                      <td className="px-2 py-2.5">
                        <div className="font-semibold">{c.alumno}</div>
                        <div className="text-xs text-tenue">{c.numeroControl}</div>
                      </td>
                      <td className="px-2 py-2.5">{c.concepto}{c.nivel ? ` · ${c.nivel}` : ''}</td>
                      <td className="px-2 py-2.5">{c.periodo}</td>
                      <td className="px-2 py-2.5">{fecha(c.fechaEmision)}</td>
                      <td className="px-2 py-2.5 text-right">{moneda(c.montoEsperado)}</td>
                      <td className="px-2 py-2.5 text-right font-semibold">{moneda(c.saldoPendiente)}</td>
                      <td className="px-2 py-2.5"><Insignia estatus={c.estatus} /></td>
                      <td className="px-2 py-2.5">{c.comprobantes}{c.comprobantesPendientes ? ` (${c.comprobantesPendientes} en revisión)` : ''}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <Paginador paginacion={paginacionCargos} />
            </div>
          )}
        </Tarjeta>
      )}

      {/* Consulta o emisión de cargos por alumno individual */}
      <Tarjeta titulo={esSoloLectura ? 'Consultar cargos de un alumno' : 'Emitir cargo a un alumno'} icono={Search}>
        <form className="flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={buscar}>
          <Campo etiqueta="Nombre o número de control" id="buscarAlumnoPago">
            <input className={claseEntrada} id="buscarAlumnoPago" required maxLength="100" value={busquedaAlumno} onChange={(e) => setBusquedaAlumno(e.target.value)} />
          </Campo>
          <Boton type="submit"><Search className="h-4 w-4" /> Buscar alumno</Boton>
        </form>
        {alumnos.length > 0 && (
          <div className="mt-3 grid gap-2">
            {alumnos.map((item) => (
              <button key={item.numeroControl} type="button" className="rounded-lg border border-linea p-3 text-left text-sm hover:bg-fondo" onClick={() => seleccionarAlumno(item)}>
                <b>{item.nombreCompleto}</b> · {item.numeroControl} · {item.carrera}
              </button>
            ))}
          </div>
        )}
        {alumno && (
          <div className="mt-4 grid gap-3">
            <p className="text-sm"><b>Alumno seleccionado:</b> {alumno.nombreCompleto} · {alumno.numeroControl}</p>
            {!esSoloLectura && (
              <form className="grid gap-3 sm:grid-cols-3 sm:items-end" onSubmit={emitirCargo}>
                <Campo etiqueta="Concepto" id="tarifaCargo" ayuda="En cursos, el cargo se liga al nivel que le toca al alumno.">
                  <select className={claseEntrada} id="tarifaCargo" required value={idTarifa} onChange={(e) => setIdTarifa(e.target.value)}>
                    <option value="">Selecciona un concepto</option>
                    {conceptosActivos.map((item) => (
                      <option key={item.id} value={item.idTarifa}>{item.nombre} — {moneda(item.monto)}</option>
                    ))}
                  </select>
                </Campo>
                <Campo etiqueta="Periodo" id="periodoCargo">
                  <select className={claseEntrada} id="periodoCargo" required value={idPeriodo} onChange={(e) => setIdPeriodo(e.target.value)}>
                    <option value="">Selecciona un periodo</option>
                    {periodos.map((item) => <option key={item.id} value={item.id}>{item.nombre}</option>)}
                  </select>
                </Campo>
                <Boton type="submit" disabled={!idTarifa || !idPeriodo || guardando !== ''}>
                  {guardando === 'cargo' ? 'Emitiendo…' : 'Emitir cargo'}
                </Boton>
              </form>
            )}
            <div className="grid gap-2">
              <h3 className="text-sm font-semibold">Cargos existentes</h3>
              {cargos.length === 0 ? <p className="text-sm text-tenue">Este alumno no tiene cargos registrados.</p> : cargos.map((cargo) => (
                <div key={cargo.id} className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-linea p-3 text-sm">
                  <div>
                    <div className="font-semibold">{cargo.concepto}{cargo.nivel ? ` · ${cargo.nivel}` : ''} · {cargo.periodo}</div>
                    <div className="text-xs text-tenue">Monto esperado: {moneda(cargo.montoEsperado || cargo.monto)} · Emisión: {fecha(cargo.fechaEmision)}</div>
                  </div>
                  <div className="flex items-center gap-3">
                    <span>Saldo: <b>{moneda(cargo.saldoPendiente)}</b></span>
                    <Insignia estatus={cargo.estatusCargo || cargo.estatus}>{cargo.estatusCargo || cargo.estatus}</Insignia>
                  </div>
                  {(cargo.comprobantes || []).length > 0 && (
                    <ul className="grid w-full gap-1.5 border-t border-linea pt-2" aria-label={`Comprobantes del cargo ${cargo.id}`}>
                      {cargo.comprobantes.map((c) => (
                        <li key={c.id} className="flex flex-wrap items-center gap-x-3 gap-y-1">
                          <span className="text-xs text-tenue">{c.fechaCarga ? new Date(c.fechaCarga).toLocaleDateString('es-MX') : '—'}</span>
                          <span>{c.total == null ? 'Importe sin capturar' : moneda(c.total)}</span>
                          <Insignia estatus={c.estatus} />
                          <VerificacionComprobante comprobante={c} />
                          <Boton variante="secundario" type="button" className="ml-auto !px-2 !py-1 text-xs" onClick={() => revisar(c)}>
                            <Eye className="h-3.5 w-3.5" /> {c.estatus === 'Pendiente' && !esSoloLectura ? 'Revisar' : 'Ver'}
                          </Boton>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              ))}
            </div>
          </div>
        )}
      </Tarjeta>

      {/* Conceptos de pago */}
      <Tarjeta titulo="Conceptos de pago" icono={Tags}>
        <p className="mb-3 text-[13px] text-tenue">
          {esSoloLectura
            ? 'Catálogo de conceptos y tarifas autorizadas para el ciclo escolar.'
            : 'Cada concepto tiene un solo monto que aplica a todos los niveles. Si cambias un monto, los cargos ya emitidos conservan el anterior.'}
        </p>
        {!esSoloLectura && (
          <form className="grid gap-3 sm:grid-cols-[1fr_180px_auto] sm:items-end" onSubmit={guardarConcepto}>
            <Campo etiqueta="Nuevo concepto" id="nuevoConcepto">
              <input className={claseEntrada} id="nuevoConcepto" required maxLength="30" placeholder="Ej. Reposición de credencial"
                value={nombreConcepto} onChange={(e) => setNombreConcepto(e.target.value)} />
            </Campo>
            <Campo etiqueta="Monto (MXN)" id="montoConcepto">
              <input className={claseEntrada} id="montoConcepto" type="number" required min="0.01" step="0.01"
                value={monto} onChange={(e) => setMonto(e.target.value)} />
            </Campo>
            <Boton type="submit" disabled={guardando !== '' || !nombreConcepto.trim() || !monto}>
              <Plus className="h-4 w-4" /> {guardando === 'concepto' ? 'Guardando…' : 'Agregar concepto'}
            </Boton>
          </form>
        )}
        <div className="mt-4 overflow-x-auto">
          <table className="w-full min-w-[560px] text-left text-sm">
            <thead className="border-b border-linea text-xs text-tenue">
              <tr>
                <th className="px-2 py-2">Concepto</th>
                <th className="px-2 py-2">Monto</th>
                <th className="px-2 py-2">Estado</th>
                {!esSoloLectura && <th className="px-2 py-2 text-right">Acciones</th>}
              </tr>
            </thead>
            <tbody>
              {conceptos.map((item) => (
                <tr key={item.id} className="border-b border-linea last:border-0">
                  <td className="px-2 py-3 font-semibold">{item.nombre}</td>
                  <td className="px-2 py-3">{item.monto != null ? moneda(item.monto) : <span className="text-tenue">Sin monto</span>}</td>
                  <td className="px-2 py-3"><Insignia estatus={item.activo ? 'Activo' : 'Inactivo'} tono={item.activo ? 'ok' : 'neutro'} /></td>
                  {!esSoloLectura && (
                    <td className="px-2 py-3">
                      <div className="flex flex-wrap justify-end gap-2">
                        <Boton variante="secundario" type="button" disabled={guardando !== ''}
                          onClick={() => setEdicionConcepto({ id: item.id, nombre: item.nombre, monto: item.monto ?? '', sistema: item.nombre === 'Curso de nivel', error: '' })}>
                          <Pencil className="h-3.5 w-3.5" /> Editar
                        </Boton>
                        <Boton variante="secundario" type="button" disabled={guardando !== ''}
                          onClick={() => (item.activo ? desactivarConceptoExistente(item.id) : activarConceptoExistente(item.id))}>
                          <Power className="h-3.5 w-3.5" /> {item.activo ? 'Desactivar' : 'Activar'}
                        </Boton>
                        <Boton variante="peligro" type="button" disabled={guardando !== ''} onClick={() => setBorrarConcepto(item)}>
                          <Trash2 className="h-3.5 w-3.5" />
                        </Boton>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Tarjeta>

      {/* Comprobantes: pendientes de revisión, validados o rechazados */}
      <section className="grid gap-3" aria-label="Comprobantes de pago">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-base font-semibold">
            {estatusLista === 'Pendiente' ? `Comprobantes pendientes (${comprobantes.length})` : `Comprobantes ${estatusLista.toLowerCase()}s`}
          </h2>
          <select className={`${claseEntrada} max-w-[220px]`} aria-label="Estatus de los comprobantes" value={estatusLista}
            onChange={(e) => setEstatusLista(e.target.value)}>
            <option value="Pendiente">Pendientes de revisión</option>
            <option value="Validado">Validados</option>
            <option value="Rechazado">Rechazados</option>
          </select>
        </div>
        {listaComprobantes.length > 0 && (
          <div className="flex flex-wrap items-center gap-3">
            <input className={`${claseEntrada} max-w-sm`} aria-label="Buscar comprobante por alumno, número de control o concepto"
              placeholder="Buscar por alumno, número de control o concepto" value={filtroPendientes}
              onChange={(e) => setFiltroPendientes(e.target.value)} />
            {estatusLista === 'Pendiente' && (
              <label className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={soloAlertas} onChange={(e) => setSoloAlertas(e.target.checked)} />
                Solo con alertas o sin datos del CFDI
              </label>
            )}
          </div>
        )}
        {cargandoLista ? <Cargando />
          : listaComprobantes.length === 0 ? (
            <Tarjeta><p className="text-sm text-tenue">
              {estatusLista === 'Pendiente' ? 'No hay comprobantes pendientes de revisión.' : `No hay comprobantes ${estatusLista.toLowerCase()}s.`}
            </p></Tarjeta>
          ) : pendientesFiltrados.length === 0 ? <Tarjeta><p className="text-sm text-tenue">Ningún comprobante coincide con el filtro.</p></Tarjeta>
          : paginacionPendientes.visibles.map((comprobante) => {
            const requiereCaptura = comprobante.uuidCfdi == null || comprobante.rfcEmisor == null || comprobante.total == null;
            const pendiente = comprobante.estatus === 'Pendiente';
            return (
              <Tarjeta key={comprobante.id} titulo={`${comprobante.alumno} · ${comprobante.numeroControl}`} extra={<Insignia estatus={comprobante.estatus} />}>
                <div className="grid gap-2 text-sm sm:grid-cols-2">
                  <p><b>Concepto:</b> {comprobante.concepto}{comprobante.nivel ? ` · ${comprobante.nivel}` : ''}</p>
                  <p><b>Periodo:</b> {comprobante.periodo}</p>
                  <p><b>UUID:</b> <span className="break-all">{comprobante.uuidCfdi || 'Pendiente de captura manual'}</span></p>
                  <p><b>Abono:</b> {comprobante.total == null ? 'Pendiente de captura manual' : moneda(comprobante.total)}
                    {pendiente && <> · <b>Saldo previo:</b> {moneda(comprobante.saldoPendiente)}</>}</p>
                  {comprobante.grupoAsignado && <p className="sm:col-span-2"><b>Grupo asignado:</b> {comprobante.grupoAsignado}</p>}
                  {comprobante.motivoRechazo && <p className="sm:col-span-2 text-error"><b>Motivo de rechazo:</b> {comprobante.motivoRechazo}</p>}
                </div>
                <div className="mt-3 rounded-lg border border-linea p-3">
                  <p className="mb-2 text-sm font-semibold">Verificación automática</p>
                  {comprobante.estadoSat || comprobante.cotejoDescripcion
                    ? <VerificacionComprobante comprobante={comprobante} />
                    : <p className="text-xs text-tenue">Sin resultados de verificación automática.</p>}
                </div>
                {pendiente && (comprobante.estadoSat === 'Cancelado' ? (
                  <Alerta tipo="error" className="mt-3">El SAT reporta este CFDI como cancelado: debe ser rechazado.</Alerta>
                ) : tieneAlertaVerificacion(comprobante) ? (
                  <Alerta tipo="error" className="mt-3">La verificación automática encontró diferencias. Revisa el documento antes de aplicar el abono.</Alerta>
                ) : requiereCaptura ? (
                  <Alerta tipo="aviso" className="mt-3">No se pudo leer el QR: captura los datos impresos en el documento al revisarlo.</Alerta>
                ) : null)}
                <div className="mt-3 flex flex-wrap gap-2">
                  {pendiente && !esSoloLectura ? (
                    <Boton type="button" onClick={() => revisar(comprobante)}>
                      <Eye className="h-4 w-4" /> Revisar documento y validar
                    </Boton>
                  ) : (
                    <Boton variante="secundario" type="button" onClick={() => revisar(comprobante)}>
                      <Eye className="h-4 w-4" /> Ver comprobante {(comprobante.tipoArchivo || '').toUpperCase()}
                    </Boton>
                  )}
                </div>
              </Tarjeta>
            );
          })}
        <Paginador paginacion={paginacionPendientes} />
      </section>

      {revision && (
        <RevisionComprobante key={revision.id} comprobante={revision} soloLectura={esSoloLectura} grupos={grupos}
          onCerrar={() => setRevision(null)} onCambio={alCambiarRevision} />
      )}

      {!esSoloLectura && edicionConcepto && (
        <Modal titulo="Editar concepto de pago" onCerrar={() => setEdicionConcepto(null)} ancho="max-w-md">
          <form className="grid gap-4" onSubmit={async (e) => {
            e.preventDefault();
            try {
              await editarConcepto(edicionConcepto.id, { nombre: edicionConcepto.nombre.trim(), monto: Number(edicionConcepto.monto) });
              setEdicionConcepto(null);
              setAviso('Concepto actualizado.');
              await cargar();
            } catch (err) {
              setEdicionConcepto({ ...edicionConcepto, error: mensajeError(err) });
            }
          }}>
            {edicionConcepto.error && <Alerta tipo="error">{edicionConcepto.error}</Alerta>}
            <Campo etiqueta="Nombre del concepto" id="editarNombreConcepto"
              ayuda={edicionConcepto.sistema ? 'Concepto del sistema: genera las papeletas de los grupos, su nombre no cambia.' : undefined}>
              <input id="editarNombreConcepto" className={claseEntrada} required maxLength="30" value={edicionConcepto.nombre}
                disabled={edicionConcepto.sistema}
                onChange={(e) => setEdicionConcepto({ ...edicionConcepto, nombre: e.target.value })} />
            </Campo>
            <Campo etiqueta="Monto (MXN)" id="editarMontoConcepto" ayuda="Aplica a todos los niveles y a los cargos que se emitan desde ahora.">
              <input id="editarMontoConcepto" className={claseEntrada} type="number" required min="0.01" step="0.01"
                value={edicionConcepto.monto} onChange={(e) => setEdicionConcepto({ ...edicionConcepto, monto: e.target.value })} />
            </Campo>
            <div className="flex justify-end gap-2">
              <Boton type="button" variante="secundario" onClick={() => setEdicionConcepto(null)}>Cancelar</Boton>
              <Boton type="submit">Guardar</Boton>
            </div>
          </form>
        </Modal>
      )}

      {!esSoloLectura && borrarConcepto && (
        <ConfirmacionDoble titulo="Eliminar concepto de pago" palabra="ELIMINAR" textoBoton="Eliminar concepto"
          mensaje={<>Se eliminará el concepto <b>{borrarConcepto.nombre}</b>. Si ya se emitieron cargos con él,
            el sistema no lo permitirá y deberás desactivarlo.</>}
          onCerrar={() => setBorrarConcepto(null)}
          onConfirmar={async () => {
            try {
              await eliminarConcepto(borrarConcepto.id);
              setAviso(`Concepto «${borrarConcepto.nombre}» eliminado.`);
              await cargar();
            } catch (err) {
              setError(mensajeError(err));
            } finally {
              setBorrarConcepto(null);
            }
          }} />
      )}
    </div>
  );
}
