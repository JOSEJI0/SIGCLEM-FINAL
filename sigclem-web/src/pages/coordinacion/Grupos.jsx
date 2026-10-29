import { useAuth } from '../../auth/AuthContext';
import { esSoloConsulta } from '../../auth/roles';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  CalendarDays, ChevronDown, LayoutGrid, Lock, NotebookPen, Pencil, Plus, Trash2, UserMinus, UserPlus, Users, X, Eye,
} from 'lucide-react';
import { listarFacilitadores } from '../../api/facilitadores';
import { crearAula, crearPeriodo, obtenerAulas, obtenerModalidades, obtenerNiveles, obtenerPeriodos } from '../../api/catalogos';
import {
  abrirGrupo, alumnosDelGrupo, asignarAlumno, asignarNivelPendiente, cerrarActaCoordinacion, editarGrupo,
  eliminarGrupo, listarGrupos, quitarAlumno, descargarActaPdf,
} from '../../api/grupos';
import { buscarAlumnos } from '../../api/alumnos';
import { mensajeError } from '../../api/cliente';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';
import { ConfirmacionDoble, Modal } from '../../components/Modal';
import CalendarioSemanal, { DIAS, colorNivel, horaCorta, minutos } from '../../components/CalendarioSemanal';
import { useVisorDocumento } from '../../components/VisorDocumento';

const pesos = (v) => (v == null ? '' : Number(v).toLocaleString('es-MX', { style: 'currency', currency: 'MXN' }));
const DURACIONES = [[60, '1 h'], [90, '1 h 30'], [120, '2 h'], [180, '3 h'], [240, '4 h']];

