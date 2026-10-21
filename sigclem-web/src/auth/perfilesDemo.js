import { listarUsuariosPrueba } from '../api/pruebas';

/** Alumno de la presentación (el de datos-demo con más avance); si no existe, se usa el primer alumno. */
const ALUMNO_PREFERIDO = '22520001';

/**
 * Modo pruebas: perfiles entre los que se alterna desde «Cambiar usuario» del encabezado
 * (coordinador, alumno, facilitador y jefa del DGTV). El primero (coordinador) es con el que se entra al abrir el sistema.
 */
export async function obtenerPerfilesDemo() {
  const activos = (await listarUsuariosPrueba()).filter((u) => u.activo);
  const primeroDe = (rol) => activos.find((u) => u.rol === rol);
  const alumnos = activos.filter((u) => u.rol === 'ALUMNO');
  const alumno = alumnos.find((u) => u.usuario === ALUMNO_PREFERIDO) || alumnos[0];
  return [primeroDe('COORDINADOR'), alumno, primeroDe('FACILITADOR'), primeroDe('JEFA_DGTV')].filter(Boolean);
}
