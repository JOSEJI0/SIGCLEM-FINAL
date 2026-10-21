import { useCallback, useEffect, useState } from 'react';
import { Eye, EyeOff, KeyRound, Plus, UserRoundCog } from 'lucide-react';
import {
  actualizarFacilitador, cambiarContrasenaFacilitador, cambiarEstadoFacilitador, listarFacilitadores, registrarFacilitador,
} from '../../api/facilitadores';
import { Modal } from '../../components/Modal';
import { GruposDeFacilitador, useFiltroFacilitadores } from '../../components/FacilitadoresConsulta';
import { mensajeError } from '../../api/cliente';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';

const inicial = { nombre: '', apellidoPaterno: '', apellidoMaterno: '', correo: '', telefono: '', contrasena: '' };

/** Campo de contraseña con botón para mostrarla u ocultarla. */
function CampoContrasena({ id, valor, onCambiar, requerido = false }) {
  const [ver, setVer] = useState(false);
  return (
    <div className="relative">
      <input className={`${claseEntrada} pr-9`} id={id} name="contrasena" type={ver ? 'text' : 'password'} minLength="8" maxLength="72"
        autoComplete="new-password" value={valor} onChange={onCambiar} required={requerido} />
      <button type="button" onClick={() => setVer(!ver)} aria-label={ver ? 'Ocultar contraseña' : 'Mostrar contraseña'}
        className="absolute right-3 top-1/2 -translate-y-1/2 text-tenue">
        {ver ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
      </button>
    </div>
  );
}

export default function Facilitadores() {
  const [facilitadores, setFacilitadores] = useState([]);
  const [formulario, setFormulario] = useState(inicial);
  const [seleccionado, setSeleccionado] = useState(null);
  const [credenciales, setCredenciales] = useState(null);
  const [error, setError] = useState('');
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [cambioClave, setCambioClave] = useState(null);
  const [aviso, setAviso] = useState('');
  const [verGrupos, setVerGrupos] = useState(null);
  const { filtrados, controles } = useFiltroFacilitadores(facilitadores);

  const cargar = useCallback(async () => {
    try {
      setError('');
      setFacilitadores(await listarFacilitadores());
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => { cargar(); }, [cargar]);

  function cambiarCampo(evento) {
    setFormulario((actual) => ({ ...actual, [evento.target.name]: evento.target.value }));
  }

  function editar(facilitador) {
    setSeleccionado(facilitador);
    setFormulario({
      ...inicial,
      nombre: facilitador.nombre,
      apellidoPaterno: facilitador.apellidoPaterno,
      apellidoMaterno: facilitador.apellidoMaterno ?? '',
      correo: facilitador.correo,
      telefono: facilitador.telefono ?? '',
    });
    setError('');
  }

  async function guardar(evento) {
    evento.preventDefault();
    setGuardando(true);
    setError('');
    try {
      const datos = { ...formulario, contrasena: formulario.contrasena || null };
      if (seleccionado) {
        await actualizarFacilitador(seleccionado.id, datos);
        setAviso(`Datos de ${formulario.nombre} actualizados${formulario.contrasena ? ', incluida su contraseña' : ''}.`);
        setSeleccionado(null);
      } else {
        const creado = await registrarFacilitador(datos);
        setCredenciales({ ...creado, contrasenaAsignada: formulario.contrasena || null });
      }
      setFormulario(inicial);
      await cargar();
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando(false);
    }
  }

  async function cambiarEstado(facilitador) {
    try {
      setError('');
      await cambiarEstadoFacilitador(facilitador.id, !facilitador.activo);
      await cargar();
    } catch (e) {
      setError(mensajeError(e));
    }
  }

  return (
    <div className="grid grid-cols-1 gap-[18px] xl:grid-cols-[minmax(300px,0.8fr)_1.2fr]">
      <Tarjeta titulo={seleccionado ? 'Editar facilitador' : 'Registrar facilitador'} icono={UserRoundCog}>
        <form className="grid gap-3" onSubmit={guardar}>
          <Campo etiqueta="Nombre" id="nombre">
            <input className={claseEntrada} id="nombre" name="nombre" maxLength="60" value={formulario.nombre} onChange={cambiarCampo} required />
          </Campo>
          <Campo etiqueta="Apellido paterno" id="apellidoPaterno">
            <input className={claseEntrada} id="apellidoPaterno" name="apellidoPaterno" maxLength="40" value={formulario.apellidoPaterno} onChange={cambiarCampo} required />
          </Campo>
          <Campo etiqueta="Apellido materno" id="apellidoMaterno">
            <input className={claseEntrada} id="apellidoMaterno" name="apellidoMaterno" maxLength="40" value={formulario.apellidoMaterno} onChange={cambiarCampo} />
          </Campo>
          <Campo etiqueta="Correo (será su usuario)" id="correo">
            <input className={claseEntrada} id="correo" name="correo" type="email" maxLength="80" value={formulario.correo} onChange={cambiarCampo} required />
          </Campo>
          <Campo etiqueta="Teléfono" id="telefono">
            <input className={claseEntrada} id="telefono" name="telefono" maxLength="15" value={formulario.telefono} onChange={cambiarCampo} />
          </Campo>
          <Campo etiqueta={seleccionado ? 'Nueva contraseña (opcional)' : 'Contraseña'} id="contrasena"
            ayuda={seleccionado ? 'Déjala vacía para conservar la actual.' : 'Mínimo 8 caracteres. Si la dejas vacía, el sistema genera una temporal.'}>
            <CampoContrasena id="contrasena" valor={formulario.contrasena} onCambiar={cambiarCampo} />
          </Campo>
          {error && <Alerta tipo="error">{error}</Alerta>}
          <div className="flex gap-2">
            <Boton type="submit" disabled={guardando}>{guardando ? 'Guardando…' : seleccionado ? 'Guardar cambios' : <><Plus className="h-4 w-4" /> Crear cuenta</>}</Boton>
            {seleccionado && <Boton variante="secundario" type="button" onClick={() => { setSeleccionado(null); setFormulario(inicial); setError(''); }}>Cancelar</Boton>}
          </div>
        </form>
        {credenciales && (
          <div className="mt-4 rounded-lg border border-[#C7DAF0] bg-info-fondo p-3 text-sm">
            <div className="mb-2 flex items-center gap-2 font-semibold"><KeyRound className="h-4 w-4" /> Cuenta creada; entrega la contraseña al facilitador.</div>
            <p>Usuario: <strong>{credenciales.usuario}</strong></p>
            {credenciales.contrasenaTemporal
              ? <p>Contraseña temporal: <strong className="break-all">{credenciales.contrasenaTemporal}</strong></p>
              : <p>Contraseña: <strong className="break-all">{credenciales.contrasenaAsignada}</strong> (la que asignaste)</p>}
            <button className="mt-2 text-xs underline" type="button" onClick={() => setCredenciales(null)}>Ocultar credenciales</button>
          </div>
        )}
      </Tarjeta>

      <Tarjeta titulo="Facilitadores registrados" extra={`${filtrados.length} de ${facilitadores.length}`}>
        {aviso && <Alerta tipo="ok" className="mb-3">{aviso}</Alerta>}
        {controles}
        {cargando ? <Cargando /> : facilitadores.length === 0 ? (
          <p className="text-sm text-tenue">Aún no hay facilitadores registrados.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[620px] text-left text-sm">
              <thead className="border-b border-linea text-xs text-tenue">
                <tr><th className="px-2 py-2">Nombre</th><th className="px-2 py-2">Correo</th><th className="px-2 py-2">Teléfono</th><th className="px-2 py-2">Cuenta</th><th className="px-2 py-2">Acciones</th></tr>
              </thead>
              <tbody>
                {filtrados.map((f) => (
                  <tr className="border-b border-linea last:border-0" key={f.id}>
                    <td className="px-2 py-3 font-medium">{f.nombreCompleto}</td>
                    <td className="px-2 py-3">{f.correo}</td>
                    <td className="px-2 py-3">{f.telefono || '—'}</td>
                    <td className="px-2 py-3"><Insignia estatus={f.activo ? 'Activa' : 'Inactiva'} /></td>
                    <td className="px-2 py-3">
                      <div className="flex gap-2">
                        <button className="text-marino underline" type="button" onClick={() => setVerGrupos(f)}>Grupos</button>
                        <button className="text-marino underline" type="button" onClick={() => editar(f)}>Editar</button>
                        <button className="text-marino underline" type="button"
                          onClick={() => { setCambioClave({ facilitador: f, contrasena: '', error: '' }); setAviso(''); }}>Contraseña</button>
                        <button className="text-marino underline" type="button" onClick={() => cambiarEstado(f)}>{f.activo ? 'Desactivar' : 'Activar'}</button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Tarjeta>

      {verGrupos && <GruposDeFacilitador facilitador={verGrupos} onCerrar={() => setVerGrupos(null)} />}

      {cambioClave && (
        <Modal titulo={`Asignar contraseña · ${cambioClave.facilitador.nombreCompleto}`} onCerrar={() => setCambioClave(null)} ancho="max-w-md">
          <form className="grid gap-4" onSubmit={async (e) => {
            e.preventDefault();
            try {
              await cambiarContrasenaFacilitador(cambioClave.facilitador.id, cambioClave.contrasena);
              setAviso(`Contraseña de ${cambioClave.facilitador.nombreCompleto} actualizada. Usuario: ${cambioClave.facilitador.correo}.`);
              setCambioClave(null);
            } catch (err) {
              setCambioClave({ ...cambioClave, error: mensajeError(err) });
            }
          }}>
            {cambioClave.error && <Alerta tipo="error">{cambioClave.error}</Alerta>}
            <p className="text-[13px] text-tenue">Usuario: <b>{cambioClave.facilitador.correo}</b></p>
            <Campo etiqueta="Nueva contraseña" id="nuevaContrasena" ayuda="Entre 8 y 72 caracteres.">
              <CampoContrasena id="nuevaContrasena" requerido valor={cambioClave.contrasena}
                onCambiar={(e) => setCambioClave({ ...cambioClave, contrasena: e.target.value })} />
            </Campo>
            <div className="flex justify-end gap-2">
              <Boton type="button" variante="secundario" onClick={() => setCambioClave(null)}>Cancelar</Boton>
              <Boton type="submit"><KeyRound className="h-4 w-4" /> Guardar contraseña</Boton>
            </div>
          </form>
        </Modal>
      )}
    </div>
  );
}
