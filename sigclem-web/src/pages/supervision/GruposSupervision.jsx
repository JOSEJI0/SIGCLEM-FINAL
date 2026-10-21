import { useEffect, useState } from 'react';
import { CalendarDays } from 'lucide-react';
import { obtenerPeriodos } from '../../api/catalogos';
import { listarGrupos } from '../../api/grupos';
import { mensajeError } from '../../api/cliente';
import { Alerta, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';

const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];

function fechaLocal() {
  const hoy = new Date();
  return `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
}

export default function GruposSupervision() {
  const [periodos, setPeriodos] = useState([]);
  const [periodoId, setPeriodoId] = useState('');
  const [grupos, setGrupos] = useState([]);
  const [error, setError] = useState('');
  const [cargando, setCargando] = useState(true);

  useEffect(() => {
    obtenerPeriodos().then((datos) => {
      setPeriodos(datos);
      const vigente = datos.find((periodo) => periodo.fechaInicio <= fechaLocal() && periodo.fechaFin >= fechaLocal());
      setPeriodoId(String(vigente?.id ?? datos[0]?.id ?? ''));
      if (datos.length === 0) setCargando(false);
    }).catch((e) => {
      setError(mensajeError(e));
      setCargando(false);
    });
  }, []);

  useEffect(() => {
    if (!periodoId) return;
    setCargando(true);
    setError('');
    listarGrupos(periodoId)
      .then(setGrupos)
      .catch((e) => setError(mensajeError(e)))
      .finally(() => setCargando(false));
  }, [periodoId]);

  return (
    <Tarjeta titulo="Grupos académicos" icono={CalendarDays} extra={`${grupos.length} en total`}>
      {error && <Alerta tipo="error">{error}</Alerta>}
      <div className="mb-4 max-w-sm">
        <Campo etiqueta="Periodo" id="periodo">
          <select className={claseEntrada} id="periodo" value={periodoId} onChange={(evento) => setPeriodoId(evento.target.value)}>
            {periodos.map((periodo) => <option key={periodo.id} value={periodo.id}>{periodo.nombre}</option>)}
          </select>
        </Campo>
      </div>
      {cargando ? <Cargando /> : grupos.length === 0 ? (
        <p className="text-sm text-tenue">No hay grupos registrados.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] text-left text-sm">
            <thead className="border-b border-linea text-xs text-tenue">
              <tr>
                <th className="px-2 py-2">Grupo</th>
                <th className="px-2 py-2">Periodo</th>
                <th className="px-2 py-2">Facilitador</th>
                <th className="px-2 py-2">Modalidad / aula</th>
                <th className="px-2 py-2">Inscripción</th>
                <th className="px-2 py-2">Horario</th>
                <th className="px-2 py-2">Acta</th>
              </tr>
            </thead>
            <tbody>
              {grupos.map((grupo) => (
                <tr className="border-b border-linea align-top last:border-0" key={grupo.id}>
                  <td className="px-2 py-3 font-medium">Nivel {grupo.nivelNumero} · {grupo.clave}</td>
                  <td className="px-2 py-3">{grupo.periodo}</td>
                  <td className="px-2 py-3">{grupo.facilitador}</td>
                  <td className="px-2 py-3">{grupo.modalidad}<br /><span className="text-xs text-tenue">{grupo.aula}</span></td>
                  <td className="px-2 py-3">{grupo.alumnosInscritos} / {grupo.cupo}</td>
                  <td className="px-2 py-3">
                    {grupo.horarios.map((horario) => (
                      <div key={`${horario.diaSemana}-${horario.horaInicio}`}>
                        {DIAS[horario.diaSemana - 1]} {horario.horaInicio}–{horario.horaFin}
                      </div>
                    ))}
                  </td>
                  <td className="px-2 py-3"><Insignia estatus={grupo.estatusActa} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Tarjeta>
  );
}
