import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarRange, Check, CheckCircle2, Clock, MapPin, Receipt, UserRound, Users } from 'lucide-react';
import { consultarOferta, elegirGrupo, retirarEleccion } from '../../api/grupos';
import { mensajeError } from '../../api/cliente';
import { Alerta, Boton, Cargando, Insignia, Tarjeta } from '../../components/ui';
import CalendarioSemanal, { DIAS, colorNivel, horaCorta } from '../../components/CalendarioSemanal';

const pesos = (v) => (v == null ? '—' : Number(v).toLocaleString('es-MX', { style: 'currency', currency: 'MXN' }));

function Horario({ horarios }) {
  return (
    <ul className="grid gap-1 text-[12.5px]">
      {horarios.map((h) => (
        <li key={`${h.diaSemana}-${h.horaInicio}`} className="flex items-center gap-1.5">
          <Clock className="h-3.5 w-3.5 text-tenue" aria-hidden />
          <span className="w-20 font-medium">{DIAS[h.diaSemana - 1]}</span>
          {horaCorta(h.horaInicio)} – {horaCorta(h.horaFin)}
        </li>
      ))}
    </ul>
  );
}

/**
 * Encuesta de inicio de semestre: el alumno ve los grupos abiertos de su siguiente nivel (sin saltar niveles)
 * y elige el curso y horario que le conviene. Coordinación lo asigna y se genera su papeleta de pago.
 */
