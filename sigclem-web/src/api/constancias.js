import { api } from './cliente';

export const consultarElegibilidadConstancia = () =>
  api.get('/constancias/disponibilidad').then((respuesta) => respuesta.data);
export const solicitarConstancia = () =>
  api.post('/constancias').then((respuesta) => respuesta.data);
export const listarMisConstancias = () =>
  api.get('/constancias/mias').then((respuesta) => respuesta.data);
export const listarConstancias = (estatus) =>
  api.get('/constancias', { params: estatus ? { estatus } : {} }).then((respuesta) => respuesta.data);
export const autorizarConstancia = (id, folio) =>
  api.post(`/constancias/${encodeURIComponent(id)}/autorizacion`, { folio }).then((respuesta) => respuesta.data);
/** Coordinación crea y emite una constancia aunque el alumno no la haya solicitado. */
export const emitirConstanciaDirecta = (numeroControl, folio) =>
  api.post('/constancias/emision', { numeroControl, folio }).then((respuesta) => respuesta.data);
export const rechazarConstancia = (id, motivo) =>
  api.post(`/constancias/${encodeURIComponent(id)}/rechazo`, { motivo }).then((respuesta) => respuesta.data);
export const descargarPdfConstancia = (id) =>
  api.get(`/constancias/${encodeURIComponent(id)}/pdf`, { responseType: 'blob' });
/** Constancia de avance por nivel de inglés (un PDF por cada nivel acreditado con acta cerrada). */
export const listarMisConstanciasAvance = () =>
  api.get('/constancias/avance/mias').then((respuesta) => respuesta.data);
export const listarConstanciasAvanceDeAlumno = (numeroControl) =>
  api.get(`/constancias/avance/alumnos/${encodeURIComponent(numeroControl)}`).then((respuesta) => respuesta.data);
export const descargarConstanciaAvance = (idInscripcion) =>
  api.get(`/constancias/avance/${encodeURIComponent(idInscripcion)}/pdf`, { responseType: 'blob' });
/** Firmantes, textos del oficio e imágenes del membrete del año (coordinación). */
export const obtenerConfiguracionConstancias = () =>
  api.get('/constancias/configuracion').then((respuesta) => respuesta.data);
export const actualizarConfiguracionConstancias = (datos) =>
  api.put('/constancias/configuracion', datos).then((respuesta) => respuesta.data);
export const guardarImagenConstancia = (tipo, datos) =>
  api.put(`/constancias/configuracion/imagenes/${encodeURIComponent(tipo)}`, datos).then((respuesta) => respuesta.data);
export const eliminarImagenConstancia = (tipo) =>
  api.delete(`/constancias/configuracion/imagenes/${encodeURIComponent(tipo)}`).then((respuesta) => respuesta.data);
export const obtenerImagenConstancia = (tipo) =>
  api.get(`/constancias/configuracion/imagenes/${encodeURIComponent(tipo)}`, { responseType: 'blob' }).then((respuesta) => respuesta.data);
export const vistaPreviaConstancia = (formato) =>
  api.get(`/constancias/configuracion/vista-previa/${encodeURIComponent(formato)}`, { responseType: 'blob' }).then((respuesta) => respuesta.data);
