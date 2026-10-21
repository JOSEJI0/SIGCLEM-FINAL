import { useCallback, useEffect, useMemo, useState } from 'react';
import { Calendar, ClipboardList, Clock, Edit3, PenLine, Plus, Search, Trash2, User } from 'lucide-react';
import {
  capturarResultadoExamen, editarExamen, listarExamenes, ocultarExamen, programarExamen, registrarExamen,
} from '../../api/examenes';
import { registrarDiagnosticoDirecto } from '../../api/diagnosticos';
import { buscarAlumnos } from '../../api/alumnos';
import { obtenerNiveles } from '../../api/catalogos';
import { mensajeError } from '../../api/cliente';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';
import { ConfirmacionDoble, Modal } from '../../components/Modal';

const TIPOS_EXAMEN = ['Colocación', 'Cuatro habilidades'];

function formatearFecha(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleString('es-MX', { dateStyle: 'medium', timeStyle: 'short' });
}

/** Valor para un input datetime-local a partir de una fecha ISO (o ahora). */
function aLocal(iso) {
  const d = iso ? new Date(iso) : new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

/** Explica cómo afectará el resultado al avance del alumno, según el tipo de examen. */
function efectoResultado(tipo, nivel) {
  if (!nivel) return null;
  return tipo === 'Colocación'
    ? `Los niveles anteriores a ${nivel.nombre} quedarán exentos y ${nivel.nombre} quedará habilitado para cursarse.`
    : `Se acreditan (exentos) todos los niveles hasta ${nivel.nombre} inclusive.`;
}

/**
 * Exámenes de colocación y cuatro habilidades: programación, captura o corrección del resultado asignando
 * directamente el nivel (sin rúbrica ni firma), edición, borrado lógico con doble confirmación y registro
 * directo de resultados (incluida la evaluación diagnóstica). Con {@code soloLectura} (jefa del DGTV) solo se consulta.
 */
export default function DiagnosticosExamenes({ soloLectura = false }) {
  const [examenes, setExamenes] = useState([]);
  const [niveles, setNiveles] = useState([]);
  const [alumnos, setAlumnos] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [filtro, setFiltro] = useState('todos');
  const [texto, setTexto] = useState('');
  const [error, setError] = useState('');
  const [exito, setExito] = useState('');

  const [programar, setProgramar] = useState(null);
  const [resultado, setResultado] = useState(null);
  const [edicion, setEdicion] = useState(null);
  const [borrar, setBorrar] = useState(null);
  const [registro, setRegistro] = useState(null);
  const [errorModal, setErrorModal] = useState('');
  const [guardando, setGuardando] = useState(false);

  const cargar = useCallback(() => {
    setCargando(true);
    Promise.all([listarExamenes(), obtenerNiveles()])
      .then(([ex, niv]) => { setExamenes(ex); setNiveles(niv); setError(''); })
      .catch((e) => setError(mensajeError(e)))
      .finally(() => setCargando(false));
  }, []);
  useEffect(() => { cargar(); }, [cargar]);
  useEffect(() => {
    buscarAlumnos({ tamano: 100 }).then((p) => setAlumnos(p.content)).catch(() => {});
  }, []);

  const nivelPorId = useMemo(() => Object.fromEntries(niveles.map((n) => [String(n.id), n])), [niveles]);

  const guardar = async (accion, mensaje) => {
    setGuardando(true);
    setErrorModal('');
    try {
      await accion();
      setProgramar(null); setResultado(null); setEdicion(null); setRegistro(null);
      setExito(mensaje);
      cargar();
    } catch (e) {
      setErrorModal(mensajeError(e));
    } finally {
      setGuardando(false);
    }
  };

  const abrir = (setter, valor) => { setErrorModal(''); setExito(''); setter(valor); };

  const visibles = examenes
    .filter((ex) => (filtro === 'pendientes' ? !ex.concluido : filtro === 'concluidos' ? ex.concluido : true))
    .filter((ex) => {
      const t = texto.trim().toLowerCase();
      return !t || ex.nombreAlumno.toLowerCase().includes(t) || ex.numeroControl.toLowerCase().includes(t);
    });

  return (
    <div className="grid gap-[18px]">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-xl font-bold">Exámenes de colocación y cuatro habilidades</h2>
          <p className="text-[13px] text-tenue">{soloLectura ? 'Consulta los exámenes aplicados y el nivel asignado a cada alumno.' : 'El resultado se captura asignando directamente el nivel y puede corregirse en cualquier momento.'}</p>
        </div>
        {!soloLectura && <Boton onClick={() => abrir(setRegistro, {
          tipo: 'Colocación', numeroControl: '', fecha: aLocal(), idNivel: '', puntaje: '', observaciones: '', recomendacion: 'Básico 1',
        })}>
          <Plus className="h-4 w-4" /> Registrar resultado
        </Boton>}
      </div>

      {soloLectura && <Alerta tipo="info">Consulta de solo lectura: las operaciones las realiza la coordinación.</Alerta>}
      {error && <Alerta tipo="error">{error}</Alerta>}
      {exito && <Alerta tipo="ok">{exito}</Alerta>}

      <div className="flex flex-wrap items-center gap-3">
        <div className="inline-flex rounded-lg border border-linea bg-white p-0.5 text-xs">
          {[['todos', `Todos (${examenes.length})`], ['pendientes', `Pendientes (${examenes.filter((x) => !x.concluido).length})`],
            ['concluidos', `Con resultado (${examenes.filter((x) => x.concluido).length})`]].map(([v, t]) => (
            <button key={v} type="button" onClick={() => setFiltro(v)}
              className={`rounded-md px-3 py-1.5 font-semibold ${filtro === v ? 'bg-marino text-white' : 'text-tenue hover:text-tinta'}`}>{t}</button>
          ))}
        </div>
        <div className="relative min-w-[240px] flex-1 sm:max-w-xs">
          <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-tenue" />
          <input className={`${claseEntrada} pl-9`} placeholder="Buscar alumno o número de control" value={texto}
            onChange={(e) => setTexto(e.target.value)} />
        </div>
      </div>

      {cargando ? <Cargando /> : visibles.length === 0 ? (
        <Tarjeta><p className="py-6 text-center text-tenue">No hay exámenes que coincidan.</p></Tarjeta>
      ) : (
        <div className="grid gap-3">
          {visibles.map((ex) => (
            <Tarjeta key={ex.id} className="!p-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <span className="flex h-10 w-10 items-center justify-center rounded-full bg-marino/10 text-marino"><User className="h-5 w-5" /></span>
                  <div>
                    <div className="flex flex-wrap items-center gap-2 font-semibold">
                      {ex.nombreAlumno} <span className="text-xs font-normal text-tenue">{ex.numeroControl}</span>
                      <Insignia estatus={ex.concluido ? 'Concluido' : 'Programado'}>{ex.concluido ? 'Con resultado' : 'Pendiente'}</Insignia>
                    </div>
                    <div className="text-xs text-tenue">{ex.carrera} · Examen de {ex.tipo}</div>
                  </div>
                </div>
                <div className="flex flex-wrap items-center gap-2 text-xs">
                  <span className="rounded border border-linea px-2.5 py-1.5"><Calendar className="mr-1 inline h-3.5 w-3.5" />{formatearFecha(ex.fechaHoraAplicacion)}</span>
                  {ex.concluido ? (
                    <span className="rounded bg-ok-fondo px-2.5 py-1.5 font-semibold text-[#155E3D]">
                      Nivel asignado: {ex.nombreNivelAsignado}{ex.puntajeTotal != null ? ` · ${ex.puntajeTotal} pts` : ''}
                    </span>
                  ) : (
                    <span className="rounded bg-aviso-fondo px-2.5 py-1.5 text-aviso"><Clock className="mr-1 inline h-3.5 w-3.5" />Sin resultado</span>
                  )}
                </div>
              </div>
              {ex.observaciones && <p className="mt-2 text-[12.5px] text-tenue">Observaciones: {ex.observaciones}</p>}
              {!soloLectura && <div className="mt-3 flex flex-wrap justify-end gap-2 border-t border-linea pt-3">
                {!ex.concluido && (
                  <Boton variante="secundario" onClick={() => abrir(setProgramar, { ex, fecha: aLocal(ex.fechaHoraAplicacion) })}>
                    <Calendar className="h-3.5 w-3.5" /> Programar
                  </Boton>
                )}
                <Boton variante="secundario" onClick={() => abrir(setEdicion, { ex, tipo: ex.tipo, fecha: aLocal(ex.fechaHoraAplicacion) })}>
                  <PenLine className="h-3.5 w-3.5" /> Editar datos
                </Boton>
                <Boton variante="peligro" onClick={() => abrir(setBorrar, ex)}>
                  <Trash2 className="h-3.5 w-3.5" /> Borrar
                </Boton>
                <Boton onClick={() => abrir(setResultado, {
                  ex, idNivel: ex.idNivelAsignado ? String(ex.idNivelAsignado) : '', puntaje: ex.puntajeTotal ?? '', observaciones: ex.observaciones || '',
                })}>
                  <Edit3 className="h-3.5 w-3.5" /> {ex.concluido ? 'Editar resultado' : 'Capturar resultado'}
                </Boton>
              </div>}
            </Tarjeta>
          ))}
        </div>
      )}

      {programar && (
        <Modal titulo={`Programar examen · ${programar.ex.nombreAlumno}`} onCerrar={() => setProgramar(null)}>
          <form className="grid gap-4" onSubmit={(e) => {
            e.preventDefault();
            guardar(() => programarExamen(programar.ex.id, { fechaHoraAplicacion: `${programar.fecha}:00` }), 'Examen programado.');
          }}>
            {errorModal && <Alerta tipo="error">{errorModal}</Alerta>}
            <Campo etiqueta="Fecha y hora de aplicación" id="fecha-prog">
              <input id="fecha-prog" type="datetime-local" required className={claseEntrada} value={programar.fecha}
                onChange={(e) => setProgramar({ ...programar, fecha: e.target.value })} />
            </Campo>
            <div className="flex justify-end gap-2">
              <Boton type="button" variante="secundario" onClick={() => setProgramar(null)}>Cancelar</Boton>
              <Boton type="submit" disabled={guardando}>Guardar</Boton>
            </div>
          </form>
        </Modal>
      )}

      {resultado && (
        <Modal titulo={`${resultado.ex.concluido ? 'Editar' : 'Capturar'} resultado · ${resultado.ex.nombreAlumno}`} onCerrar={() => setResultado(null)}>
          <form className="grid gap-4" onSubmit={(e) => {
            e.preventDefault();
            guardar(() => capturarResultadoExamen(resultado.ex.id, {
              idNivelAsignado: Number(resultado.idNivel),
              puntaje: resultado.puntaje === '' ? null : Number(resultado.puntaje),
              observaciones: resultado.observaciones || null,
            }), `Resultado guardado: ${nivelPorId[resultado.idNivel]?.nombre} para ${resultado.ex.nombreAlumno}.`);
          }}>
            {errorModal && <Alerta tipo="error">{errorModal}</Alerta>}
            <p className="text-[13px] text-tenue">Examen de <b>{resultado.ex.tipo}</b> · {formatearFecha(resultado.ex.fechaHoraAplicacion)}</p>
            <Campo etiqueta="Nivel asignado" id="nivel-res">
              <select id="nivel-res" required className={claseEntrada} value={resultado.idNivel}
                onChange={(e) => setResultado({ ...resultado, idNivel: e.target.value })}>
                <option value="">Selecciona el nivel…</option>
                {niveles.map((n) => <option key={n.id} value={n.id}>{n.numero}. {n.nombre} ({n.nivelMcer})</option>)}
              </select>
            </Campo>
            {resultado.idNivel && <Alerta tipo="info">{efectoResultado(resultado.ex.tipo, nivelPorId[resultado.idNivel])}</Alerta>}
            <Campo etiqueta="Puntaje obtenido (opcional, 0 a 100)" id="puntaje-res">
              <input id="puntaje-res" type="number" min="0" max="100" className={claseEntrada} value={resultado.puntaje}
                onChange={(e) => setResultado({ ...resultado, puntaje: e.target.value })} />
            </Campo>
            <Campo etiqueta="Observaciones (opcional)" id="obs-res">
              <textarea id="obs-res" rows="3" className={claseEntrada} value={resultado.observaciones}
                onChange={(e) => setResultado({ ...resultado, observaciones: e.target.value })} />
            </Campo>
            <div className="flex justify-end gap-2">
              <Boton type="button" variante="secundario" onClick={() => setResultado(null)}>Cancelar</Boton>
              <Boton type="submit" disabled={guardando}>{guardando ? 'Guardando…' : 'Guardar resultado'}</Boton>
            </div>
          </form>
        </Modal>
      )}

      {edicion && (
        <Modal titulo={`Editar datos del examen · ${edicion.ex.nombreAlumno}`} onCerrar={() => setEdicion(null)}>
          <form className="grid gap-4" onSubmit={(e) => {
            e.preventDefault();
            guardar(() => editarExamen(edicion.ex.id, { tipo: edicion.tipo, fechaHoraAplicacion: `${edicion.fecha}:00` }),
              'Datos del examen actualizados.');
          }}>
            {errorModal && <Alerta tipo="error">{errorModal}</Alerta>}
            <Campo etiqueta="Tipo de examen" id="tipo-ed">
              <select id="tipo-ed" className={claseEntrada} value={edicion.tipo} onChange={(e) => setEdicion({ ...edicion, tipo: e.target.value })}>
                {TIPOS_EXAMEN.map((t) => <option key={t}>{t}</option>)}
              </select>
            </Campo>
            {edicion.ex.concluido && edicion.tipo !== edicion.ex.tipo && (
              <Alerta tipo="aviso">El examen ya tiene resultado: al cambiar el tipo se recalculará el avance del alumno.</Alerta>
            )}
            <Campo etiqueta="Fecha y hora de aplicación" id="fecha-ed">
              <input id="fecha-ed" type="datetime-local" required className={claseEntrada} value={edicion.fecha}
                onChange={(e) => setEdicion({ ...edicion, fecha: e.target.value })} />
            </Campo>
            <div className="flex justify-end gap-2">
              <Boton type="button" variante="secundario" onClick={() => setEdicion(null)}>Cancelar</Boton>
              <Boton type="submit" disabled={guardando}>Guardar cambios</Boton>
            </div>
          </form>
        </Modal>
      )}

      {borrar && (
        <ConfirmacionDoble
          titulo="Borrar examen"
          mensaje={<>Se ocultará el examen de <b>{borrar.tipo}</b> de <b>{borrar.nombreAlumno}</b>. No se elimina de la base de datos
            ni cambia el avance del alumno, pero dejará de aparecer en el sistema.</>}
          textoBoton="Borrar examen"
          onCerrar={() => setBorrar(null)}
          onConfirmar={async () => {
            try {
              await ocultarExamen(borrar.id);
              setBorrar(null);
              setExito('El examen se ocultó.');
              cargar();
            } catch (e) {
              setBorrar(null);
              setError(mensajeError(e));
            }
          }}
        />
      )}

      {registro && (
        <Modal titulo="Registrar resultado de examen o diagnóstico" onCerrar={() => setRegistro(null)}>
          <form className="grid gap-4" onSubmit={(e) => {
            e.preventDefault();
            const base = { numeroControl: registro.numeroControl.trim().toUpperCase(), fechaHoraAplicacion: `${registro.fecha}:00`,
              observaciones: registro.observaciones || null };
            const esDiagnostico = registro.tipo === 'Diagnóstico';
            guardar(() => (esDiagnostico
              ? registrarDiagnosticoDirecto({ ...base, puntaje: Number(registro.puntaje), recomendacion: registro.recomendacion })
              : registrarExamen({ ...base, tipo: registro.tipo, idNivelAsignado: Number(registro.idNivel),
                puntaje: registro.puntaje === '' ? null : Number(registro.puntaje) })),
            esDiagnostico ? 'Evaluación diagnóstica registrada (consúltala en Evaluaciones diagnósticas).' : 'Examen registrado con su resultado.');
          }}>
            {errorModal && <Alerta tipo="error">{errorModal}</Alerta>}
            <div className="grid grid-cols-3 gap-2">
              {[...TIPOS_EXAMEN, 'Diagnóstico'].map((t) => (
                <button key={t} type="button" onClick={() => setRegistro({ ...registro, tipo: t })}
                  className={`rounded-lg border px-2 py-2 text-[12.5px] font-semibold ${registro.tipo === t ? 'border-marino bg-marino/5 text-marino' : 'border-linea text-tenue'}`}>
                  {t}
                </button>
              ))}
            </div>
            <Campo etiqueta="Número de control del alumno" id="nc-reg" ayuda="Escribe o elige de la lista.">
              <input id="nc-reg" list="lista-alumnos" required className={claseEntrada} value={registro.numeroControl}
                onChange={(e) => setRegistro({ ...registro, numeroControl: e.target.value.toUpperCase() })} />
              <datalist id="lista-alumnos">
                {alumnos.map((a) => <option key={a.numeroControl} value={a.numeroControl}>{a.nombreCompleto} · {a.nivelActual}</option>)}
              </datalist>
            </Campo>
            <Campo etiqueta="Fecha y hora de aplicación" id="fecha-reg">
              <input id="fecha-reg" type="datetime-local" required className={claseEntrada} value={registro.fecha}
                onChange={(e) => setRegistro({ ...registro, fecha: e.target.value })} />
            </Campo>
            {registro.tipo === 'Diagnóstico' ? (
              <>
                <Campo etiqueta="Puntaje (0 a 100)" id="puntaje-diag">
                  <input id="puntaje-diag" type="number" min="0" max="100" required className={claseEntrada} value={registro.puntaje}
                    onChange={(e) => setRegistro({ ...registro, puntaje: e.target.value })} />
                </Campo>
                <Campo etiqueta="Recomendación" id="rec-diag" ayuda="El diagnóstico solo orienta: no asigna ni exenta niveles.">
                  <select id="rec-diag" className={claseEntrada} value={registro.recomendacion}
                    onChange={(e) => setRegistro({ ...registro, recomendacion: e.target.value })}>
                    <option>Básico 1</option><option>Colocación</option>
                  </select>
                </Campo>
              </>
            ) : (
              <>
                <Campo etiqueta="Nivel asignado" id="nivel-reg">
                  <select id="nivel-reg" required className={claseEntrada} value={registro.idNivel}
                    onChange={(e) => setRegistro({ ...registro, idNivel: e.target.value })}>
                    <option value="">Selecciona el nivel…</option>
                    {niveles.map((n) => <option key={n.id} value={n.id}>{n.numero}. {n.nombre} ({n.nivelMcer})</option>)}
                  </select>
                </Campo>
                {registro.idNivel && <Alerta tipo="info">{efectoResultado(registro.tipo, nivelPorId[registro.idNivel])}</Alerta>}
                <Campo etiqueta="Puntaje (opcional, 0 a 100)" id="puntaje-reg">
                  <input id="puntaje-reg" type="number" min="0" max="100" className={claseEntrada} value={registro.puntaje}
                    onChange={(e) => setRegistro({ ...registro, puntaje: e.target.value })} />
                </Campo>
              </>
            )}
            <Campo etiqueta="Observaciones (opcional)" id="obs-reg">
              <textarea id="obs-reg" rows="2" className={claseEntrada} value={registro.observaciones}
                onChange={(e) => setRegistro({ ...registro, observaciones: e.target.value })} />
            </Campo>
            <div className="flex justify-end gap-2">
              <Boton type="button" variante="secundario" onClick={() => setRegistro(null)}>Cancelar</Boton>
              <Boton type="submit" disabled={guardando}><ClipboardList className="h-4 w-4" /> {guardando ? 'Guardando…' : 'Registrar'}</Boton>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}
