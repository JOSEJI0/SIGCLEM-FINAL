import { useState } from 'react';
import { ShieldCheck, UserCog, UserRound } from 'lucide-react';
import { mensajeError } from '../../api/cliente';
import { asignarContrasenaAlumno, asignarContrasenaJefa } from '../../api/cuentas';
import CambiarMiContrasena from '../../components/CambiarMiContrasena';
import { Alerta, Boton, Campo, Tarjeta, claseEntrada } from '../../components/ui';

const validaFuerte = (c) => c.length >= 8 && /[A-Za-zÁÉÍÓÚÑáéíóúñ]/.test(c) && /\d/.test(c);
const validaLibre = (c) => c.trim().length > 0 && new TextEncoder().encode(c).length <= 72;
const AYUDA_FUERTE = 'Mínimo 8 caracteres, con al menos una letra y un número.';
const AYUDA_ALUMNO = 'Puedes usar cualquier contraseña, incluido solo el número de control. Máximo 72 bytes UTF-8.';

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
        <CambiarMiContrasena />
        <Asignar titulo="Contraseña de un alumno" icono={UserRound} conNumeroControl
          contrasenaLibre
          ayudaExtra="Úsalo cuando el alumno olvida su contraseña; entrégasela en persona. También puedes asignar su número de control como contraseña inicial."
          enviar={(datos) => asignarContrasenaAlumno(datos.numeroControl.trim(), datos.contrasena)}
          aviso={(datos) => `Se asignó la nueva contraseña al alumno ${datos.numeroControl.trim().toUpperCase()}.`} />
        <Asignar titulo="Contraseña de la jefa del DGTV" icono={UserCog}
          enviar={(datos) => asignarContrasenaJefa(datos.contrasena)}
          aviso={() => 'Se asignó la nueva contraseña a la jefa del DGTV.'} />
      </div>
    </div>
  );
}

function Asignar({ titulo, icono, conNumeroControl = false, contrasenaLibre = false, ayudaExtra, enviar, aviso }) {
  const vacio = { numeroControl: '', contrasena: '' };
  const [datos, setDatos] = useState(vacio);
  const [estado, setEstado] = useState({ guardando: false, error: '', aviso: '' });
  const validaContrasena = contrasenaLibre ? validaLibre : validaFuerte;
  const ayudaContrasena = contrasenaLibre ? AYUDA_ALUMNO : AYUDA_FUERTE;
  const listo = validaContrasena(datos.contrasena) && (!conNumeroControl || datos.numeroControl.trim());
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
        <Campo etiqueta="Nueva contraseña" id={`${id}-contrasena`} ayuda={ayudaContrasena}
          error={datos.contrasena && !validaContrasena(datos.contrasena)
            ? contrasenaLibre ? 'Ingresa una contraseña de hasta 72 bytes UTF-8.' : 'Aún no cumple los requisitos.'
            : ''}>
          <input className={claseEntrada} id={`${id}-contrasena`} type="text" autoComplete="off" maxLength="72" value={datos.contrasena}
            onChange={(e) => setDatos({ ...datos, contrasena: e.target.value })} />
        </Campo>
        <div><Boton type="submit" disabled={!listo || estado.guardando}>{estado.guardando ? 'Guardando…' : 'Asignar contraseña'}</Boton></div>
      </form>
    </Tarjeta>
  );
}
