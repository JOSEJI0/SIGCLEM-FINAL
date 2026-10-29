import { useCallback, useEffect, useState } from 'react';
import { Search, UserRound, UserPlus, Upload, UserMinus, RotateCcw, Unlock, Download } from 'lucide-react';
import {
  buscarAlumnos, obtenerExpedienteIntegral, crearAlumno, darDeBajaAlumno, reactivarAlumno, liberarIdioma,
  cargarAlumnosExcel, descargarPlantillaAlumnos,
} from '../../api/alumnos';
import { obtenerCarreras, obtenerNiveles } from '../../api/catalogos';
import ExpedienteIntegral from '../../components/ExpedienteIntegral';
import { mensajeError } from '../../api/cliente';
import { useAuth } from '../../auth/AuthContext';
import { esCoordinacion } from '../../auth/roles';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';
import { Modal } from '../../components/Modal';

const FILTROS_VACIOS = { idCarrera: '', numeroControl: '', nombre: '', nivel: '', semestre: '', estatus: '', formaLiberacion: '' };
const SEMESTRES = Array.from({ length: 12 }, (_, i) => i + 1);
const SEXO = { H: 'Hombre', M: 'Mujer' };

function fechaCorta(iso) {
  return iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('es-MX') : null;
}

/** Consulta, alta, baja y liberación de idioma de alumnos (CU-02, CU-16, CU-21; cambio 15). */
export default function Alumnos() {
  const { perfil } = useAuth();
  const puedeEditar = esCoordinacion(perfil.rol);
  const [busqueda, setBusqueda] = useState(FILTROS_VACIOS);
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
  const [modal, setModal] = useState(null); // { tipo: 'nuevo'|'importar'|'baja'|'liberar', alumno? }

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

  const cambiarFiltro = (campo) => (evento) => setBusqueda({
    ...busqueda, [campo]: campo === 'numeroControl' ? evento.target.value.toUpperCase() : evento.target.value,
  });
  const hayFiltros = Object.values(filtro).some((v) => v !== '');

  const recargar = (mensaje) => {
    setModal(null);
    setAviso(mensaje || '');
    cargar(filtro, pagina ? pagina.number : 0);
  };

  return (
    <div className="grid gap-[18px]">
      <Tarjeta titulo="Buscar alumnos" icono={Search}>
        <form className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[1.2fr_0.9fr_1.2fr_0.7fr_1fr_0.8fr_auto] lg:items-end" onSubmit={buscar}>
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
          <Campo etiqueta="Nombre" id="filtroNombre">
            <input id="filtroNombre" className={claseEntrada} maxLength="100" placeholder="Nombre o apellidos"
              value={busqueda.nombre} onChange={cambiarFiltro('nombre')} />
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
              <option value="0">Idioma acreditado</option>
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
              {['Curso', 'Examen de colocación', 'Examen de cuatro habilidades', 'Examen global', 'Lengua materna']
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
            <Boton variante="secundario" type="button" onClick={() => { setAviso(''); setModal({ tipo: 'importar' }); }}>
              <Upload className="h-4 w-4" /> Importar Excel
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
                        {alumno.semestre}.º
                        <div className="text-[11px] text-tenue" title="Fecha en que el alumno confirmó su semestre">
                          {fechaCorta(alumno.fechaActualizacionSemestre) ? `Act. ${fechaCorta(alumno.fechaActualizacionSemestre)}` : 'Sin confirmar'}
                        </div>
                      </td>
                      <td className="px-2 py-3 font-medium">{alumno.nivelActual}</td>
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
      {modal?.tipo === 'importar' && (
        <ModalImportar 
          onCerrar={() => setModal(null)} 
          onImportado={(r) => recargar(`✅ Importación exitosa: ${r.creados} alumnos procesados y ${r.nivelesImportados} niveles históricos guardados.`)} 
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
function ModalImportar({ onCerrar, onImportado }) {
  const [archivo, setArchivo] = useState(null);
  const [resultado, setResultado] = useState(null);
  const [error, setError] = useState('');
  const [subiendo, setSubiendo] = useState(false);

  const plantilla = async () => {
    try {
      const blob = await descargarPlantillaAlumnos();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'plantilla-alumnos.xlsx';
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) { setError(mensajeError(e)); }
  };

  const subir = async () => {
    if (!archivo) return;
    setSubiendo(true);
    setError('');
    setResultado(null);
    try {
      const r = await cargarAlumnosExcel(archivo);
      setResultado(r);
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setSubiendo(false);
    }
  };

  return (
    <Modal titulo="Importar alumnos desde Excel" onCerrar={onCerrar} ancho="max-w-xl">
      <p className="text-[13px]">
        Sube un archivo <b>.xlsx</b> con el concentrado histórico. Asegúrate de que el archivo contenga la hoja "General".
      </p>
      <Boton variante="secundario" type="button" className="mt-3" onClick={plantilla}>
        <Download className="h-4 w-4" /> Descargar plantilla
      </Boton>
      <div className="mt-4">
        <input type="file" accept=".xlsx" onChange={(e) => { setArchivo(e.target.files[0]); setResultado(null); }}
          className="block w-full text-[13px] file:mr-3 file:rounded-lg file:border-0 file:bg-marino file:px-3 file:py-2 file:text-white" />
      </div>
      {error && <Alerta tipo="error" className="mt-3">{error}</Alerta>}
      {resultado && (
        <div className="mt-3">
          <Alerta tipo={resultado.errores.length ? 'aviso' : 'ok'}>
            <b>Resumen de importación:</b><br/>
            - Filas procesadas: {resultado.filas}<br/>
            - Alumnos creados/actualizados: {resultado.creados}<br/>
            - Niveles históricos guardados: {resultado.nivelesImportados}
            {resultado.errores.length > 0 && <><br/><b>{resultado.errores.length} fila(s) con error:</b></>}
          </Alerta>
          {resultado.errores.length > 0 && (
            <ul className="mt-2 max-h-40 overflow-y-auto rounded-lg border border-linea text-xs">
              {resultado.errores.map((x) => (
                <li key={x.fila} className="border-b border-linea px-3 py-1.5 last:border-0">
                  <b>Fila {x.fila}</b>{x.numeroControl ? ` (${x.numeroControl})` : ''}: {x.motivo}
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
            <Boton type="button" disabled={!archivo || subiendo} onClick={subir}>{subiendo ? 'Procesando...' : 'Subir e Importar'}</Boton>
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