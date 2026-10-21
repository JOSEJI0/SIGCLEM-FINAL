import { api } from './cliente';

export const listarNotificacionesRecibidas = () => api.get('/notificaciones/recibidas').then((r) => r.data);
export const solicitarDiagnostico = () => api.post('/diagnosticos').then((r) => r.data);
export const listarMisDiagnosticos = () => api.get('/diagnosticos/mias').then((r) => r.data);
export const listarDiagnosticos = () => api.get('/diagnosticos').then((r) => r.data);
export const programarDiagnostico = (id, datos) =>
  api.put(`/diagnosticos/${encodeURIComponent(id)}/programacion`, datos).then((r) => r.data);
export const registrarDiagnosticoDirecto = (datos) => api.post('/diagnosticos/registro', datos).then((r) => r.data);
export const registrarResultadoDiagnostico = (id, datos) =>
  api.put(`/diagnosticos/${encodeURIComponent(id)}/resultado`, datos).then((r) => r.data);