function fechaLocal() {
  const hoy = new Date();
  return `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
}
const aHora = (min) => `${String(Math.floor(min / 60)).padStart(2, '0')}:${String(min % 60).padStart(2, '0')}`;

/** Grupos y horarios: calendario del periodo, apertura/edición con calendario interactivo, borrado y alumnos. */
export default function Grupos({ soloLectura = false }) {
  const { perfil } = useAuth();
  const esSoloLectura = soloLectura || esSoloConsulta(perfil?.rol);
  const [catalogos, setCatalogos] = useState({ niveles: [], periodos: [], modalidades: [], aulas: [], facilitadores: [] });
  const [idPeriodo, setIdPeriodo] = useState('');
  const [grupos, setGrupos] = useState([]);
  const [vista, setVista] = useState('calendario');
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [editor, setEditor] = useState(null);
  const [panelAlumnos, setPanelAlumnos] = useState(null);
  const [borrar, setBorrar] = useState(null);
  const [verCatalogos, setVerCatalogos] = useState(false);

  const cargarCatalogos = useCallback(async () => {
    try {
      if (esSoloLectura) {
        const periodos = await obtenerPeriodos();
        setCatalogos((actuales) => ({ ...actuales, periodos }));
        setIdPeriodo((previo) => previo || String(periodos.find((p) => p.fechaFin >= fechaLocal())?.id ?? periodos[0]?.id ?? ''));
        return;
      }
      const [niveles, periodos, modalidades, aulas, facilitadores] = await Promise.all([
        obtenerNiveles(), obtenerPeriodos(), obtenerModalidades(), obtenerAulas(), listarFacilitadores(),
      ]);
      setCatalogos({ niveles, periodos, modalidades, aulas, facilitadores });
      setIdPeriodo((previo) => previo || String(periodos.find((p) => p.fechaFin >= fechaLocal())?.id ?? periodos[0]?.id ?? ''));
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setCargando(false);
    }
  }, [esSoloLectura]);

  const cargarGrupos = useCallback(async () => {
    if (!idPeriodo) return;
    try {
      setGrupos(await listarGrupos(idPeriodo));
    } catch (e) {
      setError(mensajeError(e));
    }
  }, [idPeriodo]);

  useEffect(() => { cargarCatalogos(); }, [cargarCatalogos]);
  useEffect(() => { cargarGrupos(); }, [cargarGrupos]);

  const nuevoGrupo = () => setEditor({
    id: null, clave: '', cupo: '30', idPeriodo,
    idNivel: String(catalogos.niveles[0]?.id ?? ''), idModalidad: String(catalogos.modalidades[0]?.id ?? ''),
    idAula: String(catalogos.aulas[0]?.id ?? ''), idFacilitador: String(catalogos.facilitadores.find((f) => f.activo)?.id ?? ''),
    horarios: [],
  });
  const editarExistente = (g) => setEditor({
    id: g.id, clave: g.clave, cupo: String(g.cupo), idPeriodo: String(g.idPeriodo), idNivel: String(g.idNivel),
    idModalidad: String(g.idModalidad), idAula: String(g.idAula), idFacilitador: String(g.idFacilitador),
    inscritos: g.alumnosInscritos,
    horarios: g.horarios.map((h) => ({ diaSemana: h.diaSemana, horaInicio: horaCorta(h.horaInicio), horaFin: horaCorta(h.horaFin) })),
  });

  const sesionesPeriodo = grupos.flatMap((g) => g.horarios.map((h, i) => ({
    clave: `${g.id}-${i}`, dia: h.diaSemana, inicio: h.horaInicio, fin: h.horaFin,
    titulo: `${g.nivel} ${g.clave}`, detalle: `${g.aula} · ${g.facilitador}`, color: colorNivel(g.nivelNumero),
    onClick: () => setPanelAlumnos(g),
  })));

  return (
    <div className="grid gap-[18px]">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap items-end gap-3">
          <Campo etiqueta="Periodo" id="periodo">
            <select id="periodo" className={`${claseEntrada} min-w-[220px]`} value={idPeriodo} onChange={(e) => setIdPeriodo(e.target.value)}>
              {catalogos.periodos.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
            </select>
          </Campo>
          <div className="inline-flex rounded-lg border border-linea bg-white p-0.5 text-xs">
            {[['calendario', 'Calendario', CalendarDays], ['lista', 'Grupos', LayoutGrid]].map(([v, t, Icono]) => (
              <button key={v} type="button" onClick={() => setVista(v)}
                className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 font-semibold ${vista === v ? 'bg-marino text-white' : 'text-tenue hover:text-tinta'}`}>
                <Icono className="h-3.5 w-3.5" /> {t}
              </button>
            ))}
          </div>
        </div>
        {!esSoloLectura && (
          <Boton onClick={nuevoGrupo} disabled={cargando || !catalogos.periodos.length}>
            <Plus className="h-4 w-4" /> Abrir grupo
          </Boton>
        )}
      </div>

      {error && <Alerta tipo="error">{error}</Alerta>}
      {aviso && <Alerta tipo="ok">{aviso}</Alerta>}

      {cargando ? <Cargando /> : vista === 'calendario' ? (
        <Tarjeta titulo="Horario semanal del periodo" icono={CalendarDays}
          extra={`${grupos.length} grupos · haz clic en una sesión para ver sus alumnos`}>
          {grupos.length === 0 ? <p className="text-sm text-tenue">
            {soloLectura ? 'No hay grupos en este periodo.' : 'No hay grupos en este periodo. Usa «Abrir grupo».'}
          </p>
            : <CalendarioSemanal sesiones={sesionesPeriodo} />}
        </Tarjeta>
      ) : (
        <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
          {grupos.length === 0 && <Tarjeta><p className="text-sm text-tenue">No hay grupos en este periodo.</p></Tarjeta>}
          {grupos.map((g) => (
            <article key={g.id} className="flex flex-col gap-3 rounded-[10px] border border-linea bg-white p-4">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <div className={`mb-1 inline-block rounded px-2 py-0.5 text-[11px] font-semibold ${colorNivel(g.nivelNumero)}`}>Nivel {g.nivelNumero}</div>
                  <div className="font-semibold">{g.nivel} · Grupo {g.clave}</div>
                  <div className="text-xs text-tenue">{g.modalidad} · {g.aula}</div>
                  <div className="text-xs text-tenue">{g.facilitador}</div>
                </div>
                <Insignia estatus={g.estatusActa === 'Cerrada' ? 'Concluido' : 'Programado'}>{g.estatusActa}</Insignia>
              </div>
              <ul className="grid gap-0.5 text-[12.5px]">
                {g.horarios.map((h) => (
                  <li key={`${h.diaSemana}-${h.horaInicio}`}><b className="inline-block w-20">{DIAS[h.diaSemana - 1]}</b>{horaCorta(h.horaInicio)}–{horaCorta(h.horaFin)}</li>
                ))}
              </ul>
              <div>
                <div className="mb-1 flex justify-between text-xs text-tenue">
                  <span>{g.alumnosInscritos} de {g.cupo} alumnos</span>
                  {g.interesados > 0 && <span className="font-semibold text-aviso">{g.interesados} interesado(s)</span>}
                </div>
                <div className="h-1.5 rounded bg-neutro-fondo"><div className="h-1.5 rounded bg-marino" style={{ width: `${Math.min(100, (g.alumnosInscritos / g.cupo) * 100)}%` }} /></div>
              </div>
              <div className="mt-auto flex flex-wrap gap-2 border-t border-linea pt-3">
                <Boton className="flex-1" onClick={() => setPanelAlumnos(g)}><Users className="h-3.5 w-3.5" /> Alumnos</Boton>
                {!esSoloLectura && (
                  <>
                    <Boton variante="secundario" disabled={g.estatusActa === 'Cerrada'} onClick={() => editarExistente(g)}>
                      <Pencil className="h-3.5 w-3.5" /> Editar
                    </Boton>
                    <Boton variante="peligro" disabled={g.alumnosInscritos > 0}
                      title={g.alumnosInscritos > 0 ? 'Quita primero a los alumnos' : ''}
                      onClick={() => setBorrar(g)}><Trash2 className="h-3.5 w-3.5" /></Boton>
                  </>
                )}
              </div>
            </article>
          ))}
        </div>
      )}

      {!esSoloLectura && (
        <Tarjeta>
          <button type="button" className="flex w-full items-center justify-between text-[14.5px] font-semibold" onClick={() => setVerCatalogos(!verCatalogos)}>
            Catálogos para grupos (periodos y aulas)
            <ChevronDown className={`h-4 w-4 transition ${verCatalogos ? 'rotate-180' : ''}`} />
          </button>
          {verCatalogos && <Catalogos onCambio={async (msg, campo, id) => {
            await cargarCatalogos();
            if (campo === 'periodo') setIdPeriodo(String(id));
            setAviso(msg);
          }} onError={setError} />}
        </Tarjeta>
      )}

      {editor && (
        <EditorGrupo editor={editor} setEditor={setEditor} catalogos={catalogos}
          onGuardado={async (msg) => { setEditor(null); setAviso(msg); if (String(editor.idPeriodo) !== idPeriodo) setIdPeriodo(String(editor.idPeriodo)); await cargarGrupos(); }} />
      )}
      {panelAlumnos && (
        <PanelAlumnos grupo={panelAlumnos} soloLectura={esSoloLectura}
          onCerrar={() => { setPanelAlumnos(null); if (!esSoloLectura) cargarGrupos(); }}
          onEditar={(g) => { setPanelAlumnos(null); editarExistente(g); }} />
      )}
      {!esSoloLectura && borrar && (
        <ConfirmacionDoble titulo="Eliminar grupo" palabra="ELIMINAR" textoBoton="Eliminar grupo"
          mensaje={<>Se eliminará el grupo <b>{borrar.nivel} {borrar.clave}</b> con sus horarios y las elecciones de los alumnos interesados.</>}
          onCerrar={() => setBorrar(null)}
          onConfirmar={async () => {
            try {
              await eliminarGrupo(borrar.id);
              setAviso(`Grupo ${borrar.nivel} ${borrar.clave} eliminado.`);
              await cargarGrupos();
            } catch (e) {
              setError(mensajeError(e));
            } finally {
              setBorrar(null);
            }
          }} />
      )}
    </div>
  );
}

