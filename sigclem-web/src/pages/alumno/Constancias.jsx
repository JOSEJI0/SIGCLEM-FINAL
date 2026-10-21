import { useEffect, useState } from 'react';
import { Award, Eye, FileCheck2 } from 'lucide-react';
import {
  consultarElegibilidadConstancia, descargarPdfConstancia, listarMisConstancias, solicitarConstancia,
} from '../../api/constancias';
import { mensajeError } from '../../api/cliente';
import ConstanciasAvance from '../../components/ConstanciasAvance';
import { Alerta, Boton, Cargando, Insignia, Tarjeta } from '../../components/ui';
import { useVisorDocumento } from '../../components/VisorDocumento';

function fecha(iso) {
  return iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('es-MX') : '—';
}


export default function Constancias() {
  const verDocumento = useVisorDocumento();
  const [elegibilidad, setElegibilidad] = useState(null);
  const [constancias, setConstancias] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState('');
  const [exito, setExito] = useState('');

  const cargar = () => Promise.all([consultarElegibilidadConstancia(), listarMisConstancias()])
    .then(([progreso, items]) => {
      setElegibilidad(progreso);
      setConstancias(items);
      setError('');
    })
    .catch((err) => setError(mensajeError(err)))
    .finally(() => setCargando(false));

  useEffect(() => {
    cargar();
  }, []);

  const activa = constancias.some((item) => ['Solicitada', 'Autorizada', 'Emitida'].includes(item.estatus));

  const solicitar = async () => {
    setEnviando(true);
    setError('');
    setExito('');
    try {
      await solicitarConstancia();
      setExito('Tu solicitud de constancia fue enviada a coordinación.');
      await cargar();
    } catch (err) {
      setError(mensajeError(err));
    } finally {
      setEnviando(false);
    }
  };

  const ver = (item) => verDocumento({
    titulo: `Constancia de acreditación · folio ${item.folio}`,
    nombre: `constancia-${item.folio}`,
    cargar: () => descargarPdfConstancia(item.id),
  });

  if (cargando) return <Cargando />;

  return (
    <div className="grid gap-5">
      <div>
        <h1 className="flex items-center gap-2 text-xl font-bold">
          <Award className="h-5 w-5" /> Mis constancias
        </h1>
        <p className="mt-1 text-sm text-tenue">
          Solicita tu constancia cuando hayas acreditado los diez niveles del programa de inglés.
        </p>
      </div>

      {error && <Alerta tipo="error">{error}</Alerta>}
      {exito && <Alerta tipo="ok">{exito}</Alerta>}

      {elegibilidad && (
        <Tarjeta titulo="Requisitos de acreditación" icono={FileCheck2}>
          <div className="grid gap-3 sm:grid-cols-3">
            <div>
              <p className="text-xs text-tenue">Niveles acreditados</p>
              <p className="font-semibold">{elegibilidad.nivelesAcreditados} de {elegibilidad.nivelesRequeridos}</p>
            </div>
            <div>
              <p className="text-xs text-tenue">Horas acumuladas</p>
              <p className="font-semibold">{elegibilidad.horasAcreditadas}</p>
            </div>
            <div>
              <p className="text-xs text-tenue">Nivel MCER previsto</p>
              <p className="font-semibold">{elegibilidad.nivelMcer || 'Pendiente de completar el programa'}</p>
            </div>
          </div>
          {!elegibilidad.elegible && (
            <div className="mt-3 rounded-lg bg-fondo p-3 text-sm">
              <p className="font-semibold">Niveles pendientes</p>
              <ul className="mt-1 list-inside list-disc text-tenue">
                {elegibilidad.nivelesFaltantes.map((nivel) => <li key={nivel}>{nivel}</li>)}
              </ul>
            </div>
          )}
          <div className="mt-4">
            <Boton onClick={solicitar} disabled={!elegibilidad.elegible || activa || enviando}>
              <Award className="h-4 w-4" />
              {enviando ? 'Enviando…' : activa ? 'Solicitud registrada' : 'Solicitar constancia'}
            </Boton>
            {activa && (
              <p className="mt-2 text-xs text-tenue">
                Ya existe una solicitud en trámite o una constancia emitida para inglés.
              </p>
            )}
          </div>
        </Tarjeta>
      )}

      <Tarjeta titulo="Historial de solicitudes" icono={Award}>
        {constancias.length === 0 ? (
          <p className="py-4 text-center text-sm text-tenue">Aún no tienes solicitudes de constancia.</p>
        ) : (
          <div className="grid gap-3">
            {constancias.map((item) => (
              <article key={item.id} className="rounded-lg border border-linea p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <h2 className="font-semibold">{item.idioma} - {item.nivelesAcreditados} niveles</h2>
                    <p className="text-xs text-tenue">Solicitud: {fecha(item.fechaSolicitud)}</p>
                  </div>
                  <Insignia
                    tono={item.estatus === 'Emitida' ? 'ok' : item.estatus === 'Rechazada' ? 'error' : 'aviso'}
                  >
                    {item.estatus}
                  </Insignia>
                </div>
                {item.estatus === 'Emitida' ? (
                  <div className="mt-3 flex flex-wrap items-center justify-between gap-2 text-sm">
                    <span>Folio {item.folio} · {item.totalHoras} horas · MCER {item.nivelMcer}</span>
                    <Boton variante="secundario" onClick={() => ver(item)}>
                      <Eye className="h-4 w-4" /> Ver PDF
                    </Boton>
                  </div>
                ) : item.estatus === 'Rechazada' ? (
                  <p className="mt-2 text-sm text-error">Motivo: {item.motivoRechazo}</p>
                ) : (
                  <p className="mt-2 text-sm text-tenue">Pendiente de revisión por coordinación.</p>
                )}
              </article>
            ))}
          </div>
        )}
      </Tarjeta>
      <ConstanciasAvance propias />
    </div>
  );
}
