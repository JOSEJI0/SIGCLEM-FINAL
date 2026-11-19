import { useCallback, useEffect, useState } from 'react';
import { Check, Download, ExternalLink, FileText, RefreshCw, X } from 'lucide-react';
import { mensajeError } from '../api/cliente';
import {
  capturarDatosComprobante, descargarComprobante, rechazarComprobante, reverificarComprobante, validarComprobante,
} from '../api/pagos';
import { Alerta, Boton, Campo, Insignia, claseEntrada } from './ui';
import VerificacionComprobante, { tieneAlertaVerificacion } from './VerificacionComprobante';
import { AreaDocumento, ControlesZoom, descargarUrl, nombreArchivo, useDocumento } from './VisorDocumento';

const CURSO = 'Curso de nivel';

function moneda(valor) {
  return new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(valor || 0);
}

const datosVacios = () => ({ uuidCfdi: '', rfcEmisor: '', total: '', selloFinal: '' });

/** Si la verificación automática no concluyó en «SAT vigente» y «descripción coincide», se pide confirmar la revisión. */
export function verificacionConcluida(comprobante) {
  return comprobante.estadoSat === 'Vigente' && comprobante.cotejoDescripcion === 'Coincide';
}

/**
 * Revisión de un comprobante de pago: el documento a la izquierda y, a la derecha, los datos del CFDI, el resultado
 * de la verificación automática (QR, SAT y descripción) y, para coordinación, la verificación manual: capturar los
 * datos si no se leyó el QR, validar (con grupo si liquida un curso) o rechazar con motivo. Sin permisos de
 * escritura (jefa del DGTV) o con el comprobante ya resuelto, el panel es solo de consulta.
 *
 * @param onCambio  se llama con un aviso tras validar o rechazar (el panel se cierra) o con null tras capturar o
 *                  reverificar (el panel sigue abierto con los datos nuevos), para que la pantalla recargue su lista.
 */
