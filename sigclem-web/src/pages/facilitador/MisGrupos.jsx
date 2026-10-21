import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { Eye, Layers, Users } from 'lucide-react';
import { mensajeError } from '../../api/cliente';
import { listarMisGruposHistorial } from '../../api/facilitadores';
import { descargarActaPdf, obtenerActa } from '../../api/grupos';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';
import { periodoVigente, textoHorario } from '../../utils/facilitador';
import { useVisorDocumento } from '../../components/VisorDocumento';

const TONO_PAGO = { Liquidada: 'ok', Parcial: 'aviso', Pendiente: 'error', 'Sin cargo': 'neutro' };

function moneda(valor) {
  return new Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }).format(valor);
}

/** Grupos del facilitador en todos sus periodos, con la lista de alumnos y su estatus de pago (CU-09). */
export default function MisGrupos() {
  const verDocumento = useVisorDocumento();
  const [params, setParams] = useSearchParams();
  const [grupos, setGrupos] = useState(null);
  const [idPeriodo, setIdPeriodo] = useState('');
  const [acta, setActa] = useState(null);
  const [cargandoActa, setCargandoActa] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    listarMisGruposHistorial().then((lista) => {
      setGrupos(lista);
      const solicitado = lista.find((g) => String(g.id) === params.get('grupo'));
      const periodos = lista.map((g) => ({ id: g.idPeriodo, fechaInicio: g.fechaInicioPeriodo, fechaFin: g.fechaFinPeriodo }));
      setIdPeriodo(String(solicitado?.idPeriodo ?? periodoVigente(periodos)?.id ?? ''));
    }).catch((e) => { setError(mensajeError(e)); setGrupos([]); });
    // Solo al entrar: el grupo de la URL decide qué periodo se muestra primero.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const periodos = useMemo(() => {
    const unicos = new Map();
    (grupos || []).forEach((g) => unicos.set(g.idPeriodo, { id: g.idPeriodo, nombre: g.periodo, inicio: g.fechaInicioPeriodo }));
    return [...unicos.values()].sort((a, b) => (a.inicio < b.inicio ? 1 : -1));
  }, [grupos]);
  const delPeriodo = (grupos || []).filter((g) => String(g.idPeriodo) === idPeriodo);

  useEffect(() => {
    const id = params.get('grupo');
    if (!id) { setActa(null); return; }
    setCargandoActa(true);
    obtenerActa(id).then(setActa).catch((e) => setError(mensajeError(e))).finally(() => setCargandoActa(false));
  }, [params]);

  function pdf() {
    verDocumento({
      titulo: `Acta de calificaciones · ${acta.grupo.nivel} ${acta.grupo.clave} · ${acta.grupo.periodo}`,
      nombre: `acta-${acta.grupo.nivel.replace(/\s+/g, '-')}-${acta.grupo.clave}`,
      cargar: () => descargarActaPdf(acta.grupo.id),
    });
  }

  if (grupos === null) return <Cargando />;
  const cerrada = acta?.grupo.estatusActa === 'Cerrada';
  const sinPago = acta ? acta.alumnos.filter((a) => a.estatusPago !== 'Liquidada').length : 0;

  return (
    <div className="grid grid-cols-1 gap-[18px] xl:grid-cols-[0.8fr_1.2fr]">
      {error && <Alerta tipo="error" className="xl:col-span-2">{error}</Alerta>}
      <Tarjeta titulo="Mis grupos" icono={Layers}>
        {periodos.length === 0 ? <p className="text-sm text-tenue">Aún no tienes grupos asignados.</p> : (
          <>
            <Campo etiqueta="Periodo" id="periodo-grupos">
              <select className={claseEntrada} id="periodo-grupos" value={idPeriodo} onChange={(e) => { setIdPeriodo(e.target.value); setParams({}); }}>
                {periodos.map((p) => <option key={p.id} value={p.id}>{p.nombre}</option>)}
              </select>
            </Campo>
            <div className="mt-3 grid gap-2">
              {delPeriodo.map((g) => (
                <button key={g.id} type="button" onClick={() => setParams({ grupo: String(g.id) })}
                  className={`rounded-lg border p-3 text-left transition hover:border-marino/40 ${acta?.grupo.id === g.id ? 'border-marino bg-info-fondo' : 'border-linea'}`}>
                  <span className="flex items-center justify-between gap-2 font-semibold">
                    {g.nivel} · {g.clave}
                    <span className="text-xs font-normal">{g.alumnosInscritos}/{g.cupo} alumnos</span>
                  </span>
                  <span className="mt-1 block text-xs text-tenue">{g.modalidad} · {g.aula} · Acta {g.estatusActa.toLowerCase()}</span>
                  <span className="mt-1 block text-xs">{textoHorario(g.horarios)}</span>
                </button>
              ))}
            </div>
            <p className="mt-3 text-xs text-tenue">{grupos.length} grupos en {periodos.length} periodos.</p>
          </>
        )}
      </Tarjeta>

      <Tarjeta titulo={acta ? `Lista · ${acta.grupo.nivel} ${acta.grupo.clave}` : 'Lista de alumnos'} icono={Users}
        extra={acta && <Insignia estatus={acta.grupo.estatusActa} tono={cerrada ? 'ok' : 'aviso'} />}>
        {cargandoActa ? <Cargando /> : !acta ? (
          <p className="text-sm text-tenue">Selecciona un grupo para ver sus alumnos, su estatus de pago y sus calificaciones.</p>
        ) : (
          <>
            <p className="mb-3 text-xs text-tenue">{acta.grupo.periodo} · {textoHorario(acta.grupo.horarios)} · {acta.grupo.aula}</p>
            {!cerrada && sinPago > 0 && (
              <Alerta tipo="aviso" className="mb-3">
                {sinPago} {sinPago === 1 ? 'alumno no ha' : 'alumnos no han'} liquidado el curso. El acta no podrá cerrarse hasta que
                coordinación valide su pago o los dé de baja del grupo.
              </Alerta>
            )}
            {acta.alumnos.length === 0 ? <p className="text-sm text-tenue">El grupo aún no tiene alumnos inscritos.</p> : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[480px] text-left text-sm">
                  <thead className="border-b border-linea text-xs text-tenue">
                    <tr><th className="px-2 py-2">#</th><th className="px-2 py-2">No. de control</th><th className="px-2 py-2">Alumno</th>
                      <th className="px-2 py-2">Pago del curso</th><th className="px-2 py-2">Calificación</th></tr>
                  </thead>
                  <tbody>
                    {acta.alumnos.map((a, i) => (
                      <tr key={a.numeroControl} className="border-b border-linea last:border-0">
                        <td className="px-2 py-2 text-tenue">{i + 1}</td>
                        <td className="px-2 py-2">{a.numeroControl}</td>
                        <td className="px-2 py-2">{a.nombreCompleto}</td>
                        <td className="px-2 py-2">
                          <Insignia tono={TONO_PAGO[a.estatusPago] || 'neutro'}>{a.estatusPago}</Insignia>
                          {a.saldoPendiente > 0 && <span className="ml-1 text-xs text-tenue">debe {moneda(a.saldoPendiente)}</span>}
                        </td>
                        <td className="px-2 py-2">{a.calificacionFinal ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            <div className="mt-4 flex flex-wrap gap-2">
              <Boton variante="secundario" type="button" onClick={pdf}>
                <Eye className="h-4 w-4" /> {cerrada ? 'Ver acta (PDF)' : 'Ver lista / acta preliminar (PDF)'}
              </Boton>
              <Link to={`/facilitador/actas?grupo=${acta.grupo.id}`}
                className="inline-flex items-center rounded-lg px-3 py-2 text-sm font-semibold text-marino hover:underline">
                {cerrada ? 'Ver acta' : 'Ir a capturar calificaciones'}
              </Link>
            </div>
          </>
        )}
      </Tarjeta>
    </div>
  );
}
