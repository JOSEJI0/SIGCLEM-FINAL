import { useEffect, useState } from 'react';
import { Calendar, CheckCircle2, ClipboardCheck, Clock, PlusCircle } from 'lucide-react';
import {
  listarMisDiagnosticos, listarNotificacionesRecibidas, solicitarDiagnostico,
} from '../../api/diagnosticos';
import { mensajeError } from '../../api/cliente';
import { Alerta, Boton, Cargando, Insignia, Tarjeta } from '../../components/ui';

function formatearFecha(fecha) {
  if (!fecha) return 'Pendiente de programación';
  return new Date(fecha).toLocaleString('es-MX', { dateStyle: 'medium', timeStyle: 'short' });
}

export default function Diagnostico() {
  const [diagnosticos, setDiagnosticos] = useState([]);
  const [notificaciones, setNotificaciones] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState('');
  const [exito, setExito] = useState('');

  const cargar = () => {
    setCargando(true);
    Promise.all([listarMisDiagnosticos(), listarNotificacionesRecibidas()])
      .then(([misDiagnosticos, recibidas]) => {
        setDiagnosticos(misDiagnosticos);
        setNotificaciones(recibidas.filter((item) => item.asunto.includes('diagnóstica')));
      })
      .catch((err) => setError(mensajeError(err)))
      .finally(() => setCargando(false));
  };

  useEffect(() => {
    cargar();
  }, []);

  const solicitar = async () => {
    setEnviando(true);
    setError('');
    try {
      await solicitarDiagnostico();
      setExito('Tu solicitud de evaluación diagnóstica fue registrada.');
      cargar();
    } catch (err) {
      setError(mensajeError(err));
    } finally {
      setEnviando(false);
    }
  };

  const tienePendiente = diagnosticos.some((item) => item.estatus !== 'Concluida');

  return (
    <div className="grid gap-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-xl font-bold">Evaluación de diagnóstico</h1>
          <p className="text-xs text-tenue">
            Es opcional; su resultado es orientativo y no modifica tu avance de nivel.
            Consulta aquí la programación y el resultado.
          </p>
        </div>
        <Boton onClick={solicitar} disabled={enviando || tienePendiente}>
          <PlusCircle className="h-4 w-4" />
          {enviando ? 'Enviando…' : tienePendiente ? 'Solicitud pendiente' : 'Solicitar evaluación'}
        </Boton>
      </div>

      {exito && <Alerta tipo="ok">{exito}</Alerta>}
      {error && <Alerta tipo="error">{error}</Alerta>}
      {notificaciones.length > 0 && (
        <Tarjeta titulo="Notificaciones de tu evaluación" icono={ClipboardCheck}>
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
      {cargando ? <Cargando /> : diagnosticos.length === 0 ? (
        <Tarjeta>
          <div className="py-8 text-center">
            <ClipboardCheck className="mx-auto mb-2 h-10 w-10 text-tenue opacity-60" />
            <p className="font-semibold">Aún no tienes solicitudes de evaluación diagnóstica.</p>
            <p className="mt-1 text-xs text-tenue">Solicita una evaluación para que coordinación pueda programarla.</p>
          </div>
        </Tarjeta>
      ) : (
        <div className="grid gap-4 md:grid-cols-2">
          {diagnosticos.map((item) => (
            <Tarjeta key={item.id} titulo={`Solicitud #${item.id}`} icono={ClipboardCheck}
              extra={<Insignia estatus={item.estatus} />}>
              <div className="grid gap-3 text-[13px]">
                <div className="flex items-center gap-2 text-tenue">
                  <Calendar className="h-4 w-4" />
                  <span><b>Fecha:</b> {formatearFecha(item.fechaHoraAplicacion)}</span>
                </div>
                {item.enlace && (
                  <a className="break-all text-marino underline" href={item.enlace} target="_blank" rel="noreferrer">
                    Abrir enlace de evaluación
                  </a>
                )}
                {item.estatus === 'Concluida' ? (
                  <div className="rounded-lg bg-[#F0FDF4] p-3 text-[#155E3D]">
                    <div className="flex items-center gap-2 font-bold">
                      <CheckCircle2 className="h-4 w-4" />
                      Resultado: {item.recomendacion}
                    </div>
                    <p className="mt-1 text-xs">Puntaje: <b>{item.puntaje}/100</b></p>
                    {item.observaciones && <p className="mt-1.5 text-xs">{item.observaciones}</p>}
                  </div>
                ) : (
                  <div className="flex items-center gap-2 rounded-lg bg-aviso-fondo p-3 text-xs text-aviso">
                    <Clock className="h-4 w-4 shrink-0" />
                    {item.estatus === 'Programada'
                      ? 'Tu evaluación ya tiene fecha y enlace asignados.'
                      : 'Tu solicitud está pendiente de programación por coordinación.'}
                  </div>
                )}
              </div>
            </Tarjeta>
          ))}
        </div>
      )}
    </div>
  );
}
