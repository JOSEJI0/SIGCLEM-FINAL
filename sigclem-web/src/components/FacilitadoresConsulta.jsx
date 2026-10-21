import { useEffect, useMemo, useState } from 'react';
import { CalendarDays, Search } from 'lucide-react';
import { gruposDeFacilitador } from '../api/facilitadores';
import { mensajeError } from '../api/cliente';
import { Alerta, Cargando, Insignia, claseEntrada } from './ui';
import { Modal } from './Modal';
import CalendarioSemanal, { DIAS, colorNivel, horaCorta } from './CalendarioSemanal';

const hoy = () => new Date().toISOString().slice(0, 10);

/** Filtros de facilitadores (CU-16): nombre o correo y estado de la cuenta. */
export function useFiltroFacilitadores(facilitadores) {
  const [texto, setTexto] = useState('');
  const [estado, setEstado] = useState('');
  const filtrados = useMemo(() => facilitadores.filter((f) => {
    const t = texto.trim().toLowerCase();
    const coincide = !t || `${f.nombreCompleto} ${f.correo} ${f.telefono || ''}`.toLowerCase().includes(t);
    const enEstado = !estado || (estado === 'activos' ? f.activo : !f.activo);
    return coincide && enEstado;
  }), [facilitadores, texto, estado]);
  const controles = (
    <div className="mb-3 grid gap-2 sm:grid-cols-[1fr_180px]">
      <div className="relative">
        <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-tenue" />
        <input className={`${claseEntrada} pl-9`} placeholder="Buscar por nombre, correo o teléfono" value={texto}
          onChange={(e) => setTexto(e.target.value)} aria-label="Buscar facilitador" />
      </div>
      <select className={claseEntrada} value={estado} onChange={(e) => setEstado(e.target.value)} aria-label="Estado de la cuenta">
        <option value="">Todos</option>
        <option value="activos">Cuentas activas</option>
        <option value="inactivos">Cuentas inactivas</option>
      </select>
    </div>
  );
  return { filtrados, controles };
}

/** Grupos y horarios de un facilitador: calendario del periodo vigente e historial de todos sus grupos. */
export function GruposDeFacilitador({ facilitador, onCerrar }) {
  const [grupos, setGrupos] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    gruposDeFacilitador(facilitador.id).then(setGrupos).catch((e) => setError(mensajeError(e)));
  }, [facilitador.id]);

  const vigentes = (grupos || []).filter((g) => g.fechaFinPeriodo >= hoy());
  const sesiones = vigentes.flatMap((g) => g.horarios.map((h, i) => ({
    clave: `${g.id}-${i}`, dia: h.diaSemana, inicio: h.horaInicio, fin: h.horaFin,
    titulo: `${g.nivel} ${g.clave}`, detalle: g.aula, color: colorNivel(g.nivelNumero),
  })));

  return (
    <Modal titulo={`Grupos y horarios · ${facilitador.nombreCompleto}`} onCerrar={onCerrar} ancho="max-w-5xl">
      {error && <Alerta tipo="error">{error}</Alerta>}
      {!grupos ? <Cargando /> : grupos.length === 0 ? <p className="text-sm text-tenue">No tiene grupos asignados.</p> : (
        <div className="grid gap-4">
          <section>
            <h4 className="mb-2 flex items-center gap-2 text-sm font-semibold"><CalendarDays className="h-4 w-4" /> Horario de periodos vigentes ({vigentes.length} grupos)</h4>
            {vigentes.length === 0 ? <p className="text-[13px] text-tenue">No tiene grupos en periodos vigentes.</p> : <CalendarioSemanal sesiones={sesiones} />}
          </section>
          <section>
            <h4 className="mb-2 text-sm font-semibold">Todos sus grupos</h4>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[640px] text-left text-[13px]">
                <thead className="border-b border-linea text-xs text-tenue">
                  <tr><th className="px-2 py-2">Periodo</th><th className="px-2 py-2">Grupo</th><th className="px-2 py-2">Horario</th><th className="px-2 py-2">Aula</th><th className="px-2 py-2">Alumnos</th><th className="px-2 py-2">Acta</th></tr>
                </thead>
                <tbody>
                  {grupos.map((g) => (
                    <tr key={g.id} className="border-b border-linea last:border-0">
                      <td className="px-2 py-2.5">{g.periodo}</td>
                      <td className="px-2 py-2.5 font-semibold">{g.nivel} · {g.clave}</td>
                      <td className="px-2 py-2.5">{g.horarios.map((h) => `${DIAS[h.diaSemana - 1].slice(0, 3)} ${horaCorta(h.horaInicio)}–${horaCorta(h.horaFin)}`).join(', ')}</td>
                      <td className="px-2 py-2.5">{g.aula}</td>
                      <td className="px-2 py-2.5">{g.alumnosInscritos}/{g.cupo}</td>
                      <td className="px-2 py-2.5"><Insignia estatus={g.estatusActa === 'Cerrada' ? 'Concluido' : 'Programado'}>{g.estatusActa}</Insignia></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </div>
      )}
    </Modal>
  );
}
