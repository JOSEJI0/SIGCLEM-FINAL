import { useCallback, useEffect, useMemo, useState } from 'react';
import { Check, Eye, Pencil, Plus, Power, Receipt, RefreshCw, Search, Tags, Trash2, X } from 'lucide-react';
import { buscarAlumnos } from '../../api/alumnos';
import { mensajeError } from '../../api/cliente';
import { obtenerPeriodos } from '../../api/catalogos';
import { listarGrupos } from '../../api/grupos';
import {
  activarConcepto, capturarDatosComprobante, crearAutorizacion, crearConcepto, descargarComprobante,
  desactivarConcepto, editarConcepto, eliminarConcepto, listarCargos, listarComprobantesPendientes, listarConceptos,
  rechazarComprobante, reverificarComprobante, validarComprobante,
} from '../../api/pagos';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';
import { ConfirmacionDoble, Modal } from '../../components/Modal';
import VerificacionComprobante, { tieneAlertaVerificacion } from '../../components/VerificacionComprobante';
import Paginador, { usePaginacion } from '../../components/Paginador';
import { useVisorDocumento } from '../../components/VisorDocumento';

function moneda(valor) {
  return new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(valor);
}

const datosManualesVacios = () => ({ uuidCfdi: '', rfcEmisor: '', total: '', selloFinal: '' });

