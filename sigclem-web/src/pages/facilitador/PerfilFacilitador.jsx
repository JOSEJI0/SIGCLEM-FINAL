import { useEffect, useState } from 'react';
import { History, KeyRound, UserRound } from 'lucide-react';
import { mensajeError } from '../../api/cliente';
import { actualizarMiTelefono, listarMisGruposHistorial, obtenerMiPerfilFacilitador } from '../../api/facilitadores';
import { Alerta, Boton, Campo, Cargando, Tarjeta, claseEntrada } from '../../components/ui';

/** Datos del facilitador, su teléfono de contacto, su acceso y su historial de grupos. */
export default function PerfilFacilitador() {
  const [datos, setDatos] = useState(null);
  const [telefono, setTelefono] = useState('');
  const [historial, setHistorial] = useState([]);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');

  useEffect(() => {
    Promise.all([obtenerMiPerfilFacilitador(), listarMisGruposHistorial()])
      .then(([perfil, grupos]) => { setDatos(perfil); setTelefono(perfil.telefono || ''); setHistorial(grupos); })
      .catch((e) => setError(mensajeError(e)));
  }, []);

  async function guardarTelefono(evento) {
    evento.preventDefault();
    setGuardando(true);
    setError('');
    setAviso('');
    try {
      const actualizado = await actualizarMiTelefono(telefono.trim());
      setDatos(actualizado);
      setAviso('Teléfono actualizado.');
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando(false);
    }
  }

  if (!datos) return error ? <Alerta tipo="error">{error}</Alerta> : <Cargando />;

  const periodos = new Set(historial.map((g) => g.idPeriodo)).size;
  const alumnos = historial.reduce((s, g) => s + Number(g.alumnosInscritos), 0);
  const niveles = [...new Set(historial.map((g) => g.nivel))];

  return (
    <div className="grid grid-cols-1 gap-[18px] xl:grid-cols-2">
      <div className="xl:col-span-2">
        <h1 className="flex items-center gap-2 text-xl font-bold"><UserRound className="h-5 w-5" /> Mi perfil</h1>
      </div>
      <Tarjeta titulo="Mis datos" icono={UserRound}>
        {error && <Alerta tipo="error" className="mb-3">{error}</Alerta>}
        {aviso && <Alerta tipo="ok" className="mb-3">{aviso}</Alerta>}
        <dl className="grid gap-2 text-sm sm:grid-cols-[140px_1fr]">
          <dt className="text-tenue">Nombre</dt><dd className="font-semibold">{datos.nombreCompleto}</dd>
          <dt className="text-tenue">Correo (usuario)</dt><dd>{datos.correo}</dd>
          <dt className="text-tenue">Estado</dt><dd>{datos.activo ? 'Activo' : 'Inactivo'}</dd>
        </dl>
        <p className="mt-2 text-xs text-tenue">Para corregir tu nombre o correo, solicítalo a coordinación.</p>
        <form className="mt-4 flex flex-wrap items-end gap-2" onSubmit={guardarTelefono}>
          <Campo etiqueta="Teléfono de contacto" id="telefono" ayuda="7 a 15 dígitos; puede incluir +, paréntesis o guiones.">
            <input className={claseEntrada} id="telefono" maxLength="15" inputMode="tel" value={telefono}
              onChange={(e) => setTelefono(e.target.value)} />
          </Campo>
          <Boton type="submit" disabled={guardando || telefono.trim() === (datos.telefono || '')}>
            {guardando ? 'Guardando…' : 'Guardar teléfono'}
          </Boton>
        </form>
      </Tarjeta>

      <Tarjeta titulo="Acceso al sistema" icono={KeyRound}>
        <p className="text-sm">Tu usuario de acceso es <b>{datos.correo}</b>.</p>
        <p className="mt-2 text-sm text-tenue">
          La contraseña la asigna y la cambia únicamente coordinación. Si la olvidaste o necesitas una nueva, solicítala a la
          Coordinación de Lenguas Extranjeras y Maternas.
        </p>
      </Tarjeta>

      <Tarjeta titulo="Mi trayectoria" icono={History} className="xl:col-span-2">
        {historial.length === 0 ? <p className="text-sm text-tenue">Aún no tienes grupos registrados.</p> : (
          <>
            <p className="mb-3 text-sm">
              <b>{historial.length}</b> grupos en <b>{periodos}</b> periodos · <b>{alumnos}</b> alumnos atendidos ·
              niveles impartidos: {niveles.join(', ')}.
            </p>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[480px] text-left text-sm">
                <thead className="border-b border-linea text-xs text-tenue">
                  <tr><th className="px-2 py-2">Periodo</th><th className="px-2 py-2">Grupo</th><th className="px-2 py-2">Modalidad</th>
                    <th className="px-2 py-2">Alumnos</th><th className="px-2 py-2">Acta</th></tr>
                </thead>
                <tbody>
                  {historial.map((g) => (
                    <tr key={g.id} className="border-b border-linea last:border-0">
                      <td className="px-2 py-2">{g.periodo}</td>
                      <td className="px-2 py-2">{g.nivel} · {g.clave}</td>
                      <td className="px-2 py-2">{g.modalidad}</td>
                      <td className="px-2 py-2">{g.alumnosInscritos}/{g.cupo}</td>
                      <td className="px-2 py-2">{g.estatusActa}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </>
        )}
      </Tarjeta>
    </div>
  );
}
