import { api } from './cliente';

/** CU-04 Solicitar examen de colocación o cuatro habilidades. */
export const solicitarExamen = (datos) => api.post('/examenes', datos).then((r) => r.data);

/** CU-05 Programar fecha y hora del examen. */
export const programarExamen = (id, datos) =>
  api.put(`/examenes/${encodeURIComponent(id)}/programacion`, datos).then((r) => r.data);

/** CU-06 Capturar o corregir el resultado: { idNivelAsignado, puntaje?, observaciones? }. */
export const capturarResultadoExamen = (id, datos) =>
  api.put(`/examenes/${encodeURIComponent(id)}/resultado`, datos).then((r) => r.data);

/** Registro directo de un examen ya aplicado con su resultado (coordinación). */
export const registrarExamen = (datos) => api.post('/examenes/registro', datos).then((r) => r.data);

/** Editar tipo y fecha de aplicación. */
export const editarExamen = (id, datos) => api.put(`/examenes/${encodeURIComponent(id)}`, datos).then((r) => r.data);

/** Borrado lógico: el examen se oculta. */
export const ocultarExamen = (id) => api.delete(`/examenes/${encodeURIComponent(id)}`);

/** Consultar exámenes con filtros opcionales (?numeroControl, ?tipo, ?pendientes). */
export const listarExamenes = (params = {}) => api.get('/examenes', { params }).then((r) => r.data);

/** Obtener examen por identificador. */
export const obtenerExamen = (id) => api.get(`/examenes/${encodeURIComponent(id)}`).then((r) => r.data);
