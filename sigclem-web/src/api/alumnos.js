import { api } from './cliente';

/** El coordinador crea la cuenta de un alumno (contraseña inicial = número de control). */
export const crearAlumno = (datos) => api.post('/alumnos/registro', datos).then((r) => r.data);
/**
 * Filtros por partes (todos opcionales): idCarrera, numeroControl, nombre, semestre, nivel (1 a 10; 0 = idioma
 * acreditado) y estatus ("Activo", "Baja" o "Todos"; por omisión solo activos).
 */
export const buscarAlumnos = ({
  busqueda = '', idCarrera, numeroControl, nombre, nivel, semestre, estatus, pagina = 0, tamano = 20,
} = {}) => {
  const params = { page: pagina, size: tamano };
  if (busqueda) params.busqueda = busqueda;
  if (idCarrera) params.idCarrera = idCarrera;
  if (numeroControl) params.numeroControl = numeroControl;
  if (nombre) params.nombre = nombre;
  if (nivel !== undefined && nivel !== null && nivel !== '') params.nivel = nivel;
  if (semestre) params.semestre = semestre;
  if (estatus) params.estatus = estatus;
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
/** Descarga la plantilla de Excel (blob). */
export const descargarPlantillaAlumnos = () =>
  api.get('/alumnos/plantilla', { responseType: 'blob' }).then((r) => r.data);
/** Expediente integral: avance, historial, pagos, exámenes, diagnósticos, constancias y lengua materna. */
export const obtenerExpedienteIntegral = (numeroControl) =>
  api.get(`/alumnos/${encodeURIComponent(numeroControl)}/integral`).then((r) => r.data);
export const obtenerExpediente = (numeroControl) =>
  api.get(`/alumnos/${encodeURIComponent(numeroControl)}`).then((r) => r.data);
/** El alumno confirma o actualiza el semestre que cursa. */
export const actualizarMiSemestre = (semestre) => api.put('/alumnos/yo/semestre', { semestre }).then((r) => r.data);
