import { useState } from 'react';
import { KeyRound, ShieldCheck, UserCog, UserRound } from 'lucide-react';
import { mensajeError } from '../../api/cliente';
import { asignarContrasenaAlumno, asignarContrasenaJefa, cambiarMiContrasena } from '../../api/cuentas';
import { Alerta, Boton, Campo, Tarjeta, claseEntrada } from '../../components/ui';

const valida = (c) => c.length >= 8 && /[A-Za-zÁÉÍÓÚÑáéíóúñ]/.test(c) && /\d/.test(c);
const AYUDA = 'Mínimo 8 caracteres, con al menos una letra y un número.';

/** Contraseñas que administra coordinación: la propia, la de los alumnos y la de la jefa del DGTV. */
export default function Cuentas() {
  return (
    <div className="grid grid-cols-1 gap-[18px]">
      <div>
        <h1 className="flex items-center gap-2 text-xl font-bold"><ShieldCheck className="h-5 w-5" /> Cuentas y contraseñas</h1>
        <p className="mt-1 text-sm text-tenue">
          Las contraseñas las administra coordinación. Las de los facilitadores se asignan en la pantalla Facilitadores.
        </p>
      </div>
      <div className="grid grid-cols-1 gap-[18px] xl:grid-cols-3">
        <MiContrasena />
        <Asignar titulo="Contraseña de un alumno" icono={UserRound} conNumeroControl
          ayudaExtra="Úsalo cuando el alumno olvida su contraseña; entrégasela en persona."
          enviar={(datos) => asignarContrasenaAlumno(datos.numeroControl.trim(), datos.contrasena)}
          aviso={(datos) => `Se asignó la nueva contraseña al alumno ${datos.numeroControl.trim().toUpperCase()}.`} />
        <Asignar titulo="Contraseña de la jefa del DGTV" icono={UserCog}
          enviar={(datos) => asignarContrasenaJefa(datos.contrasena)}
          aviso={() => 'Se asignó la nueva contraseña a la jefa del DGTV.'} />
      </div>
    </div>
  );
}

function MiContrasena() {
  const [datos, setDatos] = useState({ actual: '', nueva: '', confirmacion: '' });
  const [estado, setEstado] = useState({ guardando: false, error: '', aviso: '' });
  const listo = datos.actual && valida(datos.nueva) && datos.nueva === datos.confirmacion;

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
      <form className="grid gap-3" onSubmit={guardar}>
        <Campo etiqueta="Contraseña actual" id="mi-actual">
          <input className={claseEntrada} id="mi-actual" type="password" autoComplete="current-password" value={datos.actual}
            onChange={(e) => setDatos({ ...datos, actual: e.target.value })} />
        </Campo>
        <Campo etiqueta="Nueva contraseña" id="mi-nueva" ayuda={AYUDA}
          error={datos.nueva && !valida(datos.nueva) ? 'Aún no cumple los requisitos.' : ''}>
          <input className={claseEntrada} id="mi-nueva" type="password" autoComplete="new-password" maxLength="72" value={datos.nueva}
            onChange={(e) => setDatos({ ...datos, nueva: e.target.value })} />
        </Campo>
        <Campo etiqueta="Confirma la nueva contraseña" id="mi-confirmacion"
          error={datos.confirmacion && datos.confirmacion !== datos.nueva ? 'Las contraseñas no coinciden.' : ''}>
          <input className={claseEntrada} id="mi-confirmacion" type="password" autoComplete="new-password" maxLength="72"
            value={datos.confirmacion} onChange={(e) => setDatos({ ...datos, confirmacion: e.target.value })} />
        </Campo>
        <div><Boton type="submit" disabled={!listo || estado.guardando}>{estado.guardando ? 'Guardando…' : 'Cambiar mi contraseña'}</Boton></div>
      </form>
    </Tarjeta>
  );
}

function Asignar({ titulo, icono, conNumeroControl = false, ayudaExtra, enviar, aviso }) {
  const vacio = { numeroControl: '', contrasena: '' };
  const [datos, setDatos] = useState(vacio);
  const [estado, setEstado] = useState({ guardando: false, error: '', aviso: '' });
  const listo = valida(datos.contrasena) && (!conNumeroControl || datos.numeroControl.trim());
  const id = conNumeroControl ? 'alumno' : 'jefa';

  async function guardar(e) {
    e.preventDefault();
    if (!window.confirm(`¿Asignar la nueva contraseña${conNumeroControl ? ` al alumno ${datos.numeroControl.trim().toUpperCase()}` : ''}? `
      + 'La contraseña anterior dejará de funcionar.')) return;
    setEstado({ guardando: true, error: '', aviso: '' });
    try {
      await enviar(datos);
      setEstado({ guardando: false, error: '', aviso: aviso(datos) });
      setDatos(vacio);
    } catch (err) {
      setEstado({ guardando: false, error: mensajeError(err), aviso: '' });
    }
  }

  return (
    <Tarjeta titulo={titulo} icono={icono}>
      {ayudaExtra && <p className="mb-3 text-xs text-tenue">{ayudaExtra}</p>}
      {estado.error && <Alerta tipo="error" className="mb-3">{estado.error}</Alerta>}
      {estado.aviso && <Alerta tipo="ok" className="mb-3">{estado.aviso}</Alerta>}
      <form className="grid gap-3" onSubmit={guardar}>
        {conNumeroControl && (
          <Campo etiqueta="Número de control" id={`${id}-nc`}>
            <input className={claseEntrada} id={`${id}-nc`} maxLength="10" value={datos.numeroControl}
              onChange={(e) => setDatos({ ...datos, numeroControl: e.target.value.toUpperCase() })} />
          </Campo>
        )}
        <Campo etiqueta="Nueva contraseña" id={`${id}-contrasena`} ayuda={AYUDA}
          error={datos.contrasena && !valida(datos.contrasena) ? 'Aún no cumple los requisitos.' : ''}>
          <input className={claseEntrada} id={`${id}-contrasena`} type="text" autoComplete="off" maxLength="72" value={datos.contrasena}
            onChange={(e) => setDatos({ ...datos, contrasena: e.target.value })} />
        </Campo>
        <div><Boton type="submit" disabled={!listo || estado.guardando}>{estado.guardando ? 'Guardando…' : 'Asignar contraseña'}</Boton></div>
      </form>
    </Tarjeta>
  );
}
