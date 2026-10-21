import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  CalendarDays, CheckCircle2, ClipboardCheck, ClipboardList, Layers, TriangleAlert, Users, Wallet,
} from 'lucide-react';
import { mensajeError } from '../../api/cliente';
import { obtenerPeriodos } from '../../api/catalogos';
import { listarGrupos, obtenerActa } from '../../api/grupos';
import { useAuth } from '../../auth/AuthContext';
import CalendarioSemanal, { colorNivel } from '../../components/CalendarioSemanal';
import { Indicador } from '../../components/Graficas';
import { Alerta, Cargando, Insignia, Tarjeta } from '../../components/ui';
import { diasHasta, periodoVigente, textoHorario } from '../../utils/facilitador';

/** Inicio del facilitador: resumen del periodo, horario semanal y pendientes de cada grupo (CU-09, CU-10). */
export default function PanelFacilitador() {
  const { perfil } = useAuth();
  const [periodo, setPeriodo] = useState(null);
  const [grupos, setGrupos] = useState(null);
  const [actas, setActas] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    (async () => {
      try {
        const vigente = periodoVigente(await obtenerPeriodos());
        setPeriodo(vigente || null);
        const lista = vigente ? await listarGrupos(vigente.id) : [];
        setGrupos(lista);
        // Las actas traen la lista de alumnos con su calificación y su estatus de pago.
        const detalles = await Promise.all(lista.map((g) => obtenerActa(g.id).catch(() => null)));
        setActas(Object.fromEntries(detalles.filter(Boolean).map((a) => [a.grupo.id, a])));
      } catch (e) {
        setError(mensajeError(e));
        setGrupos([]);
        setActas({});
      }
    })();
  }, []);

  const resumen = useMemo(() => {
    const lista = grupos || [];
    const alumnos = Object.values(actas || {}).flatMap((a) => a.alumnos);
    return {
      grupos: lista.length,
      alumnos: actas ? alumnos.length : '…',
      abiertas: lista.filter((g) => g.estatusActa !== 'Cerrada').length,
      sinPago: actas ? alumnos.filter((a) => a.estatusPago !== 'Liquidada').length : '…',
    };
  }, [grupos, actas]);

  const sesiones = useMemo(() => (grupos || []).flatMap((g) => g.horarios.map((h, i) => ({
    clave: `${g.id}-${i}`, dia: h.diaSemana, inicio: h.horaInicio, fin: h.horaFin,
    titulo: `${g.nivel} · ${g.clave}`, detalle: `${g.aula} · ${g.alumnosInscritos} alumnos`, color: colorNivel(g.nivelNumero),
  }))), [grupos]);

  if (grupos === null) return <Cargando />;
  const restantes = periodo ? diasHasta(periodo.fechaFin) : null;

  return (
    <div className="grid gap-[18px]">
      <div>
        <h1 className="text-xl font-bold">Hola, {perfil?.nombre}</h1>
        <p className="mt-1 text-sm text-tenue">
          {periodo
            ? `Periodo ${periodo.nombre} · ${restantes >= 0 ? `faltan ${restantes} días para su cierre` : 'periodo concluido'}`
            : 'No hay un periodo escolar vigente.'}
        </p>
      </div>
      {error && <Alerta tipo="error">{error}</Alerta>}

      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <Indicador etiqueta="Grupos del periodo" valor={resumen.grupos} icono={Layers} tono="info" />
        <Indicador etiqueta="Alumnos a tu cargo" valor={resumen.alumnos} icono={Users} />
        <Indicador etiqueta="Actas por cerrar" valor={resumen.abiertas} icono={ClipboardList} tono={resumen.abiertas ? 'aviso' : 'ok'} />
        <Indicador etiqueta="Alumnos sin pago liquidado" valor={resumen.sinPago} icono={Wallet}
          tono={resumen.sinPago > 0 ? 'error' : 'ok'} detalle={resumen.sinPago > 0 ? 'Impiden cerrar el acta' : undefined} />
      </div>

      <div className="grid grid-cols-1 gap-[18px] xl:grid-cols-[1.4fr_1fr]">
        <Tarjeta titulo="Mi horario semanal" icono={CalendarDays}>
          {sesiones.length === 0
            ? <p className="text-sm text-tenue">No tienes sesiones asignadas en este periodo.</p>
            : <CalendarioSemanal sesiones={sesiones} horaMinima={7} horaMaxima={18} />}
        </Tarjeta>

        <Tarjeta titulo="Pendientes por grupo" icono={ClipboardCheck}>
          {grupos.length === 0 ? <p className="text-sm text-tenue">Coordinación aún no te asigna grupos en este periodo.</p> : (
            <ul className="grid gap-2">
              {grupos.map((g) => {
                const acta = actas?.[g.id];
                const alumnos = acta?.alumnos || [];
                const sinPago = alumnos.filter((a) => a.estatusPago !== 'Liquidada').length;
                const sinCalificacion = alumnos.filter((a) => a.calificacionFinal == null).length;
                const cerrada = g.estatusActa === 'Cerrada';
                return (
                  <li key={g.id} className="rounded-lg border border-linea p-3">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-semibold">{g.nivel} · {g.clave}</span>
                      <Insignia tono={cerrada ? 'ok' : g.estatusActa === 'En captura' ? 'info' : 'aviso'}>{g.estatusActa}</Insignia>
                    </div>
                    <p className="mt-1 text-xs text-tenue">{textoHorario(g.horarios)} · {g.aula}</p>
                    <div className="mt-2 grid gap-1 text-xs">
                      {cerrada ? (
                        <span className="flex items-center gap-1.5 text-ok"><CheckCircle2 className="h-3.5 w-3.5" /> Acta cerrada; avance registrado.</span>
                      ) : !actas ? (
                        <span className="text-tenue">Consultando la lista…</span>
                      ) : alumnos.length === 0 ? (
                        <span className="text-tenue">Sin alumnos inscritos todavía.</span>
                      ) : (
                        <>
                          {sinCalificacion > 0 && <span>{sinCalificacion} de {alumnos.length} alumnos sin calificación capturada.</span>}
                          {sinPago > 0 && (
                            <span className="flex items-center gap-1.5 text-error">
                              <TriangleAlert className="h-3.5 w-3.5" /> {sinPago} sin pago liquidado: el acta no podrá cerrarse hasta que paguen.
                            </span>
                          )}
                          {sinCalificacion === 0 && sinPago === 0 && <span className="text-ok">Listo para cerrar el acta.</span>}
                        </>
                      )}
                    </div>
                    <div className="mt-2 flex gap-3 text-xs font-semibold">
                      <Link className="text-marino hover:underline" to={`/facilitador/grupos?grupo=${g.id}`}>Ver lista</Link>
                      <Link className="text-marino hover:underline" to={`/facilitador/actas?grupo=${g.id}`}>{cerrada ? 'Consultar acta' : 'Capturar calificaciones'}</Link>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </Tarjeta>
      </div>
    </div>
  );
}
