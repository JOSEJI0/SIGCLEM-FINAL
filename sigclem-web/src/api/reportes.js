import { api } from './cliente';

export const obtenerReporteAcademico = (periodoId) =>
  api.get('/reportes/academico', { params: { periodoId } }).then((respuesta) => respuesta.data);
export const obtenerIndicadores = () => api.get('/reportes/indicadores').then((r) => r.data);
