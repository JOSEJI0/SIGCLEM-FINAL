import { useEffect, useState } from 'react';
import { Calendar, CheckCircle2, ClipboardCheck, Edit3 } from 'lucide-react';
import {
  listarDiagnosticos, listarNotificacionesRecibidas, programarDiagnostico, registrarResultadoDiagnostico,
} from '../../api/diagnosticos';
import { mensajeError } from '../../api/cliente';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';

function formatearFecha(fecha) {
  if (!fecha) return 'Sin programar';
  return new Date(fecha).toLocaleString('es-MX', { dateStyle: 'medium', timeStyle: 'short' });
}

/** Con {@code soloLectura} (jefa del DGTV) solo se consultan las evaluaciones. */
export default function EvaluacionesDiagnosticas({ soloLectura = false }) {
  const [items, setItems] = useState([]);
  const [notificaciones, setNotificaciones] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [exito, setExito] = useState('');
  const [seleccionado, setSeleccionado] = useState(null);
  const [modo, setModo] = useState('');
  const [fecha, setFecha] = useState('');
  const [enlace, setEnlace] = useState('');
  const [puntaje, setPuntaje] = useState('');
  const [recomendacion, setRecomendacion] = useState('Básico 1');
  const [observaciones, setObservaciones] = useState('');
  const [guardando, setGuardando] = useState(false);

  const cargar = () => {
    setCargando(true);
    Promise.all([listarDiagnosticos(), soloLectura ? Promise.resolve([]) : listarNotificacionesRecibidas()])
      .then(([data, recibidas]) => {
        setItems(data);
        setNotificaciones(recibidas.filter((item) => item.asunto.includes('diagnóstica')));
        setError('');
      })
      .catch((err) => setError(mensajeError(err)))
      .finally(() => setCargando(false));
  };

  useEffect(() => {
    cargar();
  }, []);

  const abrir = (item, accion) => {
    setSeleccionado(item);
    setModo(accion);
    setFecha(item.fechaHoraAplicacion ? item.fechaHoraAplicacion.slice(0, 16) : '');
    setEnlace(item.enlace || '');
    setPuntaje(item.puntaje ?? '');
    setRecomendacion(item.recomendacion || 'Básico 1');
    setObservaciones(item.observaciones || '');
    setError('');
  };

  const guardar = async (event) => {
    event.preventDefault();
    setGuardando(true);
    setError('');
    try {
      if (modo === 'programar') {
        await programarDiagnostico(seleccionado.id, { fechaHoraAplicacion: fecha, enlace });
        setExito(`Evaluación de ${seleccionado.nombreAlumno} programada.`);
      } else {
        await registrarResultadoDiagnostico(seleccionado.id, {
          puntaje: Number(puntaje), recomendacion, observaciones: observaciones || null,
        });
        setExito(`Resultado de ${seleccionado.nombreAlumno} registrado.`);
      }
      setSeleccionado(null);
      cargar();
    } catch (err) {
      setError(mensajeError(err));
    } finally {
      setGuardando(false);
    }
  };

  return (
    <div className="grid gap-5">
      <div>
        <h1 className="text-xl font-bold">Evaluaciones de diagnóstico</h1>
        <p className="text-xs text-tenue">{soloLectura ? 'Consulta las solicitudes, su programación y sus resultados.' : 'Programa las solicitudes y registra los resultados de evaluación.'}</p>
      </div>
      {soloLectura && <Alerta tipo="info">Consulta de solo lectura: las operaciones las realiza la coordinación.</Alerta>}
      {exito && <Alerta tipo="ok">{exito}</Alerta>}
      {error && !seleccionado && <Alerta tipo="error">{error}</Alerta>}
      {notificaciones.length > 0 && (
        <Tarjeta titulo="Notificaciones de solicitudes diagnósticas" icono={ClipboardCheck}>
          <div className="grid gap-3">
            {notificaciones.map((item) => (
              <div key={item.id} className="rounded-lg border border-linea p-3">
                <div className="flex flex-wrap justify-between gap-2">
                  <b className="text-sm">{item.asunto}</b>
                  <span className="text-xs text-tenue">
                    {new Date(item.fechaEnvio).toLocaleString('es-MX', { dateStyle: 'medium', timeStyle: 'short' })}
                  </span>
                </div>
                <p className="mt-1 whitespace-pre-wrap text-xs text-tenue">{item.mensaje}</p>
              </div>
            ))}
          </div>
        </Tarjeta>
      )}
      {cargando ? <Cargando /> : items.length === 0 ? (
        <Tarjeta><p className="py-6 text-center text-tenue">No hay solicitudes diagnósticas.</p></Tarjeta>
      ) : (
        <div className="grid gap-3">
          {items.map((item) => (
            <Tarjeta key={item.id}>
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <ClipboardCheck className="h-5 w-5 text-marino" />
                  <div>
                    <p className="font-bold text-sm">{item.nombreAlumno}</p>
                    <p className="text-xs text-tenue">{item.numeroControl} · Solicitud #{item.id}</p>
                  </div>
                  <Insignia estatus={item.estatus} />
                </div>
                <div className="flex flex-wrap items-center gap-2">
                  <span className="flex items-center gap-1 text-xs text-tenue">
                    <Calendar className="h-3.5 w-3.5" /> {formatearFecha(item.fechaHoraAplicacion)}
                  </span>
                  {!soloLectura && item.estatus !== 'Concluida' && (
                    <>
                      <Boton variante="secundario" onClick={() => abrir(item, 'programar')}>
                        <Edit3 className="h-3.5 w-3.5" /> Programar
                      </Boton>
                      {item.estatus === 'Programada' && (
                        <Boton onClick={() => abrir(item, 'resultado')}>
                          <CheckCircle2 className="h-3.5 w-3.5" /> Capturar resultado
                        </Boton>
                      )}
                    </>
                  )}
                </div>
              </div>
              {item.estatus === 'Concluida' && (
                <div className="mt-3 flex flex-wrap items-center justify-between gap-2 rounded-lg bg-fondo p-3 text-xs">
                  <span>
                    {item.puntaje}/100 · <b>{item.recomendacion}</b>
                    {item.observaciones ? ` — ${item.observaciones}` : ''}
                  </span>
                  {!soloLectura && <Boton variante="secundario" onClick={() => abrir(item, 'resultado')}>
                    <Edit3 className="h-3.5 w-3.5" /> Editar resultado
                  </Boton>}
                </div>
              )}
            </Tarjeta>
          ))}
        </div>
      )}

      {seleccionado && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-lg rounded-2xl border border-linea bg-white p-6 shadow-xl">
            <h2 className="text-lg font-bold">
              {modo === 'programar' ? 'Programar evaluación'
                : seleccionado.estatus === 'Concluida' ? 'Editar resultado' : 'Registrar resultado'}
            </h2>
            <p className="mt-1 text-xs text-tenue">{seleccionado.nombreAlumno} · {seleccionado.numeroControl}</p>
            {error && <div className="mt-3"><Alerta tipo="error">{error}</Alerta></div>}
            <form onSubmit={guardar} className="mt-4 grid gap-3">
              {modo === 'programar' ? (
                <>
                  <Campo etiqueta="Fecha y hora" id="fechaDiagnostico">
                    <input id="fechaDiagnostico" type="datetime-local" required className={claseEntrada}
                      value={fecha} onChange={(event) => setFecha(event.target.value)} />
                  </Campo>
                  <Campo etiqueta="Enlace de evaluación" id="enlaceDiagnostico">
                    <input id="enlaceDiagnostico" type="url" required maxLength={255} className={claseEntrada}
                      value={enlace} onChange={(event) => setEnlace(event.target.value)}
                      placeholder="https://…" />
                  </Campo>
                </>
              ) : (
                <>
                  <Campo etiqueta="Puntaje (0 a 100)" id="puntajeDiagnostico">
                    <input id="puntajeDiagnostico" type="number" min="0" max="100" required className={claseEntrada}
                      value={puntaje} onChange={(event) => setPuntaje(event.target.value)} />
                  </Campo>
                  <Campo etiqueta="Recomendación" id="recomendacionDiagnostico">
                    <select id="recomendacionDiagnostico" className={claseEntrada} value={recomendacion}
                      onChange={(event) => setRecomendacion(event.target.value)}>
                      <option value="Básico 1">Básico 1</option>
                      <option value="Colocación">Colocación</option>
                    </select>
                  </Campo>
                  <Campo etiqueta="Observaciones (opcional)" id="observacionesDiagnostico">
                    <textarea id="observacionesDiagnostico" maxLength={10000} className={claseEntrada}
                      value={observaciones} onChange={(event) => setObservaciones(event.target.value)} />
                  </Campo>
                </>
              )}
              <div className="mt-2 flex justify-end gap-2">
                <Boton variante="secundario" type="button" disabled={guardando}
                  onClick={() => setSeleccionado(null)}>Cancelar</Boton>
                <Boton type="submit" disabled={guardando}>
                  {guardando ? 'Guardando…' : 'Guardar'}
                </Boton>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