export default function CursosDisponibles() {
  const [oferta, setOferta] = useState(null);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [ocupado, setOcupado] = useState(null);
  const [vista, setVista] = useState('tarjetas');

  const cargar = useCallback(() => {
    consultarOferta().then(setOferta).catch((e) => setError(mensajeError(e)));
  }, []);
  useEffect(() => { cargar(); }, [cargar]);

  const accion = async (idGrupo, fn, mensaje) => {
    setOcupado(idGrupo);
    setError('');
    setAviso('');
    try {
      setOferta(await fn(idGrupo));
      setAviso(mensaje);
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setOcupado(null);
    }
  };

  if (!oferta && !error) return <Cargando />;

  const eleccion = oferta?.grupos.find((g) => g.elegido);
  const sesiones = (oferta?.grupos || []).flatMap(({ grupo, elegido }) => grupo.horarios.map((h, i) => ({
    clave: `${grupo.id}-${i}`, dia: h.diaSemana, inicio: h.horaInicio, fin: h.horaFin,
    titulo: `${grupo.nivel} ${grupo.clave}`, detalle: grupo.modalidad,
    color: colorNivel(elegido ? 2 : grupo.nivelNumero), atenuada: eleccion && !elegido,
  })));

  return (
    <div className="grid gap-[18px]">
      {error && <Alerta tipo="error">{error}</Alerta>}
      {aviso && <Alerta tipo="ok">{aviso}</Alerta>}

      {oferta?.inscripciones.length > 0 && (
        <Tarjeta titulo="Tu grupo asignado" icono={CheckCircle2}>
          {oferta.inscripciones.map(({ grupo, montoCargo, saldoPendiente, estatusPago }) => (
            <div key={grupo.id} className="grid gap-4 md:grid-cols-[1fr_auto]">
              <div>
                <div className="text-lg font-semibold">{grupo.nivel} · Grupo {grupo.clave}</div>
                <div className="mb-2 text-[13px] text-tenue">
                  {grupo.periodo} · {grupo.modalidad} · {grupo.aula} · {grupo.facilitador}
                </div>
                <Horario horarios={grupo.horarios} />
              </div>
              <div className="rounded-lg border border-linea bg-fondo p-3 text-[13px] md:min-w-[230px]">
                <div className="mb-1 flex items-center gap-2 font-semibold"><Receipt className="h-4 w-4" /> Papeleta de pago</div>
                <div>Monto del curso: <b>{pesos(montoCargo)}</b></div>
                <div>Saldo pendiente: <b>{pesos(saldoPendiente)}</b></div>
                <div className="my-2"><Insignia estatus={estatusPago} /></div>
                {estatusPago !== 'Liquidada' && (
                  <Link to="/alumno/pagos" className="font-semibold text-marino hover:underline">Ir a Pagos para registrar tu comprobante →</Link>
                )}
              </div>
            </div>
          ))}
        </Tarjeta>
      )}

      <Tarjeta titulo={oferta?.nivel ? `Cursos disponibles de ${oferta.nivel}` : 'Cursos disponibles'} icono={CalendarRange}
        extra={oferta?.grupos.length ? `${oferta.grupos.length} grupo(s) abiertos` : ''}>
        {oferta?.mensaje && <Alerta tipo="info" className="mb-3">{oferta.mensaje}</Alerta>}
        {!oferta?.mensaje && oferta?.nivel && (
          <p className="mb-3 text-[13px] text-tenue">
            Según tu avance te corresponde cursar <b>{oferta.nivel}</b>. Elige el horario que más te convenga;
            coordinación confirmará tu lugar y generará tu papeleta de pago. Puedes cambiar tu elección mientras no
            te hayan asignado.
          </p>
        )}
        {oferta?.grupos.length === 0 && !oferta?.mensaje && (
          <p className="text-[13px] text-tenue">Coordinación aún no abre grupos de este nivel para el próximo periodo.</p>
        )}

        {oferta?.grupos.length > 0 && (
          <>
            <div className="mb-3 inline-flex rounded-lg border border-linea bg-fondo p-0.5 text-xs">
              {[['tarjetas', 'Grupos'], ['calendario', 'Calendario']].map(([v, t]) => (
                <button key={v} type="button" onClick={() => setVista(v)}
                  className={`rounded-md px-3 py-1 font-semibold ${vista === v ? 'bg-white text-marino shadow-sm' : 'text-tenue'}`}>{t}</button>
              ))}
            </div>
            {vista === 'calendario' ? (
              <CalendarioSemanal sesiones={sesiones} />
            ) : (
              <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
                {oferta.grupos.map(({ grupo, elegido, estatusEleccion, lleno }) => (
                  <article key={grupo.id}
                    className={`flex flex-col gap-3 rounded-xl border p-4 ${elegido ? 'border-marino ring-2 ring-marino/15' : 'border-linea'}`}>
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <div className="font-semibold">{grupo.nivel} · Grupo {grupo.clave}</div>
                        <div className="text-xs text-tenue">{grupo.periodo} · {grupo.modalidad}</div>
                      </div>
                      {elegido && <Insignia estatus={estatusEleccion === 'Asignada' ? 'Asignada' : 'Pendiente'}>{estatusEleccion === 'Asignada' ? 'Asignado' : 'Tu elección'}</Insignia>}
                    </div>
                    <Horario horarios={grupo.horarios} />
                    <div className="grid gap-1 text-[12.5px] text-tenue">
                      <span className="flex items-center gap-1.5"><UserRound className="h-3.5 w-3.5" /> {grupo.facilitador}</span>
                      <span className="flex items-center gap-1.5"><MapPin className="h-3.5 w-3.5" /> {grupo.aula}</span>
                      <span className="flex items-center gap-1.5"><Users className="h-3.5 w-3.5" /> {grupo.alumnosInscritos} de {grupo.cupo} lugares ocupados</span>
                    </div>
                    <div className="mt-auto">
                      {elegido ? (
                        estatusEleccion !== 'Asignada' && (
                          <Boton variante="secundario" className="w-full" disabled={ocupado === grupo.id}
                            onClick={() => accion(grupo.id, retirarEleccion, 'Retiraste tu elección.')}>
                            Retirar mi elección
                          </Boton>
                        )
                      ) : (
                        <Boton className="w-full" disabled={lleno || ocupado === grupo.id || Boolean(oferta.mensaje)}
                          onClick={() => accion(grupo.id, elegirGrupo, `Elegiste ${grupo.nivel} grupo ${grupo.clave}. Coordinación confirmará tu lugar.`)}>
                          <Check className="h-4 w-4" /> {lleno ? 'Grupo lleno' : eleccion ? 'Cambiar a este horario' : 'Elegir este horario'}
                        </Boton>
                      )}
                    </div>
                  </article>
                ))}
              </div>
            )}
          </>
        )}
      </Tarjeta>
    </div>
  );
}
