import { api } from './cliente';

export const listarGrupos = (periodoId) =>
  api.get('/grupos', { params: periodoId ? { periodoId } : {} }).then((r) => r.data);
export const abrirGrupo = (datos) => api.post('/grupos', datos).then((r) => r.data);
export const editarGrupo = (id, datos) => api.put(`/grupos/${encodeURIComponent(id)}`, datos).then((r) => r.data);
export const eliminarGrupo = (id) => api.delete(`/grupos/${encodeURIComponent(id)}`);
export const alumnosDelGrupo = (id) => api.get(`/grupos/${encodeURIComponent(id)}/alumnos`).then((r) => r.data);
export const asignarAlumno = (id, numeroControl) =>
  api.post(`/grupos/${encodeURIComponent(id)}/alumnos`, { numeroControl }).then((r) => r.data);
export const quitarAlumno = (id, numeroControl) =>
  api.delete(`/grupos/${encodeURIComponent(id)}/alumnos/${encodeURIComponent(numeroControl)}`).then((r) => r.data);
/** Alumno: cursos de su siguiente nivel y su elección (encuesta de inicio de semestre). */
export const consultarOferta = () => api.get('/grupos/oferta').then((r) => r.data);
export const elegirGrupo = (id) => api.put(`/grupos/${encodeURIComponent(id)}/eleccion`).then((r) => r.data);
export const retirarEleccion = (id) => api.delete(`/grupos/${encodeURIComponent(id)}/eleccion`).then((r) => r.data);
export const obtenerActa = (id) => api.get(`/grupos/${encodeURIComponent(id)}`).then((r) => r.data);
export const guardarCalificaciones = (id, calificaciones) =>
  api.put(`/grupos/${encodeURIComponent(id)}/acta`, { calificaciones }).then((r) => r.data);
export const cerrarActa = (id) =>
  api.post(`/grupos/${encodeURIComponent(id)}/acta/cierre`).then((r) => r.data);
/** Coordinación: cierra el acta con observación aunque haya cursos sin liquidar (devuelve el detalle del grupo). */
export const cerrarActaCoordinacion = (id, observacion) =>
  api.post(`/grupos/${encodeURIComponent(id)}/acta/cierre-coordinacion`, { observacion }).then((r) => r.data);
/** Coordinación: asigna el nivel que quedó pendiente por falta de pago al cerrar el acta. */
export const asignarNivelPendiente = (id, numeroControl, observacion) =>
  api.post(`/grupos/${encodeURIComponent(id)}/alumnos/${encodeURIComponent(numeroControl)}/nivel`, { observacion })
    .then((r) => r.data);
/** Acta en PDF (preliminar mientras no esté cerrada). */
export const descargarActaPdf = (id) =>
  api.get(`/grupos/${encodeURIComponent(id)}/acta/pdf`, { responseType: 'blob' }).then((r) => r.data);