export default function Pagos() {
  const verDocumento = useVisorDocumento();
  const [edicionConcepto, setEdicionConcepto] = useState(null);
  const [borrarConcepto, setBorrarConcepto] = useState(null);
  const [conceptos, setConceptos] = useState([]);
  const [periodos, setPeriodos] = useState([]);
  const [comprobantes, setComprobantes] = useState([]);
  const [grupos, setGrupos] = useState([]);
  const [nombreConcepto, setNombreConcepto] = useState('');
  const [idTarifa, setIdTarifa] = useState('');
  const [monto, setMonto] = useState('');
  const [busquedaAlumno, setBusquedaAlumno] = useState('');
  const [alumnos, setAlumnos] = useState([]);
  const [alumno, setAlumno] = useState(null);
  const [cargos, setCargos] = useState([]);
  const [idPeriodo, setIdPeriodo] = useState('');
  const [idGrupo, setIdGrupo] = useState({});
  const [motivos, setMotivos] = useState({});
  const [datosManuales, setDatosManuales] = useState({});
  const [guardando, setGuardando] = useState('');
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      const [conceptosCargados, periodosCargados, pendientes] = await Promise.all([
        listarConceptos(true),
        obtenerPeriodos(),
        listarComprobantesPendientes(),
      ]);
      const periodosPendientes = [...new Set(pendientes.map((c) => c.idPeriodo))];
      const gruposPorPeriodo = await Promise.all(periodosPendientes.map((periodoId) => listarGrupos(periodoId)));
      setConceptos(conceptosCargados);
      setPeriodos(periodosCargados);
      setComprobantes(pendientes);
      setGrupos(gruposPorPeriodo.flat());
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
  const pendientesFiltrados = useMemo(() => {
    const texto = filtroPendientes.trim().toLowerCase();
    return comprobantes.filter((c) => (!soloAlertas || tieneAlertaVerificacion(c) || c.uuidCfdi == null)
      && (!texto || `${c.alumno} ${c.numeroControl} ${c.concepto}`.toLowerCase().includes(texto)));
  }, [comprobantes, filtroPendientes, soloAlertas]);
  // Al inicio del semestre puede haber cientos de comprobantes: se muestran de 10 en 10.
  const paginacionPendientes = usePaginacion(pendientesFiltrados, 10);
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
    if (!alumno) return;
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
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando('');
    }
  }

  async function guardarConcepto(evento) {
    evento.preventDefault();
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

  function actualizarDatosManuales(id, campo, valor) {
    setDatosManuales((previos) => ({
      ...previos,
      [id]: { ...datosManualesVacios(), ...previos[id], [campo]: valor },
    }));
  }

  async function capturarManual(comprobante) {
    const datos = { ...datosManualesVacios(), ...datosManuales[comprobante.id] };
    if (!datos.uuidCfdi.trim() || !datos.rfcEmisor.trim() || !datos.total.trim()) {
      setError(`Completa UUID, RFC e importe para el comprobante de ${comprobante.alumno}.`);
      return;
    }
    setGuardando(`manual-${comprobante.id}`);
    setError('');
    setAviso('');
    try {
      await capturarDatosComprobante(comprobante.id, {
        uuidCfdi: datos.uuidCfdi.trim(),
        rfcEmisor: datos.rfcEmisor.trim(),
        total: Number(datos.total),
        selloFinal: datos.selloFinal.trim() || null,
      });
      setAviso('Datos del comprobante capturados y consultados ante el SAT. Ahora puedes validar o rechazar la evidencia.');
      await cargar();
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando('');
    }
  }

  async function validar(comprobante) {
    const balance = Number(comprobante.saldoPendiente);
    const esLiquidacion = Math.abs(Number(comprobante.total) - balance) < 0.005;
    const grupoId = idGrupo[comprobante.id] || '';
    if (comprobante.concepto === 'Curso de nivel' && esLiquidacion && !grupoId && !comprobante.grupoAsignado) {
      setError(`Selecciona un grupo para la inscripción de ${comprobante.alumno}; este abono liquida el cargo.`);
      return;
    }
    if (tieneAlertaVerificacion(comprobante) && !window.confirm(
      `La verificación automática del comprobante de ${comprobante.alumno} tiene alertas `
      + `(SAT: ${comprobante.estadoSat || 'sin consultar'}, descripción: ${comprobante.cotejoDescripcion || 'sin cotejo'}). `
      + '¿Revisaste el documento y deseas validarlo de todos modos?',
    )) return;
    setGuardando(`comprobante-${comprobante.id}`);
    setError('');
    setAviso('');
    try {
      await validarComprobante(comprobante.id, esLiquidacion ? grupoId : '');
      setAviso(esLiquidacion
        ? `Pago liquidado${comprobante.concepto === 'Curso de nivel' ? ' e inscripción aplicada' : ''}.`
        : `Abono validado. Saldo restante: ${moneda(balance - Number(comprobante.total))}.`);
      await cargar();
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando('');
    }
  }

  async function rechazar(comprobante) {
    const motivo = motivos[comprobante.id]?.trim();
    if (!motivo) {
      setError(`Escribe el motivo de rechazo del comprobante de ${comprobante.alumno}.`);
      return;
    }
    setGuardando(`comprobante-${comprobante.id}`);
    setError('');
    setAviso('');
    try {
      await rechazarComprobante(comprobante.id, motivo);
      setAviso('Comprobante rechazado. El saldo no se modificó y el alumno podrá enviar una evidencia corregida.');
      await cargar();
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando('');
    }
  }

  async function reverificar(comprobante) {
    setGuardando(`verificar-${comprobante.id}`);
    setError('');
    setAviso('');
    try {
      const resultado = await reverificarComprobante(comprobante.id);
      setAviso(`Verificación repetida para ${comprobante.alumno}: SAT ${resultado.estadoSat || 'sin consultar'}, descripción ${resultado.cotejoDescripcion || 'sin cotejo'}.`);
      await cargar();
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando('');
    }
  }

  function descargar(comprobante) {
    verDocumento({
      titulo: `Comprobante de ${comprobante.alumno} · ${comprobante.numeroControl}`,
      nombre: `comprobante-${comprobante.numeroControl}-${comprobante.id}`,
      cargar: () => descargarComprobante(comprobante.id),
    });
  }

  if (cargando) return <Cargando />;

  return (
    <div className="grid gap-[18px]">
      <div>
        <h1 className="flex items-center gap-2 text-xl font-bold"><Receipt className="h-5 w-5" /> Pagos y comprobantes</h1>
        <p className="mt-1 text-sm text-tenue">Emite cargos, registra conceptos y revisa los abonos enviados por los alumnos.</p>
      </div>
      {error && <Alerta tipo="error">{error}</Alerta>}
      {aviso && <Alerta tipo="ok">{aviso}</Alerta>}

      <Tarjeta titulo="Emitir cargo a un alumno" icono={Search}>
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
            <div className="grid gap-2">
              <h3 className="text-sm font-semibold">Cargos existentes</h3>
              {cargos.length === 0 ? <p className="text-sm text-tenue">Este alumno no tiene cargos.</p> : cargos.map((cargo) => (
                <div key={cargo.id} className="flex flex-wrap items-center gap-3 rounded-lg border border-linea p-3 text-sm">
                  <span>{cargo.concepto}{cargo.nivel ? ` · ${cargo.nivel}` : ''} · {cargo.periodo}</span>
                  <span>Saldo: <b>{moneda(cargo.saldoPendiente)}</b></span>
                  <Insignia estatus={cargo.estatusCargo}>{cargo.estatusCargo}</Insignia>
                </div>
              ))}
            </div>
          </div>
        )}
      </Tarjeta>

      <Tarjeta titulo="Conceptos de pago" icono={Tags}>
        <p className="mb-3 text-[13px] text-tenue">
          Cada concepto tiene un solo monto que aplica a todos los niveles. Si cambias un monto, los cargos ya emitidos
          conservan el anterior.
        </p>
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
        <div className="mt-4 overflow-x-auto">
          <table className="w-full min-w-[560px] text-left text-sm">
            <thead className="border-b border-linea text-xs text-tenue">
              <tr><th className="px-2 py-2">Concepto</th><th className="px-2 py-2">Monto</th><th className="px-2 py-2">Estado</th><th className="px-2 py-2 text-right">Acciones</th></tr>
            </thead>
            <tbody>
              {conceptos.map((item) => (
                <tr key={item.id} className="border-b border-linea last:border-0">
                  <td className="px-2 py-3 font-semibold">{item.nombre}</td>
                  <td className="px-2 py-3">{item.monto != null ? moneda(item.monto) : <span className="text-tenue">Sin monto</span>}</td>
                  <td className="px-2 py-3"><Insignia estatus={item.activo ? 'Activo' : 'Inactivo'} tono={item.activo ? 'ok' : 'neutro'} /></td>
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
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Tarjeta>

      <section className="grid gap-3" aria-label="Comprobantes pendientes de validación">
        <h2 className="text-base font-semibold">Comprobantes pendientes ({comprobantes.length})</h2>
        {comprobantes.length > 0 && (
          <div className="flex flex-wrap items-center gap-3">
            <input className={`${claseEntrada} max-w-sm`} aria-label="Buscar comprobante por alumno, número de control o concepto"
              placeholder="Buscar por alumno, número de control o concepto" value={filtroPendientes}
              onChange={(e) => setFiltroPendientes(e.target.value)} />
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={soloAlertas} onChange={(e) => setSoloAlertas(e.target.checked)} />
              Solo con alertas o sin datos del CFDI
            </label>
          </div>
        )}
        {comprobantes.length === 0 ? <Tarjeta><p className="text-sm text-tenue">No hay comprobantes pendientes de revisión.</p></Tarjeta>
          : pendientesFiltrados.length === 0 ? <Tarjeta><p className="text-sm text-tenue">Ningún comprobante coincide con el filtro.</p></Tarjeta>
          : paginacionPendientes.visibles.map((comprobante) => {
            const balance = Number(comprobante.saldoPendiente);
            const importe = Number(comprobante.total);
            const requiereCaptura = comprobante.uuidCfdi == null || comprobante.rfcEmisor == null || comprobante.total == null;
            const esLiquidacion = !requiereCaptura && Math.abs(importe - balance) < 0.005;
            const gruposElegibles = grupos.filter((grupo) =>
              grupo.idPeriodo === comprobante.idPeriodo
              && grupo.idNivel === comprobante.idNivel
              && grupo.estatusActa !== 'Cerrada'
              && grupo.alumnosInscritos < grupo.cupo);
            const datos = { ...datosManualesVacios(), ...datosManuales[comprobante.id] };
            return (
              <Tarjeta key={comprobante.id} titulo={`${comprobante.alumno} · ${comprobante.numeroControl}`} extra={<Insignia estatus={comprobante.estatus} />}>
                <div className="grid gap-2 text-sm sm:grid-cols-2">
                  <p><b>Concepto:</b> {comprobante.concepto}{comprobante.nivel ? ` · ${comprobante.nivel}` : ''}</p>
                  <p><b>Periodo:</b> {comprobante.periodo}</p>
                  <p><b>UUID:</b> <span className="break-all">{comprobante.uuidCfdi || 'Pendiente de captura manual'}</span></p>
                  <p><b>RFC:</b> {comprobante.rfcEmisor || 'Pendiente de captura manual'}</p>
                  <p><b>Abono:</b> {comprobante.total == null ? 'Pendiente de captura manual' : moneda(comprobante.total)}</p>
                  <p><b>Saldo previo:</b> {moneda(comprobante.saldoPendiente)}</p>
                  {comprobante.contenidoQr && <p className="sm:col-span-2 break-all"><b>Contenido QR:</b> {comprobante.contenidoQr}</p>}
                </div>
                <div className="mt-3 rounded-lg border border-linea p-3">
                  <p className="mb-2 text-sm font-semibold">Verificación automática</p>
                  <VerificacionComprobante comprobante={comprobante} detallado />
                </div>
                <div className="mt-3 flex flex-wrap gap-2">
                  <Boton variante="secundario" type="button" onClick={() => descargar(comprobante)}>
                    <Eye className="h-4 w-4" /> Ver comprobante {(comprobante.tipoArchivo || 'jpg').toUpperCase()}
                  </Boton>
                  <Boton variante="secundario" type="button" disabled={guardando !== ''} onClick={() => reverificar(comprobante)}>
                    <RefreshCw className="h-4 w-4" /> {guardando === `verificar-${comprobante.id}` ? 'Verificando…' : 'Reverificar (QR, SAT y descripción)'}
                  </Boton>
                </div>
                {comprobante.estadoSat === 'Cancelado' ? (
                  <Alerta tipo="error" className="mt-3">El SAT reporta este CFDI como cancelado: recházalo.</Alerta>
                ) : tieneAlertaVerificacion(comprobante) ? (
                  <Alerta tipo="error" className="mt-3">
                    La verificación automática encontró diferencias. Revisa el documento antes de aplicar el abono.
                  </Alerta>
                ) : (
                  <Alerta tipo="aviso" className="mt-3">
                    {requiereCaptura
                      ? 'No se pudo leer el QR del comprobante; captura los datos del CFDI impresos en el documento.'
                      : 'Confirma que el documento corresponda al alumno y al concepto antes de aplicar el abono.'}
                  </Alerta>
                )}
                {requiereCaptura && (
                  <div className="mt-3 grid gap-3 sm:grid-cols-2">
                    <Campo etiqueta="UUID / Folio fiscal" id={`uuidManual-${comprobante.id}`}>
                      <input className={claseEntrada} id={`uuidManual-${comprobante.id}`} maxLength="36" value={datos.uuidCfdi} onChange={(e) => actualizarDatosManuales(comprobante.id, 'uuidCfdi', e.target.value)} />
                    </Campo>
                    <Campo etiqueta="RFC emisor" id={`rfcManual-${comprobante.id}`}>
                      <input className={claseEntrada} id={`rfcManual-${comprobante.id}`} maxLength="13" value={datos.rfcEmisor} onChange={(e) => actualizarDatosManuales(comprobante.id, 'rfcEmisor', e.target.value.toUpperCase())} />
                    </Campo>
                    <Campo etiqueta="Importe del abono (MXN)" id={`totalManual-${comprobante.id}`}>
                      <input className={claseEntrada} id={`totalManual-${comprobante.id}`} type="number" min="0.01" max={balance} step="0.01" value={datos.total} onChange={(e) => actualizarDatosManuales(comprobante.id, 'total', e.target.value)} />
                    </Campo>
                    <Campo etiqueta="Últimos 8 caracteres del sello digital (opcional)" id={`selloManual-${comprobante.id}`} ayuda="Final del «Sello Digital del CFDI», p. ej. P/Dk7w==. Con él se consulta el CFDI ante el SAT.">
                      <input className={claseEntrada} id={`selloManual-${comprobante.id}`} maxLength="8" value={datos.selloFinal} onChange={(e) => actualizarDatosManuales(comprobante.id, 'selloFinal', e.target.value.trim())} />
                    </Campo>
                    <Boton className="sm:col-span-2 sm:justify-self-start" type="button" disabled={guardando !== ''} onClick={() => capturarManual(comprobante)}>
                      {guardando === `manual-${comprobante.id}` ? 'Guardando…' : 'Guardar captura manual'}
                    </Boton>
                  </div>
                )}
                {comprobante.grupoAsignado && (
                  <Alerta tipo="info" className="mt-3">El alumno ya está asignado al grupo <b>{comprobante.grupoAsignado}</b>; al liquidar, el pago se liga a esa inscripción.</Alerta>
                )}
                {!requiereCaptura && !comprobante.grupoAsignado && comprobante.concepto === 'Curso de nivel' && esLiquidacion && (
                  <div className="mt-3">
                    <Campo etiqueta="Grupo para inscripción (solo al liquidar)" id={`grupo-${comprobante.id}`}>
                      <select className={claseEntrada} id={`grupo-${comprobante.id}`} required value={idGrupo[comprobante.id] || ''} onChange={(e) => setIdGrupo((previos) => ({ ...previos, [comprobante.id]: e.target.value }))}>
                        <option value="">Selecciona un grupo compatible</option>
                        {gruposElegibles.map((grupo) => (
                          <option key={grupo.id} value={grupo.id}>{grupo.clave} · {grupo.nivel} · {grupo.modalidad} · {grupo.alumnosInscritos}/{grupo.cupo}</option>
                        ))}
                      </select>
                    </Campo>
                    {gruposElegibles.length === 0 && <p className="mt-1 text-xs text-tenue">No hay grupos abiertos con cupo para el nivel y periodo autorizados.</p>}
                  </div>
                )}
                <Campo etiqueta="Motivo de rechazo (solo si rechazas)" id={`motivo-${comprobante.id}`}>
                  <textarea className={claseEntrada} id={`motivo-${comprobante.id}`} rows="2" maxLength="200" value={motivos[comprobante.id] || ''} onChange={(e) => setMotivos((previos) => ({ ...previos, [comprobante.id]: e.target.value }))} />
                </Campo>
                <div className="mt-3 flex flex-wrap gap-2">
                  <Boton variante="exito" type="button" disabled={guardando !== '' || requiereCaptura || comprobante.estadoSat === 'Cancelado' || (comprobante.concepto === 'Curso de nivel' && esLiquidacion && !idGrupo[comprobante.id] && !comprobante.grupoAsignado)} onClick={() => validar(comprobante)}>
                    <Check className="h-4 w-4" /> {guardando === `comprobante-${comprobante.id}` ? 'Procesando…' : esLiquidacion ? 'Validar, liquidar e inscribir' : 'Validar abono'}
                  </Boton>
                  <Boton variante="peligro" type="button" disabled={guardando !== '' || !motivos[comprobante.id]?.trim()} onClick={() => rechazar(comprobante)}>
                    <X className="h-4 w-4" /> Rechazar
                  </Boton>
                </div>
              </Tarjeta>
            );
          })}
        <Paginador paginacion={paginacionPendientes} />
      </section>
      {edicionConcepto && (
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
      {borrarConcepto && (
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
