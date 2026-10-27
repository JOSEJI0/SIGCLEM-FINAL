import { useState } from 'react';
import { KeyRound } from 'lucide-react';
import { mensajeError } from '../api/cliente';
import { cambiarMiContrasena } from '../api/cuentas';
import { Alerta, Boton, Campo, Tarjeta, claseEntrada } from './ui';

const validaContrasena = (contrasena) => contrasena.length >= 8
  && new TextEncoder().encode(contrasena).length <= 72
  && /[A-Za-zÁÉÍÓÚÑáéíóúñ]/.test(contrasena)
  && /\d/.test(contrasena);
const AYUDA = 'Mínimo 8 caracteres, con al menos una letra y un número. Máximo 72 bytes UTF-8.';

export default function CambiarMiContrasena() {
  const [datos, setDatos] = useState({ actual: '', nueva: '', confirmacion: '' });
  const [estado, setEstado] = useState({ guardando: false, error: '', aviso: '' });
  const listo = datos.actual && validaContrasena(datos.nueva) && datos.nueva === datos.confirmacion;

  async function guardar(e) {
    e.preventDefault();
    setEstado({ guardando: true, error: '', aviso: '' });
    try {
      await cambiarMiContrasena(datos.actual, datos.nueva);
      setDatos({ actual: '', nueva: '', confirmacion: '' });
      setEstado({ guardando: false, error: '', aviso: 'Tu contraseña se actualizó. Úsala la próxima vez que inicies sesión.' });
    } catch (err) {
      setEstado({ guardando: false, error: mensajeError(err), aviso: '' });
    }
  }

  return (
    <Tarjeta titulo="Mi contraseña" icono={KeyRound}>
      {estado.error && <Alerta tipo="error" className="mb-3">{estado.error}</Alerta>}
      {estado.aviso && <Alerta tipo="ok" className="mb-3">{estado.aviso}</Alerta>}
      <form className="grid max-w-xl gap-3" onSubmit={guardar}>
        <Campo etiqueta="Contraseña actual" id="mi-actual">
          <input className={claseEntrada} id="mi-actual" type="password" autoComplete="current-password" required value={datos.actual}
            onChange={(e) => setDatos({ ...datos, actual: e.target.value })} />
        </Campo>
        <Campo etiqueta="Nueva contraseña" id="mi-nueva" ayuda={AYUDA}
          error={datos.nueva && !validaContrasena(datos.nueva) ? 'Aún no cumple los requisitos.' : ''}>
          <input className={claseEntrada} id="mi-nueva" type="password" autoComplete="new-password" maxLength="72" required value={datos.nueva}
            onChange={(e) => setDatos({ ...datos, nueva: e.target.value })} />
        </Campo>
        <Campo etiqueta="Confirma la nueva contraseña" id="mi-confirmacion"
          error={datos.confirmacion && datos.confirmacion !== datos.nueva ? 'Las contraseñas no coinciden.' : ''}>
          <input className={claseEntrada} id="mi-confirmacion" type="password" autoComplete="new-password" maxLength="72" required
            value={datos.confirmacion} onChange={(e) => setDatos({ ...datos, confirmacion: e.target.value })} />
        </Campo>
        <div><Boton type="submit" disabled={!listo || estado.guardando}>{estado.guardando ? 'Guardando…' : 'Cambiar mi contraseña'}</Boton></div>
      </form>
    </Tarjeta>
  );
}
