import { api } from './cliente';

export const listarFacilitadores = () => api.get('/facilitadores').then((r) => r.data);
export const gruposDeFacilitador = (id) => api.get(`/facilitadores/${encodeURIComponent(id)}/grupos`).then((r) => r.data);
export const registrarFacilitador = (datos) => api.post('/facilitadores', datos).then((r) => r.data);
export const actualizarFacilitador = (id, datos) =>
  api.put(`/facilitadores/${encodeURIComponent(id)}`, datos).then((r) => r.data);
export const cambiarContrasenaFacilitador = (id, contrasena) =>
  api.put(`/facilitadores/${encodeURIComponent(id)}/contrasena`, { contrasena }).then((r) => r.data);
export const cambiarEstadoFacilitador = (id, activo) =>
  api.put(`/facilitadores/${encodeURIComponent(id)}/estado`, null, { params: { activo } }).then((r) => r.data);
/** Facilitador autenticado: sus datos, su teléfono y sus grupos de todos los periodos. */
export const obtenerMiPerfilFacilitador = () => api.get('/facilitadores/yo').then((r) => r.data);
export const actualizarMiTelefono = (telefono) => api.put('/facilitadores/yo/telefono', { telefono }).then((r) => r.data);
export const listarMisGruposHistorial = () => api.get('/facilitadores/yo/grupos').then((r) => r.data);
