import { useEffect, useState } from 'react';
import { UserSquare } from 'lucide-react';
import { listarFacilitadores } from '../../api/facilitadores';
import { mensajeError } from '../../api/cliente';
import { Alerta, Cargando, Insignia, Tarjeta } from '../../components/ui';
import { GruposDeFacilitador, useFiltroFacilitadores } from '../../components/FacilitadoresConsulta';

/** Consulta de facilitadores para la jefa del DGTV: búsqueda y sus grupos y horarios (solo lectura). */
export default function FacilitadoresSupervision() {
  const [facilitadores, setFacilitadores] = useState([]);
  const [error, setError] = useState('');
  const [cargando, setCargando] = useState(true);
  const [verGrupos, setVerGrupos] = useState(null);
  const { filtrados, controles } = useFiltroFacilitadores(facilitadores);

  useEffect(() => {
    listarFacilitadores()
      .then(setFacilitadores)
      .catch((e) => setError(mensajeError(e)))
      .finally(() => setCargando(false));
  }, []);

  return (
    <Tarjeta titulo="Facilitadores" icono={UserSquare} extra={`${filtrados.length} de ${facilitadores.length}`}>
      {error && <Alerta tipo="error">{error}</Alerta>}
      {controles}
      {cargando ? <Cargando /> : filtrados.length === 0 ? (
        <p className="text-sm text-tenue">No hay facilitadores que coincidan.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[620px] text-left text-sm">
            <thead className="border-b border-linea text-xs text-tenue">
              <tr>
                <th className="px-2 py-2">Nombre</th><th className="px-2 py-2">Correo</th><th className="px-2 py-2">Teléfono</th>
                <th className="px-2 py-2">Cuenta</th><th className="px-2 py-2">Grupos</th>
              </tr>
            </thead>
            <tbody>
              {filtrados.map((f) => (
                <tr className="border-b border-linea last:border-0" key={f.id}>
                  <td className="px-2 py-3 font-medium">{f.nombreCompleto}</td>
                  <td className="px-2 py-3">{f.correo}</td>
                  <td className="px-2 py-3">{f.telefono || '—'}</td>
                  <td className="px-2 py-3"><Insignia estatus={f.activo ? 'Activa' : 'Inactiva'} /></td>
                  <td className="px-2 py-3">
                    <button type="button" className="font-semibold text-marino underline" onClick={() => setVerGrupos(f)}>Ver grupos y horarios</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {verGrupos && <GruposDeFacilitador facilitador={verGrupos} onCerrar={() => setVerGrupos(null)} />}
    </Tarjeta>
  );
}