/** Formulario de grupo con calendario interactivo: clic en una hora libre agrega una sesión; clic en una sesión la quita. */
function EditorGrupo({ editor, setEditor, catalogos, onGuardado }) {
  const [duracion, setDuracion] = useState(120);
  const [ocupados, setOcupados] = useState([]);
  const [error, setError] = useState('');
  const [guardando, setGuardando] = useState(false);
  const cambiar = (campo) => (e) => setEditor({ ...editor, [campo]: campo === 'clave' ? e.target.value.toUpperCase() : e.target.value });

  // Horarios del mismo periodo que usan el aula o el facilitador elegidos (para evitar traslapes).
  useEffect(() => {
    if (!editor.idPeriodo) return;
    listarGrupos(editor.idPeriodo).then(setOcupados).catch(() => setOcupados([]));
  }, [editor.idPeriodo]);

  const sesionesOcupadas = useMemo(() => ocupados
    .filter((g) => g.id !== editor.id && (String(g.idAula) === editor.idAula || String(g.idFacilitador) === editor.idFacilitador))
    .flatMap((g) => g.horarios.map((h, i) => ({
      clave: `o-${g.id}-${i}`, dia: h.diaSemana, inicio: h.horaInicio, fin: h.horaFin,
      titulo: `Ocupado: ${g.nivel} ${g.clave}`,
      detalle: String(g.idAula) === editor.idAula ? `Aula ${g.aula}` : `Facilitador ${g.facilitador}`,
      color: 'bg-neutro-fondo border-neutro text-tenue', atenuada: true,
    }))), [ocupados, editor.id, editor.idAula, editor.idFacilitador]);

  const nivel = catalogos.niveles.find((n) => String(n.id) === editor.idNivel);
  const propias = editor.horarios.map((h, i) => ({
    clave: `p-${i}`, dia: h.diaSemana, inicio: h.horaInicio, fin: h.horaFin,
    titulo: `${nivel?.nombre ?? 'Grupo'} ${editor.clave || ''}`, detalle: 'Clic para quitar',
    color: colorNivel(nivel?.numero), onClick: () => setEditor({ ...editor, horarios: editor.horarios.filter((_, j) => j !== i) }),
  }));

  const agregar = (dia, hora) => {
    const inicio = hora * 60;
    setEditor({ ...editor, horarios: [...editor.horarios, { diaSemana: dia, horaInicio: aHora(inicio), horaFin: aHora(Math.min(inicio + duracion, 23 * 60 + 59)) }] });
  };
  const cambiarSesion = (i, campo, valor) => setEditor({
    ...editor, horarios: editor.horarios.map((h, j) => (j === i ? { ...h, [campo]: campo === 'diaSemana' ? Number(valor) : valor } : h)),
  });

  const guardar = async (e) => {
    e.preventDefault();
    if (!editor.horarios.length) { setError('Agrega al menos una sesión haciendo clic en el calendario.'); return; }
    const invalida = editor.horarios.find((h) => minutos(h.horaFin) <= minutos(h.horaInicio));
    if (invalida) { setError('Cada sesión debe terminar después de su hora de inicio.'); return; }
    setGuardando(true);
    setError('');
    const datos = {
      clave: editor.clave, cupo: Number(editor.cupo), idNivel: Number(editor.idNivel), idPeriodo: Number(editor.idPeriodo),
      idModalidad: Number(editor.idModalidad), idAula: Number(editor.idAula), idFacilitador: Number(editor.idFacilitador),
      horarios: editor.horarios.map((h) => ({ diaSemana: Number(h.diaSemana), horaInicio: h.horaInicio, horaFin: h.horaFin })),
    };
    try {
      if (editor.id) await editarGrupo(editor.id, datos); else await abrirGrupo(datos);
      await onGuardado(editor.id ? 'Grupo actualizado.' : 'Grupo abierto: ya aparece en los cursos disponibles de los alumnos de ese nivel.');
    } catch (err) {
      setError(mensajeError(err));
    } finally {
      setGuardando(false);
    }
  };

  const opciones = (lista, texto) => lista.map((x) => <option key={x.id} value={x.id}>{texto(x)}</option>);
  const conAlumnos = editor.inscritos > 0;

  return (
    <Modal titulo={editor.id ? 'Editar grupo' : 'Abrir grupo'} onCerrar={() => setEditor(null)} ancho="max-w-6xl">
      <form className="grid gap-5 lg:grid-cols-[300px_1fr]" onSubmit={guardar}>
        <div className="grid content-start gap-3">
          {conAlumnos && <Alerta tipo="info">El grupo tiene alumnos: no se pueden cambiar el nivel ni el periodo.</Alerta>}
          <div className="flex gap-2">
  <Boton variante="secundario" onClick={verActa}>
    <Eye className="h-3.5 w-3.5" /> {cerrada ? 'Ver acta (PDF)' : 'Ver acta preliminar (PDF)'}
  </Boton>
  {!cerrada && !soloLectura && <Boton variante="secundario" onClick={() => onEditar(g)}><Pencil className="h-3.5 w-3.5" /> Editar grupo</Boton>}
</div>
          <div className="grid grid-cols-2 gap-3">
            <Campo etiqueta="Clave (A–Z)" id="clave">
              <input id="clave" className={claseEntrada} maxLength="2" pattern="[A-Za-z]{1,2}" required value={editor.clave} onChange={cambiar('clave')} />
            </Campo>
            <Campo etiqueta="Cupo" id="cupo">
              <input id="cupo" type="number" min="1" max="255" required className={claseEntrada} value={editor.cupo} onChange={cambiar('cupo')} />
            </Campo>
          </div>
          <Campo etiqueta="Periodo" id="ed-periodo">
            <select id="ed-periodo" required disabled={conAlumnos} className={claseEntrada} value={editor.idPeriodo} onChange={cambiar('idPeriodo')}>
              {opciones(catalogos.periodos.filter((p) => p.fechaFin >= fechaLocal() || String(p.id) === editor.idPeriodo), (p) => p.nombre)}
            </select>
          </Campo>
          <Campo etiqueta="Nivel" id="ed-nivel">
            <select id="ed-nivel" required disabled={conAlumnos} className={claseEntrada} value={editor.idNivel} onChange={cambiar('idNivel')}>
              {opciones(catalogos.niveles, (n) => `${n.numero}. ${n.nombre} (${n.nivelMcer})`)}
            </select>
          </Campo>
          <Campo etiqueta="Modalidad" id="ed-modalidad">
            <select id="ed-modalidad" required className={claseEntrada} value={editor.idModalidad} onChange={cambiar('idModalidad')}>
              {opciones(catalogos.modalidades, (m) => m.nombre)}
            </select>
          </Campo>
          <Campo etiqueta="Aula" id="ed-aula">
            <select id="ed-aula" required className={claseEntrada} value={editor.idAula} onChange={cambiar('idAula')}>
              {opciones(catalogos.aulas, (a) => a.nombre)}
            </select>
          </Campo>
          <Campo etiqueta="Facilitador" id="ed-fac">
            <select id="ed-fac" required className={claseEntrada} value={editor.idFacilitador} onChange={cambiar('idFacilitador')}>
              {opciones(catalogos.facilitadores.filter((f) => f.activo || String(f.id) === editor.idFacilitador), (f) => f.nombreCompleto)}
            </select>
          </Campo>
          {error && <Alerta tipo="error">{error}</Alerta>}
          <div className="flex gap-2">
            <Boton type="button" variante="secundario" onClick={() => setEditor(null)}>Cancelar</Boton>
            <Boton type="submit" className="flex-1" disabled={guardando}>{guardando ? 'Guardando…' : editor.id ? 'Guardar cambios' : 'Abrir grupo'}</Boton>
          </div>
        </div>

        <div className="grid content-start gap-3">
          <div className="flex flex-wrap items-center justify-between gap-2 text-[13px]">
            <span className="text-tenue">Haz clic en una hora libre para agregar una sesión; clic en una sesión para quitarla. En gris: horarios ya ocupados del aula o del facilitador.</span>
            <label className="flex items-center gap-2 font-semibold">Duración
              <select className="rounded-lg border border-[#C9D1DD] px-2 py-1" value={duracion} onChange={(e) => setDuracion(Number(e.target.value))}>
                {DURACIONES.map(([v, t]) => <option key={v} value={v}>{t}</option>)}
              </select>
            </label>
          </div>
          <CalendarioSemanal sesiones={[...sesionesOcupadas, ...propias]} onSeleccionarHueco={agregar} />
          {editor.horarios.length > 0 && (
            <div className="grid gap-2">
              <div className="text-xs font-semibold text-[#3A4556]">Sesiones del grupo ({editor.horarios.length}) · puedes ajustar las horas</div>
              {editor.horarios.map((h, i) => (
                <div key={i} className="grid grid-cols-[1fr_1fr_1fr_auto] items-center gap-2">
                  <select aria-label="Día" className={claseEntrada} value={h.diaSemana} onChange={(e) => cambiarSesion(i, 'diaSemana', e.target.value)}>
                    {DIAS.map((d, j) => <option key={d} value={j + 1}>{d}</option>)}
                  </select>
                  <input aria-label="Inicio" type="time" className={claseEntrada} value={h.horaInicio} onChange={(e) => cambiarSesion(i, 'horaInicio', e.target.value)} />
                  <input aria-label="Fin" type="time" className={claseEntrada} value={h.horaFin} onChange={(e) => cambiarSesion(i, 'horaFin', e.target.value)} />
                  <button type="button" aria-label="Quitar sesión" className="text-tenue hover:text-error"
                    onClick={() => setEditor({ ...editor, horarios: editor.horarios.filter((_, j) => j !== i) })}><X className="h-4 w-4" /></button>
                </div>
              ))}
            </div>
          )}
        </div>
      </form>
    </Modal>
  );
}

