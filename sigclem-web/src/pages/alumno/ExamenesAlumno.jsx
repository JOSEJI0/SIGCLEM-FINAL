import { useEffect, useState } from 'react';
import { ClipboardList, PlusCircle, Calendar, CheckCircle2, Clock } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { listarExamenes, solicitarExamen } from '../../api/examenes';
import { mensajeError } from '../../api/cliente';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';

function formatearFecha(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleString('es-MX', {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

/** CU-04 Solicitar examen de colocación o de cuatro habilidades. */
export default function ExamenesAlumno() {
  const { perfil } = useAuth();
  const [examenes, setExamenes] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');
  const [exito, setExito] = useState('');

  // Formulario de nueva solicitud
  const [mostrarModal, setMostrarModal] = useState(false);
  const [tipo, setTipo] = useState('Colocación');
  const [fechaTentativa, setFechaTentativa] = useState('');
  const [enviando, setEnviando] = useState(false);
  const [errorModal, setErrorModal] = useState('');

  const cargar = () => {
    setCargando(true);
    listarExamenes({ numeroControl: perfil.usuario })
      .then((data) => {
        setExamenes(data);
        setError('');
      })
      .catch((err) => setError(mensajeError(err)))
      .finally(() => setCargando(false));
  };

  useEffect(() => {
    cargar();
  }, [perfil.usuario]);

  const handleSolicitar = async (e) => {
    e.preventDefault();
    setEnviando(true);
    setErrorModal('');
    try {
      await solicitarExamen({
        numeroControl: perfil.usuario,
        tipo,
        fechaHoraTentativa: fechaTentativa ? `${fechaTentativa}:00` : null,
      });
      setMostrarModal(false);
      setExito(`Solicitud de examen de ${tipo} enviada exitosamente.`);
      cargar();
    } catch (err) {
      setErrorModal(mensajeError(err));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold">Mis Exámenes</h1>
          <p className="text-xs text-tenue">
            Solicita y consulta el estatus de tus exámenes de colocación y cuatro habilidades (CU-04).
          </p>
        </div>
        <Boton onClick={() => { setMostrarModal(true); setErrorModal(''); }}>
          <PlusCircle className="h-4 w-4" /> Solicitar examen
        </Boton>
      </div>

      {exito && <Alerta tipo="ok">{exito}</Alerta>}
      {error && <Alerta tipo="error">{error}</Alerta>}

      {cargando ? (
        <Cargando />
      ) : examenes.length === 0 ? (
        <Tarjeta>
          <div className="py-8 text-center">
            <ClipboardList className="mx-auto mb-2 h-10 w-10 text-tenue opacity-60" />
            <p className="font-semibold text-tinta">Aún no has solicitado ningún examen.</p>
            <p className="mt-1 text-xs text-tenue">
              Puedes solicitar un examen de colocación o de cuatro habilidades para validar tu nivel.
            </p>
          </div>
        </Tarjeta>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {examenes.map((ex) => (
            <Tarjeta key={ex.id} titulo={`Examen de ${ex.tipo}`} icono={ClipboardList}
              extra={<Insignia estatus={ex.concluido ? 'Concluido' : 'Programada'} />}>
              <div className="grid gap-3 text-[13px]">
                <div className="flex items-center gap-2 text-tenue">
                  <Calendar className="h-4 w-4 shrink-0" />
                  <span><b>Fecha y hora:</b> {formatearFecha(ex.fechaHoraAplicacion)}</span>
                </div>

                {ex.concluido ? (
                  <>
                    <div className="rounded-lg bg-[#F0FDF4] p-3 text-[#155E3D]">
                      <div className="flex items-center gap-2 font-bold text-sm">
                        <CheckCircle2 className="h-4 w-4" /> Nivel Asignado: {ex.nombreNivelAsignado}
                      </div>
                      <div className="mt-1 text-xs">Puntaje total: <b>{ex.puntajeTotal} puntos</b></div>
                      {ex.observaciones && <div className="mt-1.5 text-xs italic">«{ex.observaciones}»</div>}
                    </div>

                    {ex.criterios && ex.criterios.length > 0 && (
                      <div className="mt-1">
                        <p className="mb-1 text-xs font-semibold text-tenue">Desglose por criterio:</p>
                        <div className="grid grid-cols-2 gap-2 text-xs">
                          {ex.criterios.map((c) => (
                            <div key={c.idCriterio} className="rounded border border-linea p-2">
                              <span className="font-semibold">{c.nombreCriterio}:</span> {c.puntaje}/6 pts
                              {c.nota && <p className="mt-0.5 text-[11px] text-tenue">{c.nota}</p>}
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </>
                ) : (
                  <div className="flex items-center gap-2 rounded-lg bg-aviso-fondo p-3 text-xs text-aviso">
                    <Clock className="h-4 w-4 shrink-0" />
                    <span>Tu examen está pendiente de aplicación y captura de resultados por parte de coordinación.</span>
                  </div>
                )}
              </div>
            </Tarjeta>
          ))}
        </div>
      )}

      {/* Modal de Solicitud de Examen (CU-04) */}
      {mostrarModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4">
          <div className="w-full max-w-md rounded-2xl border border-linea bg-white p-6 shadow-xl">
            <h2 className="flex items-center gap-2 text-lg font-bold">
              <ClipboardList className="h-5 w-5 text-marino" /> Solicitar Examen
            </h2>
            <p className="mt-1 text-xs text-tenue">
              Selecciona el tipo de examen a presentar (CU-04).
            </p>

            {errorModal && <div className="mt-3"><Alerta tipo="error">{errorModal}</Alerta></div>}

            <form onSubmit={handleSolicitar} className="mt-4 grid gap-3.5">
              <Campo etiqueta="Tipo de examen" id="tipo">
                <select id="tipo" className={claseEntrada} value={tipo} onChange={(e) => setTipo(e.target.value)}>
                  <option value="Colocación">Examen de Colocación</option>
                  <option value="Cuatro habilidades">Examen de Cuatro Habilidades</option>
                </select>
              </Campo>

              <Campo etiqueta="Fecha y hora sugerida (opcional)" id="fechaTentativa"
                ayuda="Coordinación confirmará o reprogramará la fecha oficial.">
                <input id="fechaTentativa" type="datetime-local" className={claseEntrada}
                  value={fechaTentativa} onChange={(e) => setFechaTentativa(e.target.value)} />
              </Campo>

              <div className="mt-2 flex justify-end gap-2">
                <Boton variante="secundario" type="button" onClick={() => setMostrarModal(false)} disabled={enviando}>
                  Cancelar
                </Boton>
                <Boton type="submit" disabled={enviando}>
                  {enviando ? 'Enviando…' : 'Enviar solicitud'}
                </Boton>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
