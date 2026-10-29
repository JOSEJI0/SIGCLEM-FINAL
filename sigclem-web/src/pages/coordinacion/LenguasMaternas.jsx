import { useCallback, useEffect, useState } from 'react';
import { Check, FileCheck2, Languages, Plus, X } from 'lucide-react';
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

  async function revisar(registro, estatus) {
    setError('');
    setAviso('');
    try {
      await cambiarEstatusLenguaMaterna(registro.id, estatus);
      setAviso(`Registro de ${registro.nombreAlumno} ${estatus.toLowerCase()}.`);
      await cargar();
    } catch (e) {
      setError(mensajeError(e));
    }
  }

  const ver = (registro, tipo, etiqueta) => verDocumento({
  titulo: `${etiqueta} · ${registro.nombreAlumno}`,
  nombre: `lengua-${registro.id}-${tipo}`,
  cargar: () => descargarDocumentoLenguaMaterna(registro.id, tipo),
  });

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
                      <DocumentosLengua registro={registro} onVer={ver} />
                      {!soloLectura && registro.estatus === 'Pendiente' && (
                        <>
                        <Boton variante="secundario" type="button" className="mt-2" onClick={() => setSubir({ registro, intercultural: null, constancia: null, error: '' })}>
  Subir doc. intercultural y constancia
</Boton>
                          <Boton variante="exito" type="button" onClick={() => revisar(registro, 'Validado')}
                            aria-label={`Validar registro de ${registro.nombreAlumno}`}><Check className="h-4 w-4" /> Validar</Boton>
                          <Boton variante="peligro" type="button" onClick={() => revisar(registro, 'Rechazado')}
                            aria-label={`Rechazar registro de ${registro.nombreAlumno}`}><X className="h-4 w-4" /> Rechazar</Boton>
                        </>
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
      {[['intercultural', 'Documento de la Universidad Intercultural'], ['constancia', 'Constancia de acreditación de lengua materna']].map(([k, t]) => (
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
    </div>
  );
}
