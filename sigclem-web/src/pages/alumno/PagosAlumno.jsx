import { useCallback, useEffect, useState } from 'react';
import { CreditCard, Eye, FileCheck2, Upload } from 'lucide-react';
import { mensajeError } from '../../api/cliente';
import { cargarComprobante, descargarComprobante, listarAutorizaciones } from '../../api/pagos';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';
import VerificacionComprobante from '../../components/VerificacionComprobante';
import { useVisorDocumento } from '../../components/VisorDocumento';

function moneda(valor) {
  return new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(valor);
}

const formularioVacio = () => ({ uuidCfdi: '', rfcEmisor: '', total: '', contenidoQr: '', archivo: null });

export default function PagosAlumno() {
  const verDocumento = useVisorDocumento();
  const [autorizaciones, setAutorizaciones] = useState([]);
  const [formularios, setFormularios] = useState({});
  const [estadoLecturaQr, setEstadoLecturaQr] = useState({});
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState('');
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');

  const cargar = useCallback(async () => {
    setCargando(true);
    setError('');
    try {
      setAutorizaciones(await listarAutorizaciones());
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => { cargar(); }, [cargar]);

  function cambiarCampo(id, campo, valor) {
    setFormularios((previos) => ({
      ...previos,
      [id]: { ...formularioVacio(), ...previos[id], [campo]: valor },
    }));
  }

  async function seleccionarEvidencia(id, archivo) {
    cambiarCampo(id, 'archivo', archivo);
    setEstadoLecturaQr((previos) => ({ ...previos, [id]: '' }));
    // El servidor lee el QR de PDF, JPG y PNG al enviar; la lectura en el navegador es solo una vista previa.
    if (!archivo || archivo.type === 'application/pdf' || typeof window.BarcodeDetector !== 'function') return;
    try {
      const detector = new window.BarcodeDetector({ formats: ['qr_code'] });
      const imagen = await createImageBitmap(archivo);
      let resultados;
      try {
        resultados = await detector.detect(imagen);
      } finally {
        imagen.close();
      }
      const contenido = resultados[0]?.rawValue || '';
      if (contenido) {
        cambiarCampo(id, 'contenidoQr', contenido.slice(0, 255));
        setEstadoLecturaQr((previos) => ({ ...previos, [id]: 'QR detectado en la imagen. El servidor lo confirmará al enviar.' }));
      }
    } catch {
      // Sin vista previa: el servidor intentará leer el QR al recibir el archivo.
    }
  }

  async function enviarComprobante(evento, autorizacion) {
    evento.preventDefault();
    const datos = { ...formularioVacio(), ...formularios[autorizacion.id] };
    if (!datos.archivo) {
      setError('Selecciona el comprobante de pago (PDF, JPG o PNG).');
      return;
    }
    const hayDatosParciales = [datos.uuidCfdi, datos.rfcEmisor, datos.total]
      .some((valor) => Boolean(String(valor).trim()));
    const todosLosDatos = [datos.uuidCfdi, datos.rfcEmisor, datos.total]
      .every((valor) => Boolean(String(valor).trim()));
    if (hayDatosParciales && !todosLosDatos) {
      setError('Captura UUID, RFC y total completos, o déjalos vacíos para que coordinación los registre manualmente.');
      return;
    }
    const multipart = new FormData();
    if (datos.uuidCfdi.trim()) multipart.append('uuidCfdi', datos.uuidCfdi.trim());
    if (datos.rfcEmisor.trim()) multipart.append('rfcEmisor', datos.rfcEmisor.trim());
    if (datos.total.trim()) multipart.append('total', datos.total.trim());
    if (datos.contenidoQr.trim()) multipart.append('contenidoQr', datos.contenidoQr.trim());
    multipart.append('archivo', datos.archivo);

    setGuardando(String(autorizacion.id));
    setError('');
    setAviso('');
    try {
      const enviado = await cargarComprobante(autorizacion.id, multipart);
      setAviso(enviado.uuidCfdi
        ? `Comprobante enviado. Se leyó el CFDI por ${moneda(enviado.total)} (SAT: ${enviado.estadoSat || 'sin consultar'}). Coordinación validará el pago.`
        : 'Comprobante enviado. No se pudo leer el QR; coordinación cotejará el documento y capturará los datos.');
      setFormularios((previos) => ({ ...previos, [autorizacion.id]: formularioVacio() }));
      setEstadoLecturaQr((previos) => ({ ...previos, [autorizacion.id]: '' }));
      await cargar();
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando('');
    }
  }

  function descargar(comprobante) {
    verDocumento({
      titulo: `Comprobante de pago · ${comprobante.concepto}`,
      nombre: `comprobante-${comprobante.id}`,
      cargar: () => descargarComprobante(comprobante.id),
    });
  }

  if (cargando) return <Cargando />;

  return (
    <div className="grid gap-[18px]">
      <div>
        <h1 className="flex items-center gap-2 text-xl font-bold"><CreditCard className="h-5 w-5" /> Pagos y comprobantes</h1>
        <p className="mt-1 text-sm text-tenue">Consulta los cargos emitidos por coordinación, sus abonos y el saldo pendiente.</p>
      </div>
      {error && <Alerta tipo="error">{error}</Alerta>}
      {aviso && <Alerta tipo="ok">{aviso}</Alerta>}

      <section className="grid gap-3" aria-label="Mis cargos de pago">
        <h2 className="text-base font-semibold">Mis cargos</h2>
        {autorizaciones.length === 0 ? (
          <Tarjeta><p className="text-sm text-tenue">Aún no tienes cargos emitidos. Consulta con coordinación si necesitas realizar un pago.</p></Tarjeta>
        ) : autorizaciones.map((autorizacion) => {
          const datos = { ...formularioVacio(), ...formularios[autorizacion.id] };
          const comprobantes = autorizacion.comprobantes || [];
          const pendiente = comprobantes.some((comprobante) => comprobante.estatus === 'Pendiente');
          const puedeSubir = autorizacion.estatusCargo !== 'Liquidada' && !pendiente;
          return (
            <Tarjeta key={autorizacion.id} titulo={`${autorizacion.concepto}${autorizacion.nivel ? ` · ${autorizacion.nivel}` : ''}`}>
              <div className="mb-3 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm">
                <span>{autorizacion.periodo}</span>
                <span>Cargo: <b>{moneda(autorizacion.montoEsperado)}</b></span>
                <span>Saldo: <b>{moneda(autorizacion.saldoPendiente)}</b></span>
                <span className="text-tenue">Cargo #{autorizacion.id}</span>
                <Insignia estatus={autorizacion.estatusCargo}>{autorizacion.estatusCargo}</Insignia>
              </div>

              {comprobantes.length > 0 && (
                <div className="mb-4 grid gap-2">
                  <h3 className="text-sm font-semibold">Historial de comprobantes</h3>
                  {comprobantes.map((comprobante) => (
                    <div key={comprobante.id} className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border border-linea p-3 text-sm">
                      <span>{comprobante.fechaCarga ? new Date(comprobante.fechaCarga).toLocaleDateString('es-MX') : 'Fecha pendiente'}</span>
                      <span>Importe: {comprobante.total == null ? 'Pendiente de captura' : moneda(comprobante.total)}</span>
                      <Insignia estatus={comprobante.estatus}>{comprobante.estatus}</Insignia>
                      {comprobante.motivoRechazo && <span className="text-error">Motivo: {comprobante.motivoRechazo}</span>}
                      <Boton variante="secundario" type="button" className="ml-auto" onClick={() => descargar(comprobante)}>
                        <Eye className="h-4 w-4" /> Ver comprobante
                      </Boton>
                      <div className="w-full"><VerificacionComprobante comprobante={comprobante} /></div>
                    </div>
                  ))}
                </div>
              )}

              {puedeSubir ? (
                <form className="grid gap-3 sm:grid-cols-2" onSubmit={(e) => enviarComprobante(e, autorizacion)}>
                  {autorizacion.motivoRechazo && (
                    <Alerta tipo="aviso" className="sm:col-span-2">
                      Comprobante anterior rechazado: {autorizacion.motivoRechazo}. Puedes enviar una evidencia corregida.
                    </Alerta>
                  )}
                  <div className="sm:col-span-2">
                    <Campo etiqueta="Comprobante de pago (PDF, JPG o PNG)" id={`archivo-${autorizacion.id}`}
                      ayuda="Sube el recibo oficial de cobro completo (hasta 5 MB). El sistema lee su QR, lo verifica ante el SAT y coteja la descripción con tu número de control.">
                      <input className={claseEntrada} id={`archivo-${autorizacion.id}`} required type="file" accept="application/pdf,image/jpeg,image/png,.pdf,.jpg,.jpeg,.png" onChange={(e) => seleccionarEvidencia(autorizacion.id, e.target.files?.[0] || null)} />
                    </Campo>
                    {estadoLecturaQr[autorizacion.id] && <p className="text-xs text-tenue" role="status">{estadoLecturaQr[autorizacion.id]}</p>}
                  </div>
                  <details className="sm:col-span-2 rounded-lg border border-linea p-3 text-sm">
                    <summary className="cursor-pointer font-semibold">Captura manual (solo si el QR está dañado o ilegible)</summary>
                    <div className="mt-3 grid gap-3 sm:grid-cols-2">
                      <Campo etiqueta="UUID / Folio fiscal" id={`uuid-${autorizacion.id}`}>
                        <input className={claseEntrada} id={`uuid-${autorizacion.id}`} maxLength="36" placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" value={datos.uuidCfdi} onChange={(e) => cambiarCampo(autorizacion.id, 'uuidCfdi', e.target.value)} />
                      </Campo>
                      <Campo etiqueta="RFC emisor" id={`rfc-${autorizacion.id}`} ayuda="TNM140723GFA">
                        <input className={claseEntrada} id={`rfc-${autorizacion.id}`} maxLength="13" value={datos.rfcEmisor} onChange={(e) => cambiarCampo(autorizacion.id, 'rfcEmisor', e.target.value.toUpperCase())} />
                      </Campo>
                      <Campo etiqueta="Total del recibo (MXN)" id={`total-${autorizacion.id}`} ayuda="Debe ser menor o igual al saldo.">
                        <input className={claseEntrada} id={`total-${autorizacion.id}`} type="number" min="0.01" max={autorizacion.saldoPendiente} step="0.01" value={datos.total} onChange={(e) => cambiarCampo(autorizacion.id, 'total', e.target.value)} />
                      </Campo>
                    </div>
                  </details>
                  <div className="sm:col-span-2">
                    <Boton type="submit" disabled={guardando !== ''}>
                      <Upload className="h-4 w-4" /> {guardando === String(autorizacion.id) ? 'Enviando…' : 'Enviar comprobante'}
                    </Boton>
                  </div>
                </form>
              ) : (
                <div className="flex items-center gap-2 text-sm text-tenue">
                  <FileCheck2 className="h-4 w-4" />
                  {autorizacion.estatusCargo === 'Liquidada'
                    ? 'Cargo liquidado.'
                    : 'Ya hay un comprobante pendiente de revisión.'}
                </div>
              )}
            </Tarjeta>
          );
        })}
      </section>
    </div>
  );
}