export default function RevisionComprobante({ comprobante: inicial, soloLectura = false, grupos = [], onCerrar, onCambio }) {
  const [comprobante, setComprobante] = useState(inicial);
  const [zoom, setZoom] = useState(1);
  const [datos, setDatos] = useState(datosVacios);
  const [idGrupo, setIdGrupo] = useState('');
  const [motivo, setMotivo] = useState('');
  const [revisado, setRevisado] = useState(false);
  const [guardando, setGuardando] = useState('');
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');

  const documento = useDocumento(inicial.id, useCallback(() => descargarComprobante(inicial.id), [inicial.id]));
  const titulo = `Comprobante de ${comprobante.alumno} · ${comprobante.numeroControl}`;
  const archivo = nombreArchivo(`comprobante-${comprobante.numeroControl}-${comprobante.id}`, documento.tipo);

  useEffect(() => {
    const alPulsar = (e) => e.key === 'Escape' && !guardando && onCerrar();
    window.addEventListener('keydown', alPulsar);
    const desbordeAnterior = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', alPulsar);
      document.body.style.overflow = desbordeAnterior;
    };
  }, [onCerrar, guardando]);

  const pendiente = comprobante.estatus === 'Pendiente';
  const editable = pendiente && !soloLectura;
  const requiereCaptura = comprobante.uuidCfdi == null || comprobante.rfcEmisor == null || comprobante.total == null;
  const saldo = Number(comprobante.saldoPendiente);
  const esLiquidacion = !requiereCaptura && Math.abs(Number(comprobante.total) - saldo) < 0.005;
  const pideGrupo = comprobante.concepto === CURSO && esLiquidacion && !comprobante.grupoAsignado;
  const pideConfirmacion = !verificacionConcluida(comprobante);
  const cancelado = comprobante.estadoSat === 'Cancelado';
  const gruposElegibles = grupos.filter((grupo) => grupo.idPeriodo === comprobante.idPeriodo
    && grupo.idNivel === comprobante.idNivel && grupo.estatusActa !== 'Cerrada'
    && grupo.cupo != null && grupo.alumnosInscritos < grupo.cupo);

  async function ejecutar(clave, accion) {
    setGuardando(clave);
    setError('');
    setAviso('');
    try {
      await accion();
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando('');
    }
  }

  const actualizar = (nuevo, texto) => {
    // Las respuestas de captura y reverificación no traen el grupo asignado: se conserva el que ya se conocía.
    setComprobante({ ...nuevo, grupoAsignado: comprobante.grupoAsignado });
    setRevisado(false);
    setAviso(texto);
    onCambio?.(null);
  };

  const capturar = () => {
    if (!datos.uuidCfdi.trim() || !datos.rfcEmisor.trim() || !datos.total) {
      setError('Completa UUID, RFC e importe tal como aparecen en el documento.');
      return;
    }
    ejecutar('captura', async () => {
      const resultado = await capturarDatosComprobante(comprobante.id, {
        uuidCfdi: datos.uuidCfdi.trim(),
        rfcEmisor: datos.rfcEmisor.trim(),
        total: Number(datos.total),
        selloFinal: datos.selloFinal.trim() || null,
      });
      setDatos(datosVacios());
      actualizar(resultado, `Datos capturados. SAT: ${resultado.estadoSat || 'sin consultar'}. Revisa y valida o rechaza.`);
    });
  };

  const reverificar = () => ejecutar('reverificar', async () => {
    const resultado = await reverificarComprobante(comprobante.id);
    actualizar(resultado, `Verificación repetida: SAT ${resultado.estadoSat || 'sin consultar'}, descripción ${resultado.cotejoDescripcion || 'sin cotejo'}.`);
  });

  const validar = () => ejecutar('validar', async () => {
    await validarComprobante(comprobante.id, esLiquidacion ? idGrupo : '');
    onCambio?.(esLiquidacion
      ? `Comprobante de ${comprobante.alumno} validado: cargo liquidado${comprobante.concepto === CURSO ? ' e inscripción aplicada' : ''}.`
      : `Abono de ${comprobante.alumno} validado. Saldo restante: ${moneda(saldo - Number(comprobante.total))}.`);
    onCerrar();
  });

  const rechazar = () => ejecutar('rechazar', async () => {
    await rechazarComprobante(comprobante.id, motivo.trim());
    onCambio?.(`Comprobante de ${comprobante.alumno} rechazado. El saldo no cambió y el alumno puede enviar otro.`);
    onCerrar();
  });

  const cambiar = (campo, valor) => setDatos((previos) => ({ ...previos, [campo]: valor }));

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-2 sm:p-4" onClick={() => !guardando && onCerrar()}>
      <div role="dialog" aria-modal="true" aria-label={titulo}
        className="flex h-[96vh] w-full max-w-7xl flex-col overflow-hidden rounded-xl bg-white shadow-xl"
        onClick={(e) => e.stopPropagation()}>
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-linea px-4 py-3">
          <h3 className="flex min-w-0 items-center gap-2 text-base font-semibold">
            <FileText className="h-5 w-5 shrink-0 text-marino" aria-hidden />
            <span className="truncate">{titulo}</span>
            <Insignia estatus={comprobante.estatus} />
          </h3>
          <div className="flex items-center gap-2">
            {documento.url && <div className="mr-1 hidden sm:block"><ControlesZoom zoom={zoom} onZoom={setZoom} /></div>}
            <Boton variante="secundario" type="button" disabled={!documento.url} onClick={() => descargarUrl(documento.url, archivo)}>
              <Download className="h-4 w-4" /> <span className="hidden sm:inline">Descargar</span>
            </Boton>
            <Boton variante="secundario" type="button" disabled={!documento.url} title="Abrir en una pestaña nueva"
              onClick={() => window.open(documento.url, '_blank', 'noopener')}>
              <ExternalLink className="h-4 w-4" />
            </Boton>
            <button type="button" onClick={onCerrar} disabled={guardando !== ''} aria-label="Cerrar revisión"
              className="rounded-md p-1.5 text-tenue hover:bg-black/5 hover:text-tinta">
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>

        <div className="flex min-h-0 flex-1 flex-col lg:flex-row">
          <div className="h-[45vh] shrink-0 lg:h-auto lg:min-w-0 lg:flex-1">
            <AreaDocumento documento={documento} titulo={titulo} zoom={zoom} />
          </div>

          <aside className="grid min-h-0 flex-1 content-start gap-3 overflow-y-auto border-t border-linea p-4 text-sm lg:w-[400px] lg:flex-none lg:border-l lg:border-t-0"
            aria-label="Datos y verificación del comprobante">
            {error && <Alerta tipo="error">{error}</Alerta>}
            {aviso && <Alerta tipo="ok">{aviso}</Alerta>}

            <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
              <dt className="font-semibold">Concepto</dt><dd>{comprobante.concepto}{comprobante.nivel ? ` · ${comprobante.nivel}` : ''}</dd>
              <dt className="font-semibold">Periodo</dt><dd>{comprobante.periodo}</dd>
              <dt className="font-semibold">Recibido</dt><dd>{comprobante.fechaCarga ? new Date(comprobante.fechaCarga).toLocaleString('es-MX') : '—'}</dd>
              <dt className="font-semibold">UUID</dt><dd className="break-all">{comprobante.uuidCfdi || 'Sin capturar'}</dd>
              <dt className="font-semibold">RFC emisor</dt><dd>{comprobante.rfcEmisor || 'Sin capturar'}</dd>
              <dt className="font-semibold">Abono</dt><dd>{comprobante.total == null ? 'Sin capturar' : moneda(comprobante.total)}</dd>
              <dt className="font-semibold">Saldo del cargo</dt><dd>{moneda(comprobante.saldoPendiente)}</dd>
              {comprobante.grupoAsignado && <><dt className="font-semibold">Grupo</dt><dd>{comprobante.grupoAsignado}</dd></>}
            </dl>

            <section className="rounded-lg border border-linea p-3">
              <div className="mb-2 flex items-center justify-between gap-2">
                <h4 className="font-semibold">Verificación automática</h4>
                {editable && (
                  <Boton variante="secundario" type="button" className="!px-2 !py-1 text-xs" disabled={guardando !== ''} onClick={reverificar}>
                    <RefreshCw className="h-3.5 w-3.5" /> {guardando === 'reverificar' ? 'Verificando…' : 'Repetir'}
                  </Boton>
                )}
              </div>
              {comprobante.estadoSat || comprobante.cotejoDescripcion
                ? <VerificacionComprobante comprobante={comprobante} detallado />
                : <p className="text-xs text-tenue">Sin resultados de verificación automática.</p>}
              {comprobante.contenidoQr && <p className="mt-2 break-all text-xs text-tenue"><b>QR:</b> {comprobante.contenidoQr}</p>}
            </section>

            {comprobante.estatus === 'Rechazado' && comprobante.motivoRechazo && (
              <Alerta tipo="aviso">Rechazado. Motivo: {comprobante.motivoRechazo}</Alerta>
            )}
            {!pendiente && comprobante.estatus !== 'Rechazado' && <Alerta tipo="ok">Comprobante {comprobante.estatus.toLowerCase()}.</Alerta>}
            {pendiente && soloLectura && <Alerta tipo="info">Comprobante en revisión por coordinación (solo consulta).</Alerta>}

            {editable && (
              <>
                {cancelado ? (
                  <Alerta tipo="error">El SAT reporta este CFDI como cancelado: no puede validarse, recházalo.</Alerta>
                ) : tieneAlertaVerificacion(comprobante) ? (
                  <Alerta tipo="error">La verificación automática encontró diferencias. Coteja el documento antes de decidir.</Alerta>
                ) : pideConfirmacion && !requiereCaptura ? (
                  <Alerta tipo="aviso">La verificación automática no concluyó. Puedes validar manualmente tras revisar el documento.</Alerta>
                ) : null}

                {requiereCaptura && (
                  <section className="grid gap-2 rounded-lg border border-linea p-3">
                    <h4 className="font-semibold">Captura manual</h4>
                    <p className="text-xs text-tenue">No se leyó el QR. Escribe los datos impresos en el documento; se consultarán ante el SAT.</p>
                    <Campo etiqueta="UUID / Folio fiscal" id={`rev-uuid-${comprobante.id}`}>
                      <input className={claseEntrada} id={`rev-uuid-${comprobante.id}`} maxLength="36" value={datos.uuidCfdi} onChange={(e) => cambiar('uuidCfdi', e.target.value)} />
                    </Campo>
                    <div className="grid grid-cols-2 gap-2">
                      <Campo etiqueta="RFC emisor" id={`rev-rfc-${comprobante.id}`}>
                        <input className={claseEntrada} id={`rev-rfc-${comprobante.id}`} maxLength="13" placeholder="TNM140723GFA" value={datos.rfcEmisor} onChange={(e) => cambiar('rfcEmisor', e.target.value.toUpperCase())} />
                      </Campo>
                      <Campo etiqueta="Importe (MXN)" id={`rev-total-${comprobante.id}`}>
                        <input className={claseEntrada} id={`rev-total-${comprobante.id}`} type="number" min="0.01" max={saldo} step="0.01" value={datos.total} onChange={(e) => cambiar('total', e.target.value)} />
                      </Campo>
                    </div>
                    <Campo etiqueta="Últimos 8 caracteres del sello digital (opcional)" id={`rev-sello-${comprobante.id}`}>
                      <input className={claseEntrada} id={`rev-sello-${comprobante.id}`} maxLength="8" value={datos.selloFinal} onChange={(e) => cambiar('selloFinal', e.target.value.trim())} />
                    </Campo>
                    <Boton type="button" className="justify-self-start" disabled={guardando !== ''} onClick={capturar}>
                      {guardando === 'captura' ? 'Guardando…' : 'Guardar datos del CFDI'}
                    </Boton>
                  </section>
                )}

                {!requiereCaptura && !cancelado && (
                  <section className="grid gap-2 rounded-lg border border-linea p-3">
                    <h4 className="font-semibold">Validar</h4>
                    {comprobante.grupoAsignado && esLiquidacion && (
                      <p className="text-xs text-tenue">Al liquidar, el pago se liga al grupo <b>{comprobante.grupoAsignado}</b>.</p>
                    )}
                    {pideGrupo && (
                      <Campo etiqueta="Grupo para la inscripción (este abono liquida el curso)" id={`rev-grupo-${comprobante.id}`}>
                        <select className={claseEntrada} id={`rev-grupo-${comprobante.id}`} value={idGrupo} onChange={(e) => setIdGrupo(e.target.value)}>
                          <option value="">Selecciona un grupo compatible</option>
                          {gruposElegibles.map((grupo) => (
                            <option key={grupo.id} value={grupo.id}>{grupo.clave} · {grupo.intensivo ? 'Intensivo · 10 niveles' : [grupo.nivel, grupo.nivelSecundario].filter(Boolean).join(' + ')} · {grupo.modalidad} · {grupo.alumnosInscritos}/{grupo.cupo}</option>
                          ))}
                        </select>
                        {gruposElegibles.length === 0 && <p className="mt-1 text-xs text-tenue">No hay grupos abiertos con cupo para el nivel y periodo del cargo.</p>}
                      </Campo>
                    )}
                    {pideConfirmacion && (
                      <label className="flex items-start gap-2 text-xs">
                        <input type="checkbox" className="mt-0.5" checked={revisado} onChange={(e) => setRevisado(e.target.checked)} />
                        <span>Revisé el documento: corresponde a {comprobante.alumno}, al concepto y al importe de {moneda(comprobante.total)}.</span>
                      </label>
                    )}
                    <Boton variante="exito" type="button" className="justify-self-start"
                      disabled={guardando !== '' || (pideGrupo && !idGrupo) || (pideConfirmacion && !revisado)} onClick={validar}>
                      <Check className="h-4 w-4" /> {guardando === 'validar' ? 'Validando…' : esLiquidacion ? 'Validar y liquidar' : 'Validar abono'}
                    </Boton>
                  </section>
                )}

                <section className="grid gap-2 rounded-lg border border-linea p-3">
                  <h4 className="font-semibold">Rechazar</h4>
                  <Campo etiqueta="Motivo (lo verá el alumno)" id={`rev-motivo-${comprobante.id}`}>
                    <textarea className={claseEntrada} id={`rev-motivo-${comprobante.id}`} rows="2" maxLength="200" value={motivo} onChange={(e) => setMotivo(e.target.value)} />
                  </Campo>
                  <Boton variante="peligro" type="button" className="justify-self-start" disabled={guardando !== '' || !motivo.trim()} onClick={rechazar}>
                    <X className="h-4 w-4" /> {guardando === 'rechazar' ? 'Rechazando…' : 'Rechazar comprobante'}
                  </Boton>
                </section>
                <p className="text-xs text-tenue">La validación o el rechazo quedan registrados en la bitácora de auditoría con tu cuenta.</p>
              </>
            )}
          </aside>
        </div>
      </div>
    </div>
  );
}
