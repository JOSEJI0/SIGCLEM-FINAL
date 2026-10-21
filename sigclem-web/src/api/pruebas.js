import { api, URL_BASE_API } from './cliente';

/** Ventana con todos los endpoints de la API (Swagger UI). */
export const URL_DOCUMENTACION_API = `${URL_BASE_API}/docs/ui`;

/** Indica si la API está en modo pruebas (sin login). Si el endpoint no existe, se asume que no. */
export const consultarModoPruebas = () =>
  api.get('/pruebas/estado').then((r) => r.data).catch(() => ({ modoPruebas: false }));

export const listarUsuariosPrueba = () => api.get('/pruebas/usuarios').then((r) => r.data);

export const cargarDatosDemo = () => api.post('/pruebas/datos-demo').then((r) => r.data);
