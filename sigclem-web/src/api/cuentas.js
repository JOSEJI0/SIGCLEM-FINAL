import { api } from './cliente';

/** Contraseñas administradas por coordinación. */
export const cambiarMiContrasena = (contrasenaActual, contrasenaNueva) =>
  api.put('/cuentas/mi-contrasena', { contrasenaActual, contrasenaNueva });
export const asignarContrasenaAlumno = (numeroControl, contrasena) =>
  api.put(`/cuentas/alumnos/${encodeURIComponent(numeroControl)}/contrasena`, { contrasena });
export const asignarContrasenaJefa = (contrasena) => api.put('/cuentas/jefa/contrasena', { contrasena });