/** Alumnos del grupo: asignados (con su papeleta de pago) e interesados de la encuesta; asignar o quitar. */
function PanelAlumnos({ grupo, onCerrar, onEditar, soloLectura = false }) {
  const [detalle, setDetalle] = useState(null);
  const [candidatos, setCandidatos] = useState([]);
  const [numeroControl, setNumeroControl] = useState('');
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [ocupado, setOcupado] = useState('');
  const [quitar, setQuitar] = useState(null);
  // Observación de coordinación: { tipo: 'cierre' } o { tipo: 'nivel', alumno }.
  const [observar, setObservar] = useState(null);

  useEffect(() => {
    alumnosDelGrupo(grupo.id).then(setDetalle).catch((e) => setError(mensajeError(e)));
    buscarAlumnos({ nivel: grupo.nivelNumero, tamano: 100 }).then((p) => setCandidatos(p.content)).catch(() => {});
  }, [grupo.id, grupo.nivelNumero]);

  const ejecutar = async (clave, fn, mensaje) => {
    setOcupado(clave);
    setError('');
    setAviso('');
    try {
      setDetalle(await fn());
      setAviso(mensaje);
      setNumeroControl('');
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setOcupado('');
    }
  };

  const verDocumento = useVisorDocumento();
  const verActa = () => verDocumento({
  titulo: `Acta de calificaciones · ${g.nivel} ${g.clave} · ${g.periodo}`,
  nombre: `acta-${g.nivel.replace(/\s+/g, '-')}-${g.clave}`,
  cargar: () => descargarActaPdf(g.id),
  });

  const asignar = (nc) => ejecutar(nc, () => asignarAlumno(grupo.id, nc), `Alumno ${nc} asignado; se generó su papeleta de pago.`);

  const g = detalle?.grupo ?? grupo;
  const cerrada = g.estatusActa === 'Cerrada';
  const inscritosNc = new Set((detalle?.inscritos || []).map((a) => a.numeroControl));
  const inscritos = detalle?.inscritos || [];
  const todasCapturadas = inscritos.length > 0 && inscritos.every((a) => a.calificacionFinal != null);
  const sinLiquidar = inscritos.filter((a) => a.estatusPago !== 'Liquidada');
  const nombrePorNc = Object.fromEntries(inscritos.map((a) => [a.numeroControl, a.nombreCompleto]));

  return (
    <Modal titulo={`${g.nivel} · Grupo ${g.clave}`} onCerrar={onCerrar} ancho="max-w-4xl">
      <div className="mb-4 flex flex-wrap items-center justify-between gap-2 text-[13px] text-tenue">
        <span>{g.periodo} · {g.modalidad} · {g.aula} · {g.facilitador} · {g.horarios.map((h) => `${DIAS[h.diaSemana - 1].slice(0, 3)} ${horaCorta(h.horaInicio)}–${horaCorta(h.horaFin)}`).join(', ')}</span>
        {!cerrada && !soloLectura && <Boton variante="secundario" onClick={() => onEditar(g)}><Pencil className="h-3.5 w-3.5" /> Editar grupo</Boton>}
      </div>
      {error && <Alerta tipo="error" className="mb-3">{error}</Alerta>}
      {aviso && <Alerta tipo="ok" className="mb-3">{aviso}</Alerta>}
      {!detalle ? <Cargando /> : (
        <div className="grid gap-5 lg:grid-cols-[1.4fr_1fr]">
          <section>
            <h4 className="mb-2 text-sm font-semibold">Alumnos asignados ({detalle.inscritos.length} de {g.cupo})</h4>
            {detalle.inscritos.length === 0 ? <p className="text-[13px] text-tenue">Aún no hay alumnos asignados.</p> : (
              <ul className="divide-y divide-linea rounded-lg border border-linea">
                {detalle.inscritos.map((a) => (
                  <li key={a.numeroControl} className="flex flex-wrap items-center gap-3 px-3 py-2.5">
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[13px] font-semibold">{a.nombreCompleto}</div>
                      <div className="text-xs text-tenue">{a.numeroControl}{a.calificacionFinal != null ? ` · Calificación ${a.calificacionFinal}` : ''}</div>
                    </div>
                    <div className="text-right text-xs">
                      <Insignia estatus={a.estatusPago} />
                      {a.saldoPendiente > 0 && <div className="mt-0.5 text-tenue">Saldo {pesos(a.saldoPendiente)}</div>}
                    </div>
                    {!soloLectura && a.avancePendiente && (
                      <div className="flex w-full items-center justify-end gap-2 border-t border-dashed border-linea pt-2">
                        <span className="mr-auto text-xs text-tenue">Sin pago al cerrar el acta</span>
                        <Insignia estatus="Nivel pendiente" />
                        <Boton variante="secundario" className="whitespace-nowrap" disabled={Boolean(ocupado)}
                          onClick={() => setObservar({ tipo: 'nivel', alumno: a })}>
                          Asignar nivel
                        </Boton>
                      </div>
                    )}
                    {!cerrada && !soloLectura && (
                      <button type="button" title="Quitar del grupo" className="text-tenue hover:text-error" disabled={Boolean(ocupado)}
                        onClick={() => setQuitar(a)}><UserMinus className="h-4 w-4" /></button>
                    )}
                  </li>
                ))}
              </ul>
            )}
            {!cerrada && !soloLectura && (
              <form className="mt-4 flex gap-2" onSubmit={(e) => { e.preventDefault(); if (numeroControl.trim()) asignar(numeroControl.trim().toUpperCase()); }}>
                <input list="candidatos-grupo" className={claseEntrada} placeholder={`Número de control (alumnos de ${g.nivel})`}
                  value={numeroControl} onChange={(e) => setNumeroControl(e.target.value.toUpperCase())} />
                <datalist id="candidatos-grupo">
                  {candidatos.filter((c) => !inscritosNc.has(c.numeroControl)).map((c) => (
                    <option key={c.numeroControl} value={c.numeroControl}>{c.nombreCompleto}</option>
                  ))}
                </datalist>
                <Boton type="submit" disabled={Boolean(ocupado)}><UserPlus className="h-4 w-4" /> Asignar</Boton>
              </form>
            )}
            <p className="mt-2 text-xs text-tenue">{soloLectura ? 'Consulta de alumnos del grupo y su estatus de pago.' : 'Al asignar a un alumno se genera automáticamente su papeleta de pago con la tarifa vigente del nivel. El facilitador cierra el acta cuando todos liquidaron su curso; si no, coordinación puede cerrarla con una observación.'}</p>
            {!soloLectura && (
              <SeccionActa grupo={g} cerrada={cerrada} todasCapturadas={todasCapturadas} sinLiquidar={sinLiquidar}
                ocupado={Boolean(ocupado)} onCerrar={() => setObservar({ tipo: 'cierre' })} />
            )}
            {detalle.observaciones?.length > 0 && (
              <div className="mt-4">
                <h4 className="mb-2 text-sm font-semibold">Observaciones de coordinación</h4>
                <ul className="grid gap-2">
                  {detalle.observaciones.map((o, i) => (
                    <li key={i} className="rounded-lg border border-linea px-3 py-2 text-xs">
                      <div className="font-semibold">
                        {o.tipo} · {nombrePorNc[o.numeroControl] || o.numeroControl}
                        {o.estatusNivel ? ` · ${o.estatusNivel}` : ''}
                      </div>
                      <div className="mt-0.5">{o.observacion}</div>
                      <div className="mt-0.5 text-tenue">
                        {new Date(o.fecha).toLocaleString('es-MX', { dateStyle: 'short', timeStyle: 'short' })} · {o.usuario} · Pago: {o.estatusPago}
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </section>
          <section>
            <h4 className="mb-2 text-sm font-semibold">Interesados de la encuesta ({detalle.interesados.length})</h4>
            {detalle.interesados.length === 0 ? <p className="text-[13px] text-tenue">Ningún alumno ha elegido este horario todavía.</p> : (
              <ul className="divide-y divide-linea rounded-lg border border-linea">
                {detalle.interesados.map((i) => (
                  <li key={i.numeroControl} className="flex items-center gap-3 px-3 py-2.5">
                    <div className="min-w-0 flex-1">
                      <div className="truncate text-[13px] font-semibold">{i.nombreCompleto}</div>
                      <div className="text-xs text-tenue">{i.numeroControl} · {i.carrera}</div>
                    </div>
                    {!cerrada && !soloLectura && <Boton disabled={Boolean(ocupado)} onClick={() => asignar(i.numeroControl)}>Asignar</Boton>}
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}
      {observar && (
        <ModalObservacion grupo={g} observar={observar} sinLiquidar={sinLiquidar} onCancelar={() => setObservar(null)}
          onConfirmar={(texto) => {
            const actual = observar;
            setObservar(null);
            if (actual.tipo === 'cierre') {
              ejecutar('cierre', () => cerrarActaCoordinacion(grupo.id, texto), 'Acta cerrada por coordinación con la observación registrada.');
            } else {
              ejecutar(actual.alumno.numeroControl, () => asignarNivelPendiente(grupo.id, actual.alumno.numeroControl, texto),
                `Nivel asignado a ${actual.alumno.nombreCompleto}.`);
            }
          }} />
      )}
      {quitar && (
        <Modal titulo="Quitar alumno del grupo" onCerrar={() => setQuitar(null)} ancho="max-w-md">
          <p className="text-[13px]">¿Quitar a <b>{quitar.nombreCompleto}</b> del grupo? Si aún no registró comprobantes, su papeleta de pago se elimina
            y su nivel vuelve a quedar habilitado.</p>
          <div className="mt-5 flex justify-end gap-2">
            <Boton variante="secundario" onClick={() => setQuitar(null)}>Cancelar</Boton>
            <Boton variante="peligro" onClick={() => {
              const a = quitar;
              setQuitar(null);
              ejecutar(a.numeroControl, () => quitarAlumno(grupo.id, a.numeroControl), `${a.nombreCompleto} salió del grupo.`);
            }}>Quitar</Boton>
          </div>
        </Modal>
      )}
    </Modal>
  );
}

/** Estado del acta en la vista de coordinación y botón para cerrarla con observación. */
function SeccionActa({ grupo, cerrada, todasCapturadas, sinLiquidar, ocupado, onCerrar }) {
  if (cerrada) {
    return (
      <div className="mt-4 rounded-lg border border-linea bg-fondo px-3 py-2.5 text-[13px]">
        <div className="flex items-center gap-2 font-semibold"><Lock className="h-4 w-4" /> Acta cerrada
          {grupo.fechaCierreActa && ` el ${new Date(`${grupo.fechaCierreActa}T00:00:00`).toLocaleDateString('es-MX')}`}
          {grupo.cerradaPor && ` por ${grupo.cerradaPor.toLowerCase()}`}</div>
        {grupo.observacionCierre && <p className="mt-1"><b>Observación:</b> {grupo.observacionCierre}</p>}
      </div>
    );
  }
  return (
    <div className="mt-4 rounded-lg border border-linea px-3 py-2.5 text-[13px]">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span className="font-semibold">Cierre del acta</span>
        <Boton variante="secundario" disabled={!todasCapturadas || ocupado} onClick={onCerrar}>
          <NotebookPen className="h-4 w-4" /> Cerrar acta con observación
        </Boton>
      </div>
      <p className="mt-1 text-xs text-tenue">
        {!todasCapturadas ? 'El facilitador aún no captura todas las calificaciones.'
          : sinLiquidar.length === 0 ? 'Todos liquidaron su curso; el facilitador puede cerrar el acta.'
            : `${sinLiquidar.length} alumno(s) sin liquidar: conservarán su calificación y su nivel quedará pendiente hasta asignarlo.`}
      </p>
    </div>
  );
}

/** Captura de la observación obligatoria para cerrar el acta o asignar un nivel pendiente. */
function ModalObservacion({ grupo, observar, sinLiquidar, onCancelar, onConfirmar }) {
  const [texto, setTexto] = useState('');
  const valido = texto.trim().length >= 10;
  const esCierre = observar.tipo === 'cierre';
  const a = observar.alumno;
  return (
    <Modal titulo={esCierre ? 'Cerrar acta desde coordinación' : 'Asignar nivel pendiente'} onCerrar={onCancelar} ancho="max-w-lg">
      {esCierre ? (
        <div className="text-[13px]">
          <p>Se cerrará el acta de <b>{grupo.nivel} · Grupo {grupo.clave}</b>. Quienes liquidaron su curso reciben su nivel ahora.</p>
          {sinLiquidar.length > 0 && (
            <>
              <p className="mt-2">Sin liquidar (calificación registrada, nivel pendiente):</p>
              <ul className="mt-1 list-disc pl-5">
                {sinLiquidar.map((s) => <li key={s.numeroControl}>{s.nombreCompleto} · {s.estatusPago}</li>)}
              </ul>
            </>
          )}
        </div>
      ) : (
        <div className="text-[13px]">
          <p><b>{a.nombreCompleto}</b> ({a.numeroControl}) · Calificación <b>{a.calificacionFinal}</b> · Pago: <b>{a.estatusPago}</b></p>
          <p className="mt-1">Se registrará el nivel como <b>{a.calificacionFinal >= 70 ? 'Acreditado' : 'Reprobado'}</b> y, si acreditó,
            se habilitará el siguiente nivel.</p>
          {a.estatusPago !== 'Liquidada' && (
            <Alerta tipo="aviso" className="mt-2">El curso aún no aparece liquidado. Indica en la observación cómo se comprobó el pago.</Alerta>
          )}
        </div>
      )}
      <div className="mt-4">
      <Campo etiqueta="Observación (obligatoria)" id="observacionCoordinacion"
        ayuda={`${texto.trim().length}/500 · mínimo 10 caracteres`}>
        <textarea id="observacionCoordinacion" className={`${claseEntrada} min-h-[90px]`} maxLength="500" value={texto}
          placeholder={esCierre ? 'Ej. El alumno presentará su pago la próxima semana; se cierra para no retrasar al grupo.'
            : 'Ej. Presentó el recibo del 25/11/2026, folio 1234.'}
          onChange={(e) => setTexto(e.target.value)} />
      </Campo>
      </div>
      <div className="mt-5 flex justify-end gap-2">
        <Boton variante="secundario" onClick={onCancelar}>Cancelar</Boton>
        <Boton disabled={!valido} onClick={() => onConfirmar(texto.trim())}>{esCierre ? 'Cerrar acta' : 'Asignar nivel'}</Boton>
      </div>
    </Modal>
  );
}

/** Alta rápida de periodos y aulas. */
function Catalogos({ onCambio, onError }) {
  const [periodo, setPeriodo] = useState({ nombre: '', fechaInicio: '', fechaFin: '' });
  const [aula, setAula] = useState('');
  return (
    <div className="mt-4 grid gap-4 md:grid-cols-2">
      <form className="grid gap-2" onSubmit={async (e) => {
        e.preventDefault();
        try {
          const creado = await crearPeriodo(periodo);
          setPeriodo({ nombre: '', fechaInicio: '', fechaFin: '' });
          await onCambio('Periodo agregado al catálogo.', 'periodo', creado.id);
        } catch (err) { onError(mensajeError(err)); }
      }}>
        <strong className="text-xs">Agregar periodo</strong>
        <input aria-label="Nombre del periodo" className={claseEntrada} maxLength="30" placeholder="Enero-Julio 2027" value={periodo.nombre}
          onChange={(e) => setPeriodo({ ...periodo, nombre: e.target.value })} required />
        <div className="grid grid-cols-2 gap-2">
          <input aria-label="Inicio del periodo" className={claseEntrada} type="date" value={periodo.fechaInicio} onChange={(e) => setPeriodo({ ...periodo, fechaInicio: e.target.value })} required />
          <input aria-label="Fin del periodo" className={claseEntrada} type="date" value={periodo.fechaFin} onChange={(e) => setPeriodo({ ...periodo, fechaFin: e.target.value })} required />
        </div>
        <Boton variante="secundario" type="submit">Agregar periodo</Boton>
      </form>
      <form className="grid content-start gap-2" onSubmit={async (e) => {
        e.preventDefault();
        try {
          await crearAula({ nombre: aula });
          setAula('');
          await onCambio('Aula agregada al catálogo.');
        } catch (err) { onError(mensajeError(err)); }
      }}>
        <strong className="text-xs">Agregar aula</strong>
        <input aria-label="Nombre del aula" className={claseEntrada} maxLength="30" placeholder="Aula 1" value={aula} onChange={(e) => setAula(e.target.value)} required />
        <Boton variante="secundario" type="submit">Agregar aula</Boton>
      </form>
    </div>
  );
}
