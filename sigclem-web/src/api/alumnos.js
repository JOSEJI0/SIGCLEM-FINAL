import { api } from './cliente';

/** El coordinador crea la cuenta de un alumno (contraseña inicial = número de control). */
export const crearAlumno = (datos) => api.post('/alumnos/registro', datos).then((r) => r.data);
/**
 * Filtros por partes (todos opcionales): idCarrera, numeroControl, nombre, semestre, nivel (1 a 10; 0 = idioma
 * acreditado), estatus ("Activo", "Baja" o "Todos") y formaLiberacion.
 */
export const buscarAlumnos = ({
  busqueda = '', idCarrera, numeroControl, nombre, nivel, semestre, estatus, formaLiberacion, pagina = 0, tamano = 20,
} = {}) => {
  const params = { page: pagina, size: tamano };
  if (busqueda) params.busqueda = busqueda;
  if (idCarrera) params.idCarrera = idCarrera;
  if (numeroControl) params.numeroControl = numeroControl;
  if (nombre) params.nombre = nombre;
  if (nivel !== undefined && nivel !== null && nivel !== '') params.nivel = nivel;
  if (semestre) params.semestre = semestre;
  if (estatus) params.estatus = estatus;
  if (formaLiberacion) params.formaLiberacion = formaLiberacion;
  return api.get('/alumnos', { params }).then((r) => r.data);
};
/** Baja reversible de un alumno (motivo obligatorio). */
export const darDeBajaAlumno = (numeroControl, motivo) =>
  api.post(`/alumnos/${encodeURIComponent(numeroControl)}/baja`, { motivo }).then((r) => r.data);
export const reactivarAlumno = (numeroControl) =>
  api.post(`/alumnos/${encodeURIComponent(numeroControl)}/reactivar`).then((r) => r.data);
/** Libera la lengua extranjera por otra forma (Examen global, Lengua materna u Otra). */
export const liberarIdioma = (numeroControl, forma, detalle) =>
  api.post(`/alumnos/${encodeURIComponent(numeroControl)}/liberacion`, { forma, detalle }).then((r) => r.data);
/** Carga masiva de alumnos desde un archivo Excel. */
export const cargarAlumnosExcel = (archivo) => {
  const datos = new FormData();
  datos.append('archivo', archivo);
  return api.post('/alumnos/carga', datos, { headers: { 'Content-Type': 'multipart/form-data' } }).then((r) => r.data);
};
export const descargarPlantillaAlumnos = () =>
  api.get('/alumnos/plantilla-excel', { responseType: 'blob' }).then((r) => r.data);
/**
 * CU-03: consulta completa del expediente del alumno con su avance reticular en los diez niveles de inglés.
 * Rol: Alumno (solo su propio expediente), Coordinador.
 */
export const obtenerExpediente = (numeroControl) =>
  api.get(`/alumnos/${encodeURIComponent(numeroControl)}/expediente`).then((r) => r.data);

/**
 * Expediente integral (cambio 15): agrupa alumno, avance reticular, historial académico, cargos de pago,
 * exámenes y evaluaciones diagnósticas, constancias y registro de lengua materna en una sola consulta.
 */
export const obtenerExpedienteIntegral = (numeroControl) =>
  api.get(`/alumnos/${encodeURIComponent(numeroControl)}/expediente-integral`).then((r) => r.data);
