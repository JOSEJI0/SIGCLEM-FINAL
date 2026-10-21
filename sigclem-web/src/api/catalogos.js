import { api } from './cliente';

export const obtenerCarreras = () => api.get('/catalogos/carreras').then((r) => r.data);
export const obtenerNiveles = () => api.get('/catalogos/niveles').then((r) => r.data);
export const obtenerPeriodos = () => api.get('/catalogos/periodos').then((r) => r.data);
export const crearPeriodo = (datos) => api.post('/catalogos/periodos', datos).then((r) => r.data);
export const obtenerModalidades = () => api.get('/catalogos/modalidades').then((r) => r.data);
export const obtenerAulas = () => api.get('/catalogos/aulas').then((r) => r.data);
export const crearAula = (datos) => api.post('/catalogos/aulas', datos).then((r) => r.data);
