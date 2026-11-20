import { useCallback, useEffect, useState } from 'react';
import {
  ArrowLeft, CalendarPlus, Download, FileSpreadsheet, History, RotateCcw, Search, Unlock, Upload, UserMinus,
  UserPlus, UserRound, Wand2,
} from 'lucide-react';
import {
  buscarAlumnos, obtenerExpedienteIntegral, crearAlumno, darDeBajaAlumno, reactivarAlumno, liberarIdioma,
  cargarAlumnosExcel, descargarPlantillaAlumnos, cargarImportacionOperativa, descargarPlantillaOperativa,
  completarDatosExcel,
} from '../../api/alumnos';
import { obtenerCarreras, obtenerNiveles } from '../../api/catalogos';
import ExpedienteIntegral from '../../components/ExpedienteIntegral';
import { mensajeError } from '../../api/cliente';
import { useAuth } from '../../auth/AuthContext';
import { esCoordinacion } from '../../auth/roles';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';
import { Modal } from '../../components/Modal';

const FILTROS_VACIOS = { idCarrera: '', numeroControl: '', nombre: '', nivel: '', semestre: '', estatus: '', formaLiberacion: '' };
const SEMESTRES = Array.from({ length: 15 }, (_, i) => i + 1);
/** Caracteres que no forman parte de un nombre: todo lo que no sea letra (con acentos y ñ), espacio, guion o apóstrofo. */
const NO_LETRAS = /[^\p{L}\s'’-]/gu;
const SEXO = { H: 'Hombre', M: 'Mujer' };

/** Guarda en el equipo un archivo descargado de la API (plantillas de Excel). */
function guardarArchivo(blob, nombre) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = nombre;
  a.click();
  URL.revokeObjectURL(url);
}

/**
 * Las tres cargas por Excel. Cada una dice qué hace, qué no hace y cuándo usarla, para que la coordinación elija una
 * sola vez la correcta en lugar de probar botones parecidos.
 */
const CARGAS_EXCEL = [
  {
    tipo: 'importar-operativo',
    icono: CalendarPlus,
    titulo: 'Inicio de periodo (carga operativa)',
    cuando: 'Al arrancar un periodo: dar de alta alumnos, facilitadores, grupos y horarios nuevos.',
    hace: 'Crea lo que no existe y deja igual lo ya registrado. Genera contraseñas temporales para facilitadores nuevos.',
    noHace: 'No inscribe alumnos ni registra pagos, calificaciones o avance.',
    plantilla: 'Plantilla operativa',
  },
  {
    tipo: 'importar',
    icono: History,
    titulo: 'Histórico académico',
    cuando: 'Para pasar al sistema el concentrado de la coordinación o periodos anteriores completos.',
    hace: 'Crea alumnos y guarda sus niveles cursados; con 70 o más acredita el nivel. Acepta la matrícula de control escolar.',
    noHace: 'No genera cargos ni comprobantes; lo que no venga en el archivo se queda vacío.',
    plantilla: 'Concentrado o plantilla completa',
  },
  {
    tipo: 'completar',
    icono: Wand2,
    titulo: 'Completar datos faltantes',
    cuando: 'Cuando los registros ya existen y solo faltan datos (correo, semestre, aula, horario…).',
    hace: 'Rellena únicamente los campos vacíos de alumnos, facilitadores y grupos, y agrega horarios faltantes.',
    noHace: 'No crea registros ni cambia datos ya guardados, calificaciones o avance.',
    plantilla: 'Misma plantilla completa del histórico',
  },
];

/** Paso 1 de «Importar desde Excel»: elegir el tipo de carga. */
function ModalElegirCarga({ onElegir, onCerrar }) {
  return (
    <Modal titulo="Importar desde Excel" onCerrar={onCerrar} ancho="max-w-2xl">
      <p className="mb-3 text-[13px] text-tenue">¿Qué necesitas cargar? Elige una opción; cada una tiene su plantilla.</p>
      <div className="grid gap-2">
        {CARGAS_EXCEL.map(({ tipo, icono: Icono, titulo, cuando, hace, noHace, plantilla }) => (
          <button key={tipo} type="button" onClick={() => onElegir(tipo)}
            className="grid grid-cols-[auto_1fr] gap-x-3 rounded-lg border border-linea p-3 text-left text-[13px] hover:border-marino hover:bg-fondo focus:outline-none focus-visible:ring-2 focus-visible:ring-marino">
            <Icono className="row-span-4 mt-0.5 h-5 w-5 text-marino" aria-hidden />
            <b className="text-sm">{titulo}</b>
            <span>{cuando}</span>
            <span className="text-tenue"><b>Hace:</b> {hace} <b>No hace:</b> {noHace}</span>
            <span className="flex items-center gap-1 text-xs text-tenue"><FileSpreadsheet className="h-3.5 w-3.5" aria-hidden /> {plantilla}</span>
          </button>
        ))}
      </div>
    </Modal>
  );
}

/** Enlace para volver a la elección del tipo de carga desde cada ventana de importación. */
function VolverAElegir({ onVolver, deshabilitado }) {
  if (!onVolver) return null;
  return (
    <button type="button" disabled={deshabilitado} onClick={onVolver}
      className="mb-3 inline-flex items-center gap-1 text-xs font-semibold text-marino hover:underline disabled:opacity-50">
      <ArrowLeft className="h-3.5 w-3.5" /> Elegir otro tipo de carga
    </button>
  );
}

function fechaCorta(iso) {
  return iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('es-MX') : null;
}

