import { api } from './cliente';

export const listarConceptos = (incluirInactivos = false) =>
  api.get('/pagos/conceptos', { params: { incluirInactivos } }).then((r) => r.data);
export const crearConcepto = (datos) => api.post('/pagos/conceptos', datos).then((r) => r.data);
export const editarConcepto = (id, datos) =>
  api.put(`/pagos/conceptos/${encodeURIComponent(id)}`, datos).then((r) => r.data);
export const eliminarConcepto = (id) => api.delete(`/pagos/conceptos/${encodeURIComponent(id)}`);
export const activarConcepto = (id) =>
  api.patch(`/pagos/conceptos/${encodeURIComponent(id)}/activar`).then((r) => r.data);
export const desactivarConcepto = (id) =>
  api.patch(`/pagos/conceptos/${encodeURIComponent(id)}/desactivar`).then((r) => r.data);
export const listarTarifas = (incluirInactivas = false) =>
  api.get('/pagos/tarifas', { params: { incluirInactivas } }).then((r) => r.data);
export const crearTarifa = (datos) => api.post('/pagos/tarifas', datos).then((r) => r.data);
export const desactivarTarifa = (id) => api.patch(`/pagos/tarifas/${encodeURIComponent(id)}/desactivar`).then((r) => r.data);
/** Listado general de cargos (coordinación y supervisión); estatus opcional: Pendiente, Parcial o Liquidada. */
export const listarTodosLosCargos = (estatus) =>
  api.get('/pagos/cargos', { params: estatus ? { estatus } : {} }).then((r) => r.data);
export const listarAutorizaciones = () => api.get('/pagos/autorizaciones').then((r) => r.data);
export const listarCargos = (numeroControl) =>
  api.get(`/pagos/alumnos/${encodeURIComponent(numeroControl)}/cargos`).then((r) => r.data);
export const crearAutorizacion = (datos) => api.post('/pagos/autorizaciones', datos).then((r) => r.data);
/** El servidor lee el QR, consulta al SAT y, con OCR, coteja la descripción: puede tardar más que otras peticiones. */
export const cargarComprobante = (id, datos) =>
  api.post(`/pagos/autorizaciones/${encodeURIComponent(id)}/comprobante`, datos, { timeout: 90000 }).then((r) => r.data);
export const reverificarComprobante = (id) =>
  api.post(`/pagos/comprobantes/${encodeURIComponent(id)}/reverificar`, null, { timeout: 90000 }).then((r) => r.data);
export const listarComprobantesPendientes = () => api.get('/pagos/comprobantes/pendientes').then((r) => r.data);
export const capturarDatosComprobante = (id, datos) =>
  api.post(`/pagos/comprobantes/${encodeURIComponent(id)}/captura-manual`, datos).then((r) => r.data);
export const validarComprobante = (id, idGrupo) =>
  api.post(`/pagos/comprobantes/${encodeURIComponent(id)}/validar`, { idGrupo: idGrupo || null }).then((r) => r.data);
export const rechazarComprobante = (id, motivo) =>
  api.post(`/pagos/comprobantes/${encodeURIComponent(id)}/rechazar`, { motivo }).then((r) => r.data);
export const descargarComprobante = (id) =>
  api.get(`/pagos/comprobantes/${encodeURIComponent(id)}/archivo`, { responseType: 'blob' }).then((r) => r.data);
