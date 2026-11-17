import { useCallback, useEffect, useState } from 'react';
import { Check, FileCheck2, Languages, Plus, RotateCcw, X } from 'lucide-react';
import {
  cambiarEstatusLenguaMaterna,
  crearLenguaMaterna,
  descargarDocumentoLenguaMaterna,
  listarLenguasMaternas,
  listarRegistrosLenguasMaternas,
  subirDocumentosCoordinacion,
} from '../../api/lenguasMaternas';
import DocumentosLengua from '../../components/DocumentosLengua';
import { Modal } from '../../components/Modal';
import { mensajeError } from '../../api/cliente';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';
import { useVisorDocumento } from '../../components/VisorDocumento';


/** Con {@code soloLectura} (jefa del DGTV) solo se consultan catálogo, registros y documentos. */
export default function LenguasMaternas({ soloLectura = false }) {
  const verDocumento = useVisorDocumento();
  const [catalogo, setCatalogo] = useState([]);
  const [registros, setRegistros] = useState([]);
  const [nombre, setNombre] = useState('');
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [subir, setSubir] = useState(null);
  const [rechazo, setRechazo] = useState(null);
  const [validacion, setValidacion] = useState(null);

  const cargar = useCallback(async () => {
    setCargando(true);
    try {
      const [lenguas, items] = await Promise.all([listarLenguasMaternas(), listarRegistrosLenguasMaternas()]);
      setCatalogo(lenguas);
      setRegistros(items);
      setError('');
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => { cargar(); }, [cargar]);

  async function agregarLengua(evento) {
    evento.preventDefault();
    setGuardando(true);
    setError('');
    setAviso('');
    try {
      await crearLenguaMaterna({ nombre: nombre.trim() });
      setNombre('');
      setAviso('Lengua agregada al catálogo.');
      await cargar();
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando(false);
    }
  }

  async function revisar(registro, solicitud) {
    setError('');
    setAviso('');
    try {
      await cambiarEstatusLenguaMaterna(registro.id, solicitud);
      setRechazo(null);
      setValidacion(null);
      setAviso(registro.estatus === 'Validado' && solicitud.estatus === 'Validado'
        ? `Registro de ${registro.nombreAlumno} validado nuevamente.`
        : `Registro de ${registro.nombreAlumno} ${solicitud.estatus.toLowerCase()}.`);
      await cargar();
    } catch (e) {
      if (solicitud.estatus === 'Rechazado') {
        setRechazo((actual) => actual ? { ...actual, error: mensajeError(e) } : actual);
      } else if (solicitud.estatus === 'Validado' && solicitud.folio) {
        setValidacion((actual) => actual ? { ...actual, error: mensajeError(e) } : actual);
      } else {
        setError(mensajeError(e));
      }
    }
  }

  const ver = (registro, tipo, etiqueta) => verDocumento({
  titulo: `${etiqueta} · ${registro.nombreAlumno}`,
  nombre: `lengua-${registro.id}-${tipo}`,
  cargar: () => descargarDocumentoLenguaMaterna(registro.id, tipo),
  });

  async function descargarConstancia(registro) {
    try {
      const blob = await descargarDocumentoLenguaMaterna(registro.id, 'constancia');
      const url = URL.createObjectURL(blob);
      const enlace = document.createElement('a');
      enlace.href = url;
      enlace.download = `constancia-lengua-materna-${registro.folioConstancia}.docx`;
      enlace.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      setError(mensajeError(e));
    }
  }

  function obtenerDocumento(registro) {
    verDocumento({
      titulo: `Documento de lengua materna · ${registro.alumno ?? registro.nombreAlumno ?? registro.numeroControl ?? ''}`,
      nombre: `documento-lengua-${registro.id}`,
      cargar: () => descargarDocumentoLenguaMaterna(registro.id),
    });
  }

  if (cargando) return <Cargando />;

  return (
    <div className="grid gap-[18px]">
      <div>
        <h1 className="flex items-center gap-2 text-xl font-bold"><Languages className="h-5 w-5" /> Lenguas maternas</h1>
        <p className="mt-1 text-sm text-tenue">{soloLectura ? 'Consulta el catálogo y los registros del alumnado.' : 'Administra el catálogo y revisa los documentos enviados por el alumnado.'}</p>
      </div>
      {error && <Alerta tipo="error">{error}</Alerta>}
      {aviso && <Alerta tipo="ok">{aviso}</Alerta>}

      {soloLectura && <Alerta tipo="info">Consulta de solo lectura: las operaciones las realiza la coordinación.</Alerta>}
      <Tarjeta titulo="Catálogo de lenguas" icono={Plus}>
        {!soloLectura && <form className="mb-4 flex max-w-xl items-end gap-3" onSubmit={agregarLengua}>
          <Campo etiqueta="Nueva lengua" id="nuevaLengua">
            <input className={claseEntrada} id="nuevaLengua" maxLength={40} required value={nombre}
              onChange={(evento) => setNombre(evento.target.value)} />
          </Campo>
          <Boton type="submit" disabled={guardando || !nombre.trim()}>Agregar</Boton>
        </form>}
        <div className="flex flex-wrap gap-2">
          {catalogo.map((lengua) => (
            <span key={lengua.id} className="rounded-full bg-info-fondo px-3 py-1.5 text-xs font-medium text-[#1C4A7E]">
              {lengua.nombre}
            </span>
          ))}
        </div>
      </Tarjeta>

      <Tarjeta titulo="Registros de alumnado" icono={FileCheck2} extra={`${registros.length} registros`}>
        {registros.length === 0 ? (
          <p className="text-sm text-tenue">Aún no hay registros para revisar.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[850px] text-left">
              <thead><tr className="border-b border-linea text-xs text-tenue">
                <th className="py-2 pr-3">Alumno</th><th className="py-2 pr-3">Núm. control</th>
                <th className="py-2 pr-3">Lengua</th><th className="py-2 pr-3">Nivel</th>
                <th className="py-2 pr-3">Fecha</th><th className="py-2 pr-3">Estatus</th><th className="py-2">Acciones</th>
              </tr></thead>
              <tbody>{registros.map((registro) => (
                <tr className="border-b border-linea last:border-0" key={registro.id}>
                  <td className="py-3 pr-3 font-medium">{registro.nombreAlumno}</td>
                  <td className="py-3 pr-3">{registro.numeroControl}</td>
                  <td className="py-3 pr-3">{registro.lengua}</td>
                  <td className="py-3 pr-3">{registro.nivelDominio || '—'}</td>
                  <td className="py-3 pr-3">{registro.fechaRegistro}</td>
                  <td className="py-3 pr-3"><Insignia estatus={registro.estatus} /></td>
                  <td className="py-3">
                    <div className="flex flex-wrap gap-2">
                      {registro.motivoRechazo && (
                        <div className="w-full rounded-md bg-aviso-fondo p-2 text-xs text-[#7A5210]">
                          <b>Documento rechazado:</b> {{
                            curp: 'CURP', domicilio: 'Comprobante de domicilio', ine: 'INE',
                          }[registro.documentoRechazado] || 'No especificado'}.
                          <br /><b>Motivo:</b> {registro.motivoRechazo}
                        </div>
                      )}
                      <DocumentosLengua registro={registro} onVer={ver} onDescargar={descargarConstancia} />
                      {registro.folioConstancia && (
                        <span className="text-xs text-tenue">Constancia Word · folio {registro.folioConstancia}</span>
                      )}
                      {!soloLectura && ['Pendiente', 'Validado'].includes(registro.estatus) && (
                        <Boton variante="secundario" type="button" className="mt-2"
                          onClick={() => setSubir({ registro, intercultural: null, constancia: null, error: '' })}>
                          Subir doc. intercultural y constancia
                        </Boton>
                      )}
                      {!soloLectura && registro.estatus === 'Pendiente' && (
                        <>
                          <Boton variante="exito" type="button" disabled={!registro.tieneIntercultural}
                            onClick={() => setValidacion({ registro, folio: '', error: '' })}
                            aria-label={`Validar registro de ${registro.nombreAlumno}`}><Check className="h-4 w-4" /> Validar</Boton>
                          <Boton variante="peligro" type="button" onClick={() => setRechazo({
                            registro, documentoRechazado: '', motivoRechazo: '', error: '',
                          })}
                            aria-label={`Rechazar registro de ${registro.nombreAlumno}`}><X className="h-4 w-4" /> Rechazar</Boton>
                        </>
                      )}
                      {!soloLectura && registro.estatus === 'Validado' && (
                        <Boton variante="secundario" type="button" disabled={!registro.tieneIntercultural}
                          onClick={() => revisar(registro, { estatus: 'Validado' })}
                          aria-label={`Volver a validar registro de ${registro.nombreAlumno}`}>
                          <RotateCcw className="h-4 w-4" /> Volver a validar
                        </Boton>
                      )}
                      {!soloLectura && ['Pendiente', 'Validado'].includes(registro.estatus)
                        && !registro.tieneIntercultural && (
                        <span className="self-center text-xs text-aviso">
                          Para validar, carga el documento de la Universidad Intercultural. La constancia se genera desde la plantilla Word.
                        </span>
                      )}
                    </div>
                  </td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        )}
      </Tarjeta>
      {subir && (
  <Modal titulo={`Documentos de acreditación · ${subir.registro.nombreAlumno}`} onCerrar={() => setSubir(null)} ancho="max-w-md">
    <form className="grid gap-3" onSubmit={async (e) => {
      e.preventDefault();
      try {
        await subirDocumentosCoordinacion(subir.registro.id, subir);
        setSubir(null); setAviso('Documentos cargados.'); await cargar();
      } catch (err) { setSubir({ ...subir, error: mensajeError(err) }); }
    }}>
      {subir.error && <Alerta tipo="error">{subir.error}</Alerta>}
      {[['intercultural', 'Documento de la Universidad Intercultural']].map(([k, t]) => (
        <Campo key={k} etiqueta={t} id={`sub-${k}`} ayuda="PDF, PNG o JPEG; máximo 5 MB.">
          <input id={`sub-${k}`} type="file" className={claseEntrada} accept=".pdf,.png,.jpg,.jpeg"
            onChange={(ev) => setSubir({ ...subir, [k]: ev.target.files?.[0] || null })} />
        </Campo>
      ))}
      <div className="flex justify-end gap-2">
        <Boton type="button" variante="secundario" onClick={() => setSubir(null)}>Cancelar</Boton>
        <Boton type="submit" disabled={!subir.intercultural && !subir.constancia}>Guardar</Boton>
      </div>
    </form>
  </Modal>
)}
      {validacion && (
        <Modal titulo={`Generar constancia · ${validacion.registro.nombreAlumno}`}
          onCerrar={() => setValidacion(null)} ancho="max-w-md">
          <form className="grid gap-3" onSubmit={async (evento) => {
            evento.preventDefault();
            await revisar(validacion.registro, { estatus: 'Validado', folio: validacion.folio.trim().toUpperCase() });
          }}>
            {validacion.error && <Alerta tipo="error">{validacion.error}</Alerta>}
            <p className="text-sm text-tenue">
              Se generará la constancia Word de {validacion.registro.lengua} con los datos académicos del expediente.
            </p>
            <Campo etiqueta="Folio único" id="folioLenguaMaterna">
              <input id="folioLenguaMaterna" className={claseEntrada} required maxLength={20}
                pattern="[A-Za-z0-9]+" autoFocus value={validacion.folio}
                onChange={(evento) => setValidacion({ ...validacion, folio: evento.target.value.toUpperCase() })} />
            </Campo>
            <div className="flex justify-end gap-2">
              <Boton type="button" variante="secundario" onClick={() => setValidacion(null)}>Cancelar</Boton>
              <Boton type="submit" disabled={!validacion.folio.trim()}>Generar y validar</Boton>
            </div>
          </form>
        </Modal>
      )}
      {rechazo && (
        <Modal titulo={`Rechazar registro · ${rechazo.registro.nombreAlumno}`}
          onCerrar={() => setRechazo(null)} ancho="max-w-md">
          <form className="grid gap-3" onSubmit={async (evento) => {
            evento.preventDefault();
            await revisar(rechazo.registro, {
              estatus: 'Rechazado',
              documentoRechazado: rechazo.documentoRechazado,
              motivoRechazo: rechazo.motivoRechazo.trim(),
            });
          }}>
            {rechazo.error && <Alerta tipo="error">{rechazo.error}</Alerta>}
            <Campo etiqueta="Documento que debe corregirse" id="documentoRechazado">
              <select id="documentoRechazado" className={claseEntrada} required
                value={rechazo.documentoRechazado}
                onChange={(evento) => setRechazo({ ...rechazo, documentoRechazado: evento.target.value })}>
                <option value="">Selecciona un documento</option>
                <option value="curp">CURP</option>
                <option value="domicilio">Comprobante de domicilio</option>
                <option value="ine">INE</option>
              </select>
            </Campo>
            <Campo etiqueta="Motivo del rechazo" id="motivoRechazo">
              <textarea id="motivoRechazo" className={claseEntrada} required maxLength={500} rows={4}
                value={rechazo.motivoRechazo}
                onChange={(evento) => setRechazo({ ...rechazo, motivoRechazo: evento.target.value })} />
            </Campo>
            <div className="flex justify-end gap-2">
              <Boton type="button" variante="secundario" onClick={() => setRechazo(null)}>Cancelar</Boton>
              <Boton type="submit" variante="peligro" disabled={!rechazo.motivoRechazo.trim()}>
                Confirmar rechazo
              </Boton>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}