/** Consulta, alta, baja y liberación de idioma de alumnos (CU-02, CU-16, CU-21; cambio 15). */
export default function Alumnos() {
  const { perfil } = useAuth();
  const puedeEditar = esCoordinacion(perfil.rol);
  const [busqueda, setBusqueda] = useState(FILTROS_VACIOS);
  const [avisoNombre, setAvisoNombre] = useState(false);
  const [filtro, setFiltro] = useState(FILTROS_VACIOS);
  const [carreras, setCarreras] = useState([]);
  const [niveles, setNiveles] = useState([]);
  const [pagina, setPagina] = useState(null);
  const [expediente, setExpediente] = useState(null);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [errorExpediente, setErrorExpediente] = useState('');
  const [cargando, setCargando] = useState(true);
  const [cargandoExpediente, setCargandoExpediente] = useState(false);
  // { tipo: 'nuevo'|'elegir-carga'|'importar-operativo'|'importar'|'completar'|'baja'|'liberar', alumno?, desdeEleccion? }
  const [modal, setModal] = useState(null);
  const volverAElegir = () => setModal({ tipo: 'elegir-carga' });

  const cargar = useCallback(async (criterio, numeroPagina) => {
    setCargando(true);
    setError('');
    try {
      setPagina(await buscarAlumnos({ ...criterio, pagina: numeroPagina }));
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => {
    cargar(FILTROS_VACIOS, 0);
    Promise.all([obtenerCarreras(), obtenerNiveles()])
      .then(([c, n]) => { setCarreras(c); setNiveles(n); })
      .catch(() => {});
  }, [cargar]);

  async function seleccionar(alumno) {
    setExpediente(null);
    setErrorExpediente('');
    setCargandoExpediente(true);
    try {
      setExpediente(await obtenerExpedienteIntegral(alumno.numeroControl));
      window.setTimeout(() => document.getElementById('expediente-integral')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 50);
    } catch (e) {
      setErrorExpediente(mensajeError(e));
    } finally {
      setCargandoExpediente(false);
    }
  }

  function buscar(evento) {
    evento.preventDefault();
    const criterio = { ...busqueda, numeroControl: busqueda.numeroControl.trim(), nombre: busqueda.nombre.trim() };
    setFiltro(criterio);
    setExpediente(null);
    cargar(criterio, 0);
  }

  const cambiarFiltro = (campo) => (evento) => {
    const val = evento.target.value;
    if (campo === 'nivel' && val.includes(':')) {
      const [n, f] = val.split(':');
      setBusqueda({ ...busqueda, nivel: n, formaLiberacion: f });
      return;
    }
    if (campo === 'nombre') {
      const soloLetras = val.replace(NO_LETRAS, '');
      setAvisoNombre(soloLetras !== val);
      setBusqueda({ ...busqueda, nombre: soloLetras });
      return;
    }
    setBusqueda({
      ...busqueda, [campo]: campo === 'numeroControl' ? val.toUpperCase() : val,
    });
  };
  const hayFiltros = Object.values(filtro).some((v) => v !== '');

  const recargar = (mensaje) => {
    setModal(null);
    setAviso(mensaje || '');
    cargar(filtro, pagina ? pagina.number : 0);
  };

  return (
    <div className="grid gap-[18px]">
      <Tarjeta titulo="Buscar alumnos" icono={Search}>
        <form className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4 lg:items-end" onSubmit={buscar}>
          <Campo etiqueta="Carrera" id="filtroCarrera">
            <select id="filtroCarrera" className={claseEntrada} value={busqueda.idCarrera} onChange={cambiarFiltro('idCarrera')}>
              <option value="">Todas</option>
              {carreras.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
            </select>
          </Campo>
          <Campo etiqueta="Número de control" id="filtroNumeroControl">
            <input id="filtroNumeroControl" className={claseEntrada} maxLength="10" placeholder="Ej. 2252"
              value={busqueda.numeroControl} onChange={cambiarFiltro('numeroControl')} />
          </Campo>
          <Campo etiqueta="Nombre" id="filtroNombre"
            error={avisoNombre ? 'El nombre solo admite letras; para buscar por número usa «Número de control».' : undefined}>
            <input id="filtroNombre" className={claseEntrada} maxLength="100" placeholder="Nombre o apellidos"
              aria-invalid={avisoNombre || undefined}
              value={busqueda.nombre} onChange={cambiarFiltro('nombre')} onBlur={() => setAvisoNombre(false)} />
          </Campo>
          <Campo etiqueta="Semestre" id="filtroSemestre">
            <select id="filtroSemestre" className={claseEntrada} value={busqueda.semestre} onChange={cambiarFiltro('semestre')}>
              <option value="">Todos</option>
              {SEMESTRES.map((s) => <option key={s} value={s}>{s}.º</option>)}
            </select>
          </Campo>
          <Campo etiqueta="Nivel" id="filtroNivel">
            <select id="filtroNivel" className={claseEntrada} value={busqueda.nivel} onChange={cambiarFiltro('nivel')}>
              <option value="">Todos</option>
              {niveles.map((n) => <option key={n.id} value={n.numero}>{n.numero}. {n.nombre}</option>)}
              <option value="0">Idioma acreditado (todos)</option>
              <option value="0:Examen global">Idioma acreditado · Examen global</option>
              <option value="0:Lengua materna">Idioma acreditado · Lengua materna</option>
              <option value="0:Curso">Idioma acreditado · Cursos concluidos</option>
              <option value="0:Registro histórico">Idioma acreditado · Registro histórico</option>
            </select>
          </Campo>
          <Campo etiqueta="Estatus" id="filtroEstatus">
            <select id="filtroEstatus" className={claseEntrada} value={busqueda.estatus} onChange={cambiarFiltro('estatus')}>
              <option value="">Activos</option>
              <option value="Baja">De baja</option>
              <option value="Todos">Todos</option>
            </select>
          </Campo>
          <Campo etiqueta="Forma de liberación" id="filtroForma">
            <input id="filtroForma" list="formas-liberacion" className={claseEntrada} maxLength="60"
              placeholder="Ej. Examen global" value={busqueda.formaLiberacion}
              onChange={cambiarFiltro('formaLiberacion')} />
            <datalist id="formas-liberacion">
              {['Curso', 'Examen de colocación', 'Examen de cuatro habilidades', 'Examen global', 'Lengua materna', 'Registro histórico']
                .map((f) => <option key={f} value={f} />)}
            </datalist>
          </Campo>
          <div className="flex gap-2">
            <Boton type="submit"><Search className="h-4 w-4" /> Buscar</Boton>
            {hayFiltros && (
              <Boton variante="secundario" type="button" onClick={() => {
                setBusqueda(FILTROS_VACIOS);
                setFiltro(FILTROS_VACIOS);
                setExpediente(null);
                cargar(FILTROS_VACIOS, 0);
              }}>
                Limpiar
              </Boton>
            )}
          </div>
        </form>
      </Tarjeta>

      {error && <Alerta tipo="error">{error}</Alerta>}
      {aviso && <Alerta tipo="ok">{aviso}</Alerta>}

      <Tarjeta
        titulo="Alumnos registrados"
        icono={UserRound}
        extra={pagina ? `${pagina.totalElements} en total` : ''}
        acciones={puedeEditar ? (
          <div className="flex flex-wrap gap-2">
            <Boton variante="secundario" type="button" onClick={() => { setAviso(''); setModal({ tipo: 'elegir-carga' }); }}>
              <Upload className="h-4 w-4" /> Importar desde Excel
            </Boton>
            <Boton type="button" onClick={() => { setAviso(''); setModal({ tipo: 'nuevo' }); }}>
              <UserPlus className="h-4 w-4" /> Nuevo alumno
            </Boton>
          </div>
        ) : null}
      >
        {cargando ? <Cargando /> : !pagina?.content.length ? (
          <p className="text-sm text-tenue">No se encontraron alumnos con esos criterios.</p>
        ) : (
          <>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[860px] text-left text-sm">
                <thead className="border-b border-linea text-xs text-tenue">
                  <tr>
                    <th className="px-2 py-2">Número de control</th>
                    <th className="px-2 py-2">Nombre</th>
                    <th className="px-2 py-2">Sexo</th>
                    <th className="px-2 py-2">Carrera</th>
                    <th className="px-2 py-2">Semestre</th>
                    <th className="px-2 py-2">Nivel actual</th>
                    <th className="px-2 py-2">Estatus</th>
                    <th className="px-2 py-2">Acciones</th>
                  </tr>
                </thead>
                <tbody>
                  {pagina.content.map((alumno) => (
                    <tr className="border-b border-linea last:border-0 align-top" key={alumno.numeroControl}>
                      <td className="px-2 py-3 font-medium">{alumno.numeroControl}</td>
                      <td className="px-2 py-3">{alumno.nombreCompleto}</td>
                      <td className="px-2 py-3">{SEXO[alumno.sexo] || '—'}</td>
                      <td className="px-2 py-3">{alumno.carrera}</td>
                      <td className="px-2 py-3">
                        {alumno.semestre ? `${alumno.semestre}.º` : "—"}
                        <div className="text-[11px] text-tenue" title="Fecha en que el alumno confirmó su semestre">
                          {fechaCorta(alumno.fechaActualizacionSemestre) ? `Act. ${fechaCorta(alumno.fechaActualizacionSemestre)}` : 'Sin confirmar'}
                        </div>
                      </td>
                      <td className="px-2 py-3 font-medium">
                        {alumno.numeroNivelActual === 0 ? (
                          <>
                            <span className="font-semibold text-ok">Idioma acreditado</span>
                            <div className="text-[11.5px] font-medium text-marino">
                              {alumno.formaLiberacion ? `Liberado por: ${alumno.formaLiberacion}` : 'Acreditado'}
                            </div>
                          </>
                        ) : (
                          <>
                            {alumno.nivelActual}
                            {alumno.formaLiberacion && (
                              <div className="text-[11px] font-normal text-tenue">{alumno.formaLiberacion}</div>
                            )}
                          </>
                        )}
                      </td>
                      <td className="px-2 py-3">
                        <Insignia estatus={alumno.estatus === 'Baja' ? 'Rechazado' : 'Validado'}>{alumno.estatus}</Insignia>
                      </td>
                      <td className="px-2 py-3">
                        <div className="flex flex-wrap gap-x-3 gap-y-1 text-[13px]">
                          <button className="font-semibold text-marino underline" type="button" onClick={() => seleccionar(alumno)}>
                            Expediente
                          </button>
                          {puedeEditar && alumno.estatus !== 'Baja' && (
                            <>
                              {alumno.numeroNivelActual !== 0 && (
                                <button className="inline-flex items-center gap-1 text-marino hover:underline" type="button"
                                  onClick={() => { setAviso(''); setModal({ tipo: 'liberar', alumno }); }}>
                                  <Unlock className="h-3.5 w-3.5" /> Liberar idioma
                                </button>
                              )}
                              <button className="inline-flex items-center gap-1 text-error hover:underline" type="button"
                                onClick={() => { setAviso(''); setModal({ tipo: 'baja', alumno }); }}>
                                <UserMinus className="h-3.5 w-3.5" /> Baja
                              </button>
                            </>
                          )}
                          {puedeEditar && alumno.estatus === 'Baja' && (
                            <button className="inline-flex items-center gap-1 text-ok hover:underline" type="button"
                              onClick={() => reactivarAlumno(alumno.numeroControl).then(() => recargar(`${alumno.nombreCompleto} reactivado.`)).catch((e) => setError(mensajeError(e)))}>
                              <RotateCcw className="h-3.5 w-3.5" /> Reactivar
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="mt-4 flex items-center justify-between gap-3">
              <span className="text-xs text-tenue">
                Página {pagina.number + 1} de {Math.max(pagina.totalPages, 1)}
              </span>
              <div className="flex gap-2">
                <Boton variante="secundario" type="button" disabled={pagina.first} onClick={() => cargar(filtro, pagina.number - 1)}>
                  Anterior
                </Boton>
                <Boton variante="secundario" type="button" disabled={pagina.last} onClick={() => cargar(filtro, pagina.number + 1)}>
                  Siguiente
                </Boton>
              </div>
            </div>
          </>
        )}
      </Tarjeta>

      {cargandoExpediente && <Cargando texto="Cargando expediente…" />}
      {errorExpediente && <Alerta tipo="error">{errorExpediente}</Alerta>}
      {expediente && (
        <div id="expediente-integral" className="scroll-mt-4"><ExpedienteIntegral datos={expediente} /></div>
      )}

      {modal?.tipo === 'nuevo' && (
        <ModalNuevoAlumno carreras={carreras} onCerrar={() => setModal(null)}
          onCreado={(nc) => recargar(`Alumno ${nc} creado. Su contraseña inicial es su número de control.`)} />
      )}
      {modal?.tipo === 'elegir-carga' && (
        <ModalElegirCarga onCerrar={() => setModal(null)} onElegir={(tipo) => setModal({ tipo, desdeEleccion: true })} />
      )}
      {modal?.tipo === 'importar' && (
        <ModalImportar
          onCerrar={() => setModal(null)}
          onVolver={modal.desdeEleccion ? volverAElegir : null}
          onImportado={(r) => recargar(
            `Importación: ${r.creados} alumnos nuevos, ${r.actualizados ?? 0} actualizados, ` +
            `${r.nivelesImportados} niveles en el historial` +
            (r.gruposImportados ? `, ${r.gruposImportados} grupos y ${r.inscripcionesImportadas} inscripciones` : '') +
            (r.errores.length ? `; ${r.errores.length} fila(s) pendientes.` : '.')
          )}
        />
      )}
      {modal?.tipo === 'completar' && (
        <ModalCompletarDatos
          onCerrar={() => setModal(null)}
          onVolver={modal.desdeEleccion ? volverAElegir : null}
          onCompletado={(r) => recargar(
            `Completar datos: ${r.camposCompletados} campos en ${r.alumnosActualizados} alumnos, ` +
            `${r.facilitadoresActualizados} facilitadores y ${r.gruposActualizados} grupos; ` +
            `${r.horariosAgregados} horarios agregados.`
          )}
        />
      )}
      {modal?.tipo === 'importar-operativo' && (
        <ModalImportarOperativo
          onCerrar={() => setModal(null)}
          onVolver={modal.desdeEleccion ? volverAElegir : null}
          onImportado={(r) => recargar(
            `Carga operativa: ${r.alumnosCreados} alumnos, ${r.facilitadoresCreados} facilitadores, ` +
            `${r.gruposCreados} grupos y ${r.horariosCreados} horarios creados.`
          )}
        />
      )}
      {modal?.tipo === 'baja' && (
        <ModalBaja alumno={modal.alumno} onCerrar={() => setModal(null)}
          onBaja={() => recargar(`${modal.alumno.nombreCompleto} dado de baja.`)} />
      )}
      {modal?.tipo === 'liberar' && (
        <ModalLiberar alumno={modal.alumno} onCerrar={() => setModal(null)}
          onLiberado={(forma) => recargar(`Idioma liberado (${forma}) para ${modal.alumno.nombreCompleto}.`)} />
      )}
    </div>
  );
}

/** Alta de un alumno por el coordinador. */
function ModalNuevoAlumno({ carreras, onCerrar, onCreado }) {
  const [f, setF] = useState({ numeroControl: '', nombre: '', apellidoPaterno: '', apellidoMaterno: '', sexo: '', idCarrera: '', semestre: '', correo: '', hablaLenguaMaterna: false });
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  const cambiar = (c) => (e) => setF({ ...f, [c]: c === 'numeroControl' ? e.target.value.toUpperCase() : e.target.value });

  const enviar = async (e) => {
    e.preventDefault();
    setEnviando(true);
    setError('');
    try {
      const creado = await crearAlumno({
        ...f, idCarrera: Number(f.idCarrera), semestre: Number(f.semestre),
        correo: f.correo.trim() || null, apellidoMaterno: f.apellidoMaterno.trim() || null,
      });
      onCreado(creado.numeroControl);
    } catch (err) {
      setError(mensajeError(err));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <Modal titulo="Nuevo alumno" onCerrar={onCerrar} ancho="max-w-xl">
      {error && <Alerta tipo="error" className="mb-3">{error}</Alerta>}
      <form className="grid gap-3 sm:grid-cols-2" onSubmit={enviar}>
        <Campo etiqueta="Número de control" id="nc">
          <input id="nc" className={claseEntrada} maxLength="10" required value={f.numeroControl} onChange={cambiar('numeroControl')} />
        </Campo>
        <Campo etiqueta="Sexo" id="sexo">
          <select id="sexo" className={claseEntrada} required value={f.sexo} onChange={cambiar('sexo')}>
            <option value="">Selecciona…</option>
            <option value="H">Hombre</option>
            <option value="M">Mujer</option>
          </select>
        </Campo>
        <Campo etiqueta="Nombre(s)" id="nombre">
          <input id="nombre" className={claseEntrada} maxLength="60" required value={f.nombre} onChange={cambiar('nombre')} />
        </Campo>
        <Campo etiqueta="Apellido paterno" id="app">
          <input id="app" className={claseEntrada} maxLength="40" required value={f.apellidoPaterno} onChange={cambiar('apellidoPaterno')} />
        </Campo>
        <Campo etiqueta="Apellido materno" id="apm">
          <input id="apm" className={claseEntrada} maxLength="40" value={f.apellidoMaterno} onChange={cambiar('apellidoMaterno')} />
        </Campo>
        <Campo etiqueta="Carrera" id="carr">
          <select id="carr" className={claseEntrada} required value={f.idCarrera} onChange={cambiar('idCarrera')}>
            <option value="">Selecciona…</option>
            {carreras.map((c) => <option key={c.id} value={c.id}>{c.nombre}</option>)}
          </select>
        </Campo>
        <Campo etiqueta="Semestre" id="sem">
          <select id="sem" className={claseEntrada} required value={f.semestre} onChange={cambiar('semestre')}>
            <option value="">Selecciona…</option>
            {SEMESTRES.map((s) => <option key={s} value={s}>{s}.º</option>)}
          </select>
        </Campo>
        <Campo etiqueta="Correo (opcional)" id="correo">
          <input id="correo" type="email" className={claseEntrada} maxLength="80" value={f.correo} onChange={cambiar('correo')} />
        </Campo>
        <label className="flex items-center gap-2 text-[13px] sm:col-span-2">
          <input type="checkbox" checked={f.hablaLenguaMaterna} onChange={(e) => setF({ ...f, hablaLenguaMaterna: e.target.checked })} />
          Habla una lengua materna (náhuatl, me&apos;phaa, tu&apos;un savi…)
        </label>
        <p className="text-xs text-tenue sm:col-span-2">La contraseña inicial será el número de control; cámbiala después en «Cuentas y contraseñas».</p>
        <div className="mt-1 flex justify-end gap-2 sm:col-span-2">
          <Boton variante="secundario" type="button" onClick={onCerrar}>Cancelar</Boton>
          <Boton type="submit" disabled={enviando}>{enviando ? 'Creando…' : 'Crear alumno'}</Boton>
        </div>
      </form>
    </Modal>
  );
}

/** Carga masiva desde Excel con reporte de resultado. */
function ModalImportar({ onCerrar, onVolver, onImportado }) {
  const [archivo, setArchivo] = useState(null);
  const [matricula, setMatricula] = useState(null);
  const [resultado, setResultado] = useState(null);
  const [error, setError] = useState('');
  const [subiendo, setSubiendo] = useState(false);

  const plantilla = async () => {
    try {
      guardarArchivo(await descargarPlantillaAlumnos(), 'plantilla-historico-sigclem.xlsx');
    } catch (e) { setError(mensajeError(e)); }
  };

  const subir = async () => {
    if (!archivo) return;
    setSubiendo(true);
    setError('');
    setResultado(null);
    try {
      const r = await cargarAlumnosExcel(archivo, matricula);
      setResultado(r);
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setSubiendo(false);
    }
  };

  /** Descarga las filas pendientes (CSV que abre Excel) para corregirlas en el concentrado y volver a subirlas. */
  const descargarPendientes = () => {
    const escapar = (v) => `"${String(v ?? '').replace(/"/g, '""')}"`;
    const lineas = [['Fila del Excel', 'Número de control', 'Motivo'],
      ...resultado.errores.map((x) => [x.fila, x.numeroControl, x.mensaje ?? x.motivo])];
    const blob = new Blob(['﻿' + lineas.map((l) => l.map(escapar).join(',')).join('\r\n')],
      { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'pendientes-importacion.csv';
    a.click();
    URL.revokeObjectURL(url);
  };

  const claseArchivo = 'block w-full text-[13px] file:mr-3 file:rounded-lg file:border-0 file:bg-marino file:px-3 file:py-2 file:text-white';
  const esConcentrado = resultado && (resultado.actualizados > 0 || resultado.conMatricula > 0 || resultado.nivelesAcreditados > 0
    || (resultado.gruposImportados === 0 && resultado.inscripcionesImportadas === 0));

  return (
    <Modal titulo="Histórico académico desde Excel" onCerrar={() => (resultado ? onImportado(resultado) : !subiendo && onCerrar())} ancho="max-w-xl">
      <VolverAElegir onVolver={resultado ? null : onVolver} deshabilitado={subiendo} />
      <p className="text-[13px]">
        Sube el <b>concentrado de la coordinación</b> tal como está (una fila por alumno con Básico 1 … Intermedio 5,
        Intensivo y Liberado). No hace falta llenar los espacios en blanco: lo que no venga se guarda vacío. Las
        calificaciones de 70 o más acreditan el nivel y «Liberado = Sí» libera el idioma.
      </p>
      <p className="mt-2 text-[13px] text-tenue">
        También acepta la plantilla completa (hojas Grupos, Inscripciones y Horarios) para periodos con todos los datos.
        Puedes volver a subir el archivo: no se duplica nada. No se generan cargos ni comprobantes. Si los alumnos ya
        existen y solo faltan datos, usa «Completar datos faltantes».
      </p>
      <Boton variante="secundario" type="button" className="mt-3" onClick={plantilla}>
        <Download className="h-4 w-4" /> Descargar plantilla completa
      </Boton>
      <div className="mt-4 grid gap-3">
        <Campo etiqueta="Concentrado histórico o plantilla completa (.xlsx)" id="archivo-historico">
          <input id="archivo-historico" type="file" accept=".xlsx" className={claseArchivo}
            onChange={(e) => { setArchivo(e.target.files[0]); setResultado(null); }} />
        </Campo>
        <Campo etiqueta="Matrícula de control escolar (opcional, .xlsx)" id="archivo-matricula">
          <input id="archivo-matricula" type="file" accept=".xlsx" className={claseArchivo}
            onChange={(e) => { setMatricula(e.target.files[0] || null); setResultado(null); }} />
          <span className="mt-1 block text-xs text-tenue">
            Completa semestre, sexo, correo y lengua indígena de los alumnos que siguen inscritos. A quien no aparezca
            (egresados, desertores) se le dejan vacíos.
          </span>
        </Campo>
      </div>
      {error && <Alerta tipo="error" className="mt-3">{error}</Alerta>}
      {resultado && (
        <div className="mt-3">
          <Alerta tipo={resultado.errores.length ? 'aviso' : 'ok'}>
            <b>Resumen de importación:</b><br/>
            - Filas procesadas: {resultado.filas}<br/>
            - Alumnos nuevos: {resultado.creados}
            {esConcentrado ? (
              <>
                <br/>- Alumnos ya registrados actualizados: {resultado.actualizados}
                <br/>- Niveles guardados en el historial: {resultado.nivelesImportados}
                <br/>- Niveles acreditados en el avance: {resultado.nivelesAcreditados}
                <br/>- Alumnos con el idioma liberado: {resultado.liberados}
                <br/>- Alumnos completados con la matrícula: {resultado.conMatricula}
              </>
            ) : (
              <>
                <br/>- Grupos históricos procesados: {resultado.gruposImportados ?? 0}
                <br/>- Inscripciones históricas procesadas: {resultado.inscripcionesImportadas ?? 0}
                <br/>- Horarios agregados: {resultado.horariosImportados ?? 0}
                <br/>- Registros informativos: {resultado.nivelesImportados}
              </>
            )}
            {resultado.errores.length > 0 && (
              <><br/><b>{resultado.errores.length} fila(s) pendientes</b> (el resto sí se guardó). Corrígelas en el
                Excel y vuelve a subirlo:</>
            )}
          </Alerta>
          {resultado.errores.length > 0 && (
            <ul className="mt-2 max-h-40 overflow-y-auto rounded-lg border border-linea text-xs">
              {resultado.errores.map((x, i) => (
                <li key={`${x.fila}-${i}`} className="border-b border-linea px-3 py-1.5 last:border-0">
                  <b>Fila {x.fila}</b>{x.numeroControl ? ` (${x.numeroControl})` : ''}: {x.mensaje ?? x.motivo}
                </li>
              ))}
            </ul>
          )}
          {resultado.errores.length > 0 && (
            <Boton variante="secundario" type="button" className="mt-2" onClick={descargarPendientes}>
              <Download className="h-4 w-4" /> Descargar pendientes
            </Boton>
          )}
        </div>
      )}
      <div className="mt-5 flex justify-end gap-2">
        {resultado ? (
          <Boton type="button" onClick={() => onImportado(resultado)}>Terminar</Boton>
        ) : (
          <>
            <Boton variante="secundario" type="button" onClick={onCerrar}>Cancelar</Boton>
            <Boton type="button" disabled={!archivo || subiendo} onClick={subir}>{subiendo ? 'Procesando...' : 'Subir e Importar'}</Boton>
          </>
        )}
      </div>
    </Modal>
  );
}

function ModalCompletarDatos({ onCerrar, onVolver, onCompletado }) {
  const [archivo, setArchivo] = useState(null);
  const [resultado, setResultado] = useState(null);
  const [error, setError] = useState('');
  const [subiendo, setSubiendo] = useState(false);

  const descargarPlantilla = async () => {
    try {
      guardarArchivo(await descargarPlantillaAlumnos(), 'plantilla-historico-sigclem.xlsx');
    } catch (e) {
      setError(mensajeError(e));
    }
  };

  const subir = async () => {
    if (!archivo) return;
    setSubiendo(true);
    setError('');
    setResultado(null);
    try {
      setResultado(await completarDatosExcel(archivo));
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setSubiendo(false);
    }
  };

  return (
    <Modal titulo="Completar datos faltantes" onCerrar={() => (resultado ? onCompletado(resultado) : !subiendo && onCerrar())} ancho="max-w-xl">
      <VolverAElegir onVolver={resultado ? null : onVolver} deshabilitado={subiendo} />
      <p className="text-[13px]">
        Usa la misma plantilla completa del histórico (hojas Alumnos, Grupos y Horarios); al descargarla ya trae los
        datos actuales, así que solo llena lo que falta. Solo se rellenan campos vacíos en
        alumnos, facilitadores y grupos; cualquier dato ya guardado se conserva. Los horarios nuevos se agregan sin
        borrar ni reemplazar los actuales. No se crean alumnos, facilitadores, grupos o inscripciones, y no se cambian
        calificaciones ni avance académico.
      </p>
      <Boton variante="secundario" type="button" className="mt-3" onClick={descargarPlantilla}>
        <Download className="h-4 w-4" /> Descargar plantilla con los datos actuales
      </Boton>
      <div className="mt-4">
        <input type="file" accept=".xlsx" onChange={(e) => {
          setArchivo(e.target.files[0]);
          setResultado(null);
        }} className="block w-full text-[13px] file:mr-3 file:rounded-lg file:border-0 file:bg-marino file:px-3 file:py-2 file:text-white" />
      </div>
      {error && <Alerta tipo="error" className="mt-3">{error}</Alerta>}
      {resultado && (
        <div className="mt-3">
          <Alerta tipo={resultado.errores.length ? 'aviso' : 'ok'}>
            <b>Resumen:</b><br/>
            - Filas procesadas: {resultado.filasProcesadas}<br/>
            - Campos vacíos completados: {resultado.camposCompletados}<br/>
            - Alumnos actualizados: {resultado.alumnosActualizados}<br/>
            - Facilitadores actualizados: {resultado.facilitadoresActualizados}<br/>
            - Grupos actualizados: {resultado.gruposActualizados}<br/>
            - Horarios agregados: {resultado.horariosAgregados}
            {resultado.errores.length > 0 && <><br/><b>Filas con error: {resultado.errores.length}</b></>}
          </Alerta>
          {resultado.errores.length > 0 && (
            <ul className="mt-2 max-h-40 overflow-y-auto rounded-lg border border-linea text-xs">
              {resultado.errores.map((x, i) => (
                <li key={`${x.fila}-${i}`} className="border-b border-linea px-3 py-1.5 last:border-0">
                  <b>Fila {x.fila}</b>{x.numeroControl ? ` (${x.numeroControl})` : ''}: {x.mensaje}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      <div className="mt-5 flex justify-end gap-2">
        {resultado ? (
          <Boton type="button" onClick={() => onCompletado(resultado)}>Terminar</Boton>
        ) : (
          <>
            <Boton variante="secundario" type="button" onClick={onCerrar}>Cancelar</Boton>
            <Boton type="button" disabled={!archivo || subiendo} onClick={subir}>
              {subiendo ? 'Procesando...' : 'Completar campos vacíos'}
            </Boton>
          </>
        )}
      </div>
    </Modal>
  );
}

function ModalImportarOperativo({ onCerrar, onVolver, onImportado }) {
  const [archivo, setArchivo] = useState(null);
  const [resultado, setResultado] = useState(null);
  const [error, setError] = useState('');
  const [subiendo, setSubiendo] = useState(false);

  const descargarPlantilla = async () => {
    try {
      guardarArchivo(await descargarPlantillaOperativa(), 'plantilla-carga-operativa-sigclem.xlsx');
    } catch (e) {
      setError(mensajeError(e));
    }
  };

  const importar = async () => {
    if (!archivo) return;
    setSubiendo(true);
    setError('');
    setResultado(null);
    try {
      setResultado(await cargarImportacionOperativa(archivo));
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setSubiendo(false);
    }
  };

  const descargarCredenciales = () => {
    const escapar = (valor) => {
      let texto = String(valor ?? '');
      if (/^[=+\-@\t\r]/.test(texto)) texto = `'${texto}`;
      return `"${texto.replaceAll('"', '""')}"`;
    };
    const filas = [
      ['Facilitador', 'Usuario', 'Contraseña temporal'],
      ...resultado.credencialesTemporales.map((c) => [c.nombre, c.correo, c.contrasenaTemporal]),
    ];
    const contenido = `\uFEFF${filas.map((fila) => fila.map(escapar).join(',')).join('\r\n')}`;
    const url = URL.createObjectURL(new Blob([contenido], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = 'credenciales-temporales-facilitadores.csv';
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <Modal titulo="Inicio de periodo: carga operativa" onCerrar={() => (resultado ? onImportado(resultado) : !subiendo && onCerrar())} ancho="max-w-2xl">
      <VolverAElegir onVolver={resultado ? null : onVolver} deshabilitado={subiendo} />
      <p className="text-[13px]">
        Importa alumnos, facilitadores, grupos y horarios. <b>No</b> inscribe alumnos ni importa pagos,
        calificaciones o avance académico. Los niveles y carreras deben existir en sus catálogos.
      </p>
      <Boton variante="secundario" type="button" className="mt-3" onClick={descargarPlantilla}>
        <Download className="h-4 w-4" /> Descargar plantilla operativa
      </Boton>
      <div className="mt-4">
        <input type="file" accept=".xlsx" onChange={(e) => {
          setArchivo(e.target.files[0]);
          setResultado(null);
        }} className="block w-full text-[13px] file:mr-3 file:rounded-lg file:border-0 file:bg-marino file:px-3 file:py-2 file:text-white" />
      </div>
      {error && <Alerta tipo="error" className="mt-3">{error}</Alerta>}
      {resultado && (
        <div className="mt-3 grid gap-3">
          <Alerta tipo={resultado.errores.length ? 'aviso' : 'ok'}>
            <b>Resumen:</b><br/>
            - Filas procesadas: {resultado.filas}<br/>
            - Alumnos nuevos / ya registrados: {resultado.alumnosCreados} / {resultado.alumnosExistentes}<br/>
            - Facilitadores nuevos / ya registrados: {resultado.facilitadoresCreados} / {resultado.facilitadoresExistentes}<br/>
            - Grupos nuevos / ya registrados: {resultado.gruposCreados} / {resultado.gruposExistentes}<br/>
            - Horarios creados: {resultado.horariosCreados}
            {resultado.errores.length > 0 && <><br/><b>Filas con error: {resultado.errores.length}</b></>}
          </Alerta>
          {resultado.credencialesTemporales.length > 0 && (
            <div className="grid gap-2 rounded-lg border border-linea p-3 text-xs">
              <Alerta tipo="aviso">
                Guarda y entrega estas contraseñas de forma segura: solo se muestran en este resultado y cada facilitador
                deberá cambiarlas al iniciar sesión.
              </Alerta>
              <Boton variante="secundario" type="button" onClick={descargarCredenciales}>
                <Download className="h-4 w-4" /> Descargar credenciales temporales
              </Boton>
              <div className="max-h-40 overflow-auto">
                <table className="w-full text-left">
                  <thead><tr><th className="p-1">Facilitador</th><th className="p-1">Usuario</th><th className="p-1">Contraseña temporal</th></tr></thead>
                  <tbody>{resultado.credencialesTemporales.map((c) => (
                    <tr key={c.correo} className="border-t border-linea">
                      <td className="p-1">{c.nombre}</td><td className="p-1">{c.correo}</td>
                      <td className="p-1 font-mono">{c.contrasenaTemporal}</td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            </div>
          )}
          {resultado.errores.length > 0 && (
            <ul className="max-h-40 overflow-y-auto rounded-lg border border-linea text-xs">
              {resultado.errores.map((x, i) => (
                <li key={`${x.hoja}-${x.fila}-${i}`} className="border-b border-linea px-3 py-1.5 last:border-0">
                  <b>{x.hoja}, fila {x.fila}</b>{x.identificador ? ` (${x.identificador})` : ''}: {x.mensaje}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
      <div className="mt-5 flex justify-end gap-2">
        {resultado ? (
          <Boton type="button" onClick={() => onImportado(resultado)}>Terminar</Boton>
        ) : (
          <>
            <Boton variante="secundario" type="button" onClick={onCerrar}>Cancelar</Boton>
            <Boton type="button" disabled={!archivo || subiendo} onClick={importar}>
              {subiendo ? 'Procesando…' : 'Subir e importar'}
            </Boton>
          </>
        )}
      </div>
    </Modal>
  );
}

/** Baja reversible con motivo. */
function ModalBaja({ alumno, onCerrar, onBaja }) {
  const [motivo, setMotivo] = useState('');
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  const comunes = ['Terminó el curso de inglés', 'Abandonó el curso', 'Baja del instituto'];

  const confirmar = async () => {
    setEnviando(true);
    setError('');
    try {
      await darDeBajaAlumno(alumno.numeroControl, motivo.trim());
      onBaja();
    } catch (e) {
      setError(mensajeError(e));
      setEnviando(false);
    }
  };

  return (
    <Modal titulo="Dar de baja al alumno" onCerrar={onCerrar} ancho="max-w-md">
      <p className="text-[13px]">
        <b>{alumno.nombreCompleto}</b> ({alumno.numeroControl}) dejará de aparecer en las listas y reportes activos y no
        podrá iniciar sesión. Su historial se conserva y puedes reactivarlo después.
      </p>
      {error && <Alerta tipo="error" className="mt-3">{error}</Alerta>}
      <Campo etiqueta="Motivo de la baja" id="motivoBaja" className="mt-3">
        <input id="motivoBaja" list="motivos-baja" className={claseEntrada} maxLength="200" value={motivo}
          onChange={(e) => setMotivo(e.target.value)} placeholder="Escribe o elige un motivo" />
        <datalist id="motivos-baja">{comunes.map((m) => <option key={m} value={m} />)}</datalist>
      </Campo>
      <div className="mt-5 flex justify-end gap-2">
        <Boton variante="secundario" type="button" onClick={onCerrar}>Cancelar</Boton>
        <Boton variante="peligro" type="button" disabled={motivo.trim().length < 3 || enviando} onClick={confirmar}>
          {enviando ? 'Dando de baja…' : 'Dar de baja'}
        </Boton>
      </div>
    </Modal>
  );
}

/** Liberar la lengua extranjera por otra forma. */
function ModalLiberar({ alumno, onCerrar, onLiberado }) {
  const [forma, setForma] = useState('Examen global');
  const [detalle, setDetalle] = useState('');
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [otra, setOtra] = useState('');
  const formaFinal = forma === 'Otra' ? otra.trim() : forma;

  const confirmar = async () => {
    setEnviando(true);
    setError('');
    try {
      await liberarIdioma(alumno.numeroControl, formaFinal, detalle.trim() || null);
      onLiberado(formaFinal);
    } catch (e) {
      setError(mensajeError(e));
      setEnviando(false);
    }
  };

  return (
    <Modal titulo="Liberar la lengua extranjera" onCerrar={onCerrar} ancho="max-w-md">
      <p className="text-[13px]">
        <b>{alumno.nombreCompleto}</b> ({alumno.numeroControl}) quedará con el <b>idioma acreditado</b> por la forma que
        elijas. Todos sus niveles pendientes se marcarán como exentos con esa forma.
      </p>

      {error && <Alerta tipo="error" className="mt-3">{error}</Alerta>}

      <Campo etiqueta="Forma de liberación" id="forma" className="mt-3">
        <select id="forma" className={claseEntrada} value={forma} onChange={(e) => setForma(e.target.value)}>
          <option value="Examen global">Examen global (aprobado)</option>
          <option value="Lengua materna">Lengua materna (documento validado)</option>
          <option value="Otra">Otra (escribir cuál)</option>
        </select>
      </Campo>

      {forma === 'Otra' && (
        <Campo etiqueta="¿De qué forma se liberó?" id="otraForma" className="mt-3">
          <input id="otraForma" className={claseEntrada} maxLength="60" value={otra}
            onChange={(e) => setOtra(e.target.value)} placeholder="Ej. Constancia TOEFL, equivalencia…" />
        </Campo>
      )}

      {forma === 'Examen global' && (
        <Alerta tipo="info" className="mt-2">Confirma que el alumno pagó el examen global y lo aprobó.</Alerta>
      )}
      {forma === 'Lengua materna' && (
        <Alerta tipo="info" className="mt-2">Confirma que validaste el documento oficial de lengua materna.</Alerta>
      )}

      <Campo etiqueta="Detalle (opcional)" id="detalle" className="mt-3" ayuda="Folio, fecha o nota; queda en la auditoría.">
        <input id="detalle" className={claseEntrada} maxLength="200" value={detalle} onChange={(e) => setDetalle(e.target.value)} />
      </Campo>

      <div className="mt-5 flex justify-end gap-2">
        <Boton variante="secundario" type="button" onClick={onCerrar}>Cancelar</Boton>
        <Boton type="button" disabled={enviando || !formaFinal} onClick={confirmar}>{enviando ? 'Liberando…' : 'Liberar idioma'}</Boton>
      </div>
    </Modal>
  );
}