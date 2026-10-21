import { useCallback, useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { ClipboardCheck, Eye, Layers } from 'lucide-react';
import { mensajeError } from '../../api/cliente';
import { obtenerPeriodos } from '../../api/catalogos';
import { cerrarActa, descargarActaPdf, guardarCalificaciones, listarGrupos, obtenerActa } from '../../api/grupos';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';
import { useVisorDocumento } from '../../components/VisorDocumento';

const TONO_PAGO = { Liquidada: 'ok', Parcial: 'aviso', Pendiente: 'error', 'Sin cargo': 'neutro' };

const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];

function fechaLocal() {
  const hoy = new Date();
  return `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
}

/** Actas de calificaciones del facilitador (CU-10): captura, cierre y descarga en PDF. */
export default function GruposFacilitador() {
  const verDocumento = useVisorDocumento();
  const [params, setParams] = useSearchParams();
  const [periodos, setPeriodos] = useState([]);
  const [periodoId, setPeriodoId] = useState('');
  const [grupos, setGrupos] = useState([]);
  const [acta, setActa] = useState(null);
  const [calificaciones, setCalificaciones] = useState({});
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [confirmarCierre, setConfirmarCierre] = useState(false);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');

  const cargarGrupos = useCallback(async () => {
    try {
      setError('');
      setGrupos(await listarGrupos(periodoId || undefined));
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setCargando(false);
    }
  }, [periodoId]);

  useEffect(() => {
    obtenerPeriodos().then((datos) => {
      setPeriodos(datos);
      const actual = fechaLocal();
      const vigente = datos.find((p) => p.fechaInicio <= actual && p.fechaFin >= actual);
      setPeriodoId(String(vigente?.id ?? datos[0]?.id ?? ''));
    }).catch((e) => setError(mensajeError(e)));
  }, []);

  useEffect(() => {
    // Al cambiar de periodo se cierra el acta abierta, salvo que sea de ese mismo periodo (enlace directo).
    setActa((previa) => (previa && String(previa.grupo.idPeriodo) === periodoId ? previa : null));
    cargarGrupos();
  }, [cargarGrupos, periodoId]);

  // Enlaces desde Inicio o Mis grupos: /facilitador/actas?grupo=ID abre esa acta.
  useEffect(() => {
    const id = params.get('grupo');
    if (id && acta?.grupo.id !== Number(id)) abrirActa(Number(id));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [params]);

  function pdf() {
    verDocumento({
      titulo: `Acta de calificaciones · ${acta.grupo.nivel} ${acta.grupo.clave} · ${acta.grupo.periodo}`,
      nombre: `acta-${acta.grupo.nivel.replace(/\s+/g, '-')}-${acta.grupo.clave}`,
      cargar: () => descargarActaPdf(acta.grupo.id),
    });
  }

  async function abrirActa(id) {
    try {
      setError('');
      setAviso('');
      setConfirmarCierre(false);
      const resultado = await obtenerActa(id);
      setActa(resultado);
      if (resultado.grupo.idPeriodo && String(resultado.grupo.idPeriodo) !== periodoId) setPeriodoId(String(resultado.grupo.idPeriodo));
      setCalificaciones(Object.fromEntries(resultado.alumnos.map((a) => [a.numeroControl, a.calificacionFinal ?? ''])));
    } catch (e) {
      setError(mensajeError(e));
    }
  }

  function cambiarCalificacion(numeroControl, valor) {
    setCalificaciones((previo) => ({ ...previo, [numeroControl]: valor }));
  }

  async function guardar(evento) {
    evento.preventDefault();
    if (!acta) return;
    setGuardando(true);
    setError('');
    setAviso('');
    try {
      const actualizada = await guardarCalificaciones(acta.grupo.id, acta.alumnos.map((alumno) => ({
        numeroControl: alumno.numeroControl,
        calificacion: calificaciones[alumno.numeroControl] === '' ? null : Number(calificaciones[alumno.numeroControl]),
      })));
      setActa(actualizada);
      setAviso('Las calificaciones quedaron guardadas. El acta permanece abierta hasta que la cierres.');
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando(false);
    }
  }

  async function cerrar() {
    if (!acta) return;
    setGuardando(true);
    setError('');
    setAviso('');
    try {
      const calificacionesGuardadas = await guardarCalificaciones(acta.grupo.id, acta.alumnos.map((alumno) => ({
        numeroControl: alumno.numeroControl,
        calificacion: Number(calificaciones[alumno.numeroControl]),
      })));
      setActa(calificacionesGuardadas);
      const cerrada = await cerrarActa(acta.grupo.id);
      setActa(cerrada);
      setConfirmarCierre(false);
      setAviso('Acta cerrada. Las calificaciones y el avance de nivel ya no pueden modificarse.');
      await cargarGrupos();
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando(false);
    }
  }

  const todasCapturadas = acta?.alumnos.length > 0
    && acta.alumnos.every((alumno) => calificaciones[alumno.numeroControl] !== ''
      && Number.isFinite(Number(calificaciones[alumno.numeroControl]))
      && Number(calificaciones[alumno.numeroControl]) >= 0
      && Number(calificaciones[alumno.numeroControl]) <= 100);
  const sinPago = acta ? acta.alumnos.filter((alumno) => alumno.estatusPago !== 'Liquidada') : [];
  const capturadas = acta ? acta.alumnos.filter((alumno) => calificaciones[alumno.numeroControl] !== '').length : 0;
  const periodoEditable = acta && fechaLocal() >= acta.grupo.fechaInicioPeriodo
    && fechaLocal() <= acta.grupo.fechaFinPeriodo;

  return (
    <div className="grid grid-cols-1 gap-[18px] xl:grid-cols-[0.8fr_1.2fr]">
      {error && <Alerta tipo="error" className="xl:col-span-2">{error}</Alerta>}
      <Tarjeta titulo="Mis grupos" icono={Layers}>
        <Campo etiqueta="Periodo" id="periodo">
          <select className={claseEntrada} id="periodo" value={periodoId} onChange={(e) => { setPeriodoId(e.target.value); setParams({}); }}>
            {periodos.map((periodo) => <option key={periodo.id} value={periodo.id}>{periodo.nombre}</option>)}
          </select>
        </Campo>
        {cargando ? <Cargando /> : grupos.length === 0 ? (
          <p className="mt-4 text-sm text-tenue">No tienes grupos asignados para este periodo.</p>
        ) : (
          <div className="mt-3 grid gap-2">
            {grupos.map((grupo) => (
              <button
                className={`rounded-lg border p-3 text-left transition hover:border-marino/40 ${acta?.grupo.id === grupo.id ? 'border-marino bg-info-fondo' : 'border-linea'}`}
                key={grupo.id}
                type="button"
                onClick={() => setParams({ grupo: String(grupo.id) })}
              >
                <span className="flex items-center justify-between gap-2 font-semibold">{grupo.nivel} · {grupo.clave}<span className="text-xs font-normal">{grupo.estatusActa}</span></span>
                <span className="mt-1 block text-xs text-tenue">{grupo.modalidad} · {grupo.aula} · {grupo.alumnosInscritos}/{grupo.cupo} alumnos</span>
                <span className="mt-1 block text-xs">{grupo.horarios.map((h) => `${DIAS[h.diaSemana - 1]} ${h.horaInicio.slice(0, 5)}–${h.horaFin.slice(0, 5)}`).join(' · ')}</span>
              </button>
            ))}
          </div>
        )}
      </Tarjeta>

      <Tarjeta titulo={acta ? `Acta · ${acta.grupo.nivel} ${acta.grupo.clave}` : 'Acta de calificaciones'} icono={ClipboardCheck}>
        {!acta ? <p className="text-sm text-tenue">Selecciona un grupo para consultar su lista y capturar las calificaciones finales.</p>
          : <>
            {aviso && <Alerta tipo="ok" className="mb-3">{aviso}</Alerta>}
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <p className="text-xs text-tenue">
                {acta.grupo.periodo} · {acta.alumnos.length} alumnos · {capturadas} calificaciones capturadas · Estado: {acta.grupo.estatusActa}
              </p>
              <Boton variante="secundario" type="button" onClick={pdf}>
                <Eye className="h-4 w-4" /> {acta.grupo.estatusActa === 'Cerrada' ? 'Ver acta (PDF)' : 'Ver acta preliminar (PDF)'}
              </Boton>
            </div>
            {acta.grupo.estatusActa !== 'Cerrada' && sinPago.length > 0 && (
              <Alerta tipo="aviso" className="mb-3">
                Puedes capturar calificaciones, pero el acta no se podrá cerrar mientras {sinPago.length === 1 ? 'este alumno' : `estos ${sinPago.length} alumnos`} no
                {sinPago.length === 1 ? ' liquide' : ' liquiden'} el curso: {sinPago.slice(0, 5).map((a) => a.nombreCompleto).join(', ')}{sinPago.length > 5 ? ` y ${sinPago.length - 5} más` : ''}. Avisa a coordinación.
              </Alerta>
            )}
            {!periodoEditable && <Alerta tipo="info" className="mb-3">Este periodo está fuera de vigencia; el acta se muestra en modo de consulta.</Alerta>}
            {acta.alumnos.length === 0 ? (
              <p className="text-sm text-tenue">Aún no hay alumnos inscritos en este grupo.</p>
            ) : (
              <form onSubmit={guardar}>
                <div className="overflow-x-auto">
                  <table className="w-full min-w-[420px] text-left text-sm">
                    <thead className="border-b border-linea text-xs text-tenue">
                      <tr><th className="px-2 py-2">Número de control</th><th className="px-2 py-2">Alumno</th><th className="px-2 py-2">Pago</th><th className="w-32 px-2 py-2">Calificación (0–100)</th></tr>
                    </thead>
                    <tbody>
                      {acta.alumnos.map((alumno) => (
                        <tr className="border-b border-linea last:border-0" key={alumno.numeroControl}>
                          <td className="px-2 py-2">{alumno.numeroControl}</td>
                          <td className="px-2 py-2">{alumno.nombreCompleto}</td>
                          <td className="px-2 py-2"><Insignia tono={TONO_PAGO[alumno.estatusPago] || 'neutro'}>{alumno.estatusPago}</Insignia></td>
                          <td className="px-2 py-2">
                            <input
                              aria-label={`Calificación de ${alumno.nombreCompleto}`}
                              className={`${claseEntrada} w-24`}
                              disabled={!periodoEditable || acta.grupo.estatusActa === 'Cerrada'}
                              max="100"
                              min="0"
                              type="number"
                              value={calificaciones[alumno.numeroControl]}
                              onChange={(e) => cambiarCalificacion(alumno.numeroControl, e.target.value)}
                            />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
                {periodoEditable && acta.grupo.estatusActa !== 'Cerrada' && (
                  <div className="mt-4 flex flex-wrap gap-2">
                    <Boton type="submit" disabled={guardando}>{guardando ? 'Guardando…' : 'Guardar calificaciones'}</Boton>
                    {!confirmarCierre
                      ? <Boton variante="exito" type="button" disabled={guardando || !todasCapturadas || sinPago.length > 0} title={sinPago.length > 0 ? 'Hay alumnos sin pago liquidado' : undefined} onClick={() => setConfirmarCierre(true)}>Cerrar acta</Boton>
                      : <><span className="self-center text-xs text-tenue">Al cerrar se registra el avance de nivel de cada alumno y ya no podrás editar el acta.</span><Boton variante="peligro" type="button" disabled={guardando} onClick={cerrar}>Confirmar cierre</Boton><Boton variante="secundario" type="button" onClick={() => setConfirmarCierre(false)}>Cancelar</Boton></>}
                  </div>
                )}
              </form>
            )}
          </>}
      </Tarjeta>
    </div>
  );
}
