import { api } from './cliente';

export const obtenerMisBoletas = () => api.get('/boletas/mias').then((respuesta) => respuesta.data);
