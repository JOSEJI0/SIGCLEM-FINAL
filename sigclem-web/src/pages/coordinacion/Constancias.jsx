import { useEffect, useState } from 'react';
import { Award, CheckCircle2, Eye, FilePlus2, XCircle } from 'lucide-react';
import {
  autorizarConstancia, descargarPdfConstancia, emitirConstanciaDirecta, listarConstancias, rechazarConstancia,
} from '../../api/constancias';
import { buscarAlumnos } from '../../api/alumnos';
import { mensajeError } from '../../api/cliente';
import ConstanciasAvance from '../../components/ConstanciasAvance';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';
import { useVisorDocumento } from '../../components/VisorDocumento';

function fecha(iso) {
  return iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('es-MX') : '—';
}


/** Con {@code soloLectura} (jefa del DGTV) solo se consultan y descargan las constancias. */
export default function Constancias({ soloLectura = false }) {
  const verDocumento = useVisorDocumento();
  const [constancias, setConstancias] = useState([]);
  const [filtro, setFiltro] = useState('Solicitada');
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [seleccionada, setSeleccionada] = useState(null);
  const [modo, setModo] = useState('');
  const [folio, setFolio] = useState('');
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState('');
  const [exito, setExito] = useState('');
  const [directa, setDirecta] = useState({ numeroControl: '', folio: '' });
  const [alumnosAcreditados, setAlumnosAcreditados] = useState([]);
  const [emitiendo, setEmitiendo] = useState(false);

  // Sugerencias: alumnos que ya cubrieron los diez niveles (nivel actual = idioma acreditado).
  useEffect(() => {
    buscarAlumnos({ nivel: 0, tamano: 100 }).then((p) => setAlumnosAcreditados(p.content)).catch(() => {});
  }, []);

  const emitirDirecta = async (event) => {
    event.preventDefault();
    setEmitiendo(true);
    setError('');
    setExito('');
    try {
      const emitida = await emitirConstanciaDirecta(directa.numeroControl.trim().toUpperCase(), directa.folio.trim().toUpperCase());
      setExito(`Constancia ${emitida.folio} emitida para ${emitida.nombreAlumno}.`);
      setDirecta({ numeroControl: '', folio: '' });
      setFiltro('Emitida');
      cargar();
    } catch (err) {
      setError(mensajeError(err));
    } finally {
      setEmitiendo(false);
    }
  };

  const cargar = () => {
    setCargando(true);
    listarConstancias(filtro || undefined)
      .then((data) => {
        setConstancias(data);
        setError('');
      })
      .catch((err) => setError(mensajeError(err)))
      .finally(() => setCargando(false));
  };

  useEffect(() => {
    cargar();
  }, [filtro]);

  const abrir = (item, accion) => {
    setSeleccionada(item);
    setModo(accion);
    setFolio('');
    setMotivo('');
    setError('');
  };

  const guardar = async (event) => {
    event.preventDefault();
    setGuardando(true);
    setError('');
    try {
      if (modo === 'autorizar') {
        const folioNormalizado = folio.trim().toUpperCase();
        await autorizarConstancia(seleccionada.id, folioNormalizado);
        setExito(`Constancia emitida con el folio ${folioNormalizado}.`);
      } else {
        await rechazarConstancia(seleccionada.id, motivo.trim());
        setExito(`Solicitud de ${seleccionada.nombreAlumno} rechazada y notificada.`);
      }
      setSeleccionada(null);
      cargar();
    } catch (err) {
      setError(mensajeError(err));
    } finally {
      setGuardando(false);
    }
  };

  const ver = (item) => verDocumento({
    titulo: `Constancia de acreditación · ${item.nombreAlumno} · folio ${item.folio}`,
    nombre: `constancia-${item.folio}`,
    cargar: () => descargarPdfConstancia(item.id),
  });

  return (
    <div className="grid gap-5">
      <div>
        <h1 className="flex items-center gap-2 text-xl font-bold"><Award className="h-5 w-5" /> Constancias</h1>
        <p className="mt-1 text-sm text-tenue">{soloLectura ? 'Consulta las solicitudes y constancias de acreditación de inglés.' : 'Revisa, emite o rechaza las constancias de acreditación de inglés.'}</p>
      </div>

      {error && <Alerta tipo="error">{error}</Alerta>}
      {exito && <Alerta tipo="ok">{exito}</Alerta>}

      {soloLectura && <Alerta tipo="info">Consulta de solo lectura: las operaciones las realiza la coordinación.</Alerta>}
      {!soloLectura && <Tarjeta titulo="Emitir constancia sin solicitud del alumno" icono={FilePlus2}>
        <form className="grid items-end gap-3 sm:grid-cols-[1fr_1fr_auto]" onSubmit={emitirDirecta}>
          <Campo etiqueta="Número de control" id="ncDirecta" ayuda="Debe haber acreditado o exentado los diez niveles.">
            <input id="ncDirecta" list="alumnos-acreditados" required className={claseEntrada} value={directa.numeroControl}
              onChange={(e) => setDirecta({ ...directa, numeroControl: e.target.value.toUpperCase() })} />
            <datalist id="alumnos-acreditados">
              {alumnosAcreditados.map((a) => <option key={a.numeroControl} value={a.numeroControl}>{a.nombreCompleto}</option>)}
            </datalist>
          </Campo>
          <Campo etiqueta="Folio único" id="folioDirecta" ayuda="Solo letras mayúsculas y números.">
            <input id="folioDirecta" required maxLength="20" pattern="[A-Za-z0-9]+" className={claseEntrada} value={directa.folio}
              onChange={(e) => setDirecta({ ...directa, folio: e.target.value.toUpperCase() })} />
          </Campo>
          <Boton type="submit" disabled={emitiendo} className="mb-5"><Award className="h-4 w-4" /> {emitiendo ? 'Emitiendo…' : 'Crear y emitir'}</Boton>
        </form>
      </Tarjeta>}

      <Tarjeta titulo="Solicitudes y constancias">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <label className="text-xs font-semibold" htmlFor="filtro-estatus">Mostrar</label>
          <select id="filtro-estatus" className={`${claseEntrada} max-w-xs`} value={filtro} onChange={(e) => setFiltro(e.target.value)}>
            <option value="">Todas</option>
            <option value="Solicitada">Pendientes</option>
            <option value="Emitida">Emitidas</option>
            <option value="Rechazada">Rechazadas</option>
          </select>
        </div>
        {cargando ? <Cargando /> : constancias.length === 0 ? (
          <p className="py-6 text-center text-sm text-tenue">No hay constancias para este filtro.</p>
        ) : (
          <div className="grid gap-3">
            {constancias.map((item) => (
              <article key={item.id} className="rounded-lg border border-linea p-3">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <h2 className="font-semibold">{item.nombreAlumno}</h2>
                    <p className="text-xs text-tenue">
                      {item.numeroControl} · {item.carrera} · Solicitud: {fecha(item.fechaSolicitud)}
                    </p>
                  </div>
                  <Insignia tono={item.estatus === 'Emitida' ? 'ok' : item.estatus === 'Rechazada' ? 'error' : 'aviso'}>
                    {item.estatus}
                  </Insignia>
                </div>
                <p className="mt-2 text-sm">
                  {item.idioma} · {item.nivelesAcreditados} niveles · {item.totalHoras} horas · MCER {item.nivelMcer}
                </p>
                {item.estatus === 'Solicitada' ? (soloLectura ? <p className="mt-2 text-sm text-tenue">Solicitud en espera de que la coordinación la atienda.</p> :
                  <div className="mt-3 flex flex-wrap gap-2">
                    <Boton onClick={() => abrir(item, 'autorizar')}>
                      <CheckCircle2 className="h-4 w-4" /> Emitir constancia
                    </Boton>
                    <Boton variante="peligro" onClick={() => abrir(item, 'rechazar')}>
                      <XCircle className="h-4 w-4" /> Rechazar
                    </Boton>
                  </div>
                ) : item.estatus === 'Emitida' ? (
                  <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-sm">
                    <span>Folio {item.folio} · Fecha de emisión: {fecha(item.fechaExpedicion)}</span>
                    <Boton variante="secundario" onClick={() => ver(item)}>
                      <Eye className="h-4 w-4" /> Ver PDF
                    </Boton>
                  </div>
                ) : (
                  <p className="mt-2 text-sm text-error">Motivo: {item.motivoRechazo}</p>
                )}
              </article>
            ))}
          </div>
        )}
      </Tarjeta>

      {seleccionada && (
        <Tarjeta titulo={modo === 'autorizar' ? 'Emitir constancia' : 'Rechazar solicitud'}>
          <form className="grid gap-4" onSubmit={guardar}>
            <p className="text-sm text-tenue">
              {seleccionada.nombreAlumno} · {seleccionada.numeroControl} · {seleccionada.idioma}
            </p>
            {modo === 'autorizar' ? (
              <Campo etiqueta="Folio único" id="folio" ayuda="Usa únicamente letras mayúsculas y números (máximo 20).">
                <input
                  id="folio"
                  className={claseEntrada}
                  value={folio}
                  onChange={(event) => setFolio(event.target.value.toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 20))}
                  required
                  autoComplete="off"
                  autoFocus
                />
              </Campo>
            ) : (
              <Campo etiqueta="Motivo del rechazo" id="motivo">
                <textarea
                  id="motivo"
                  className={claseEntrada}
                  value={motivo}
                  onChange={(event) => setMotivo(event.target.value.slice(0, 200))}
                  rows={3}
                  required
                  maxLength={200}
                  autoFocus
                />
              </Campo>
            )}
            <div className="flex flex-wrap gap-2">
              <Boton type="submit" disabled={guardando}>
                {guardando ? 'Procesando…' : modo === 'autorizar' ? 'Generar PDF y emitir' : 'Rechazar y notificar'}
              </Boton>
              <Boton variante="secundario" type="button" onClick={() => setSeleccionada(null)} disabled={guardando}>
                Cancelar
              </Boton>
            </div>
          </form>
        </Tarjeta>
      )}
      <ConstanciasAvance />
    </div>
  );
}
