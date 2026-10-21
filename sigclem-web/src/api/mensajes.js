import { api } from './cliente';

/** Mensajería entre alumnos, facilitadores y coordinación. */
export const consultarDestinatarios = () => api.get('/mensajes/destinatarios').then((r) => r.data);
export const enviarMensaje = (datos) => api.post('/mensajes', datos).then((r) => r.data);
export const listarRecibidos = () => api.get('/mensajes/recibidos').then((r) => r.data);
export const listarEnviados = () => api.get('/mensajes/enviados').then((r) => r.data);
export const contarNoLeidos = () => api.get('/mensajes/no-leidos').then((r) => r.data.total);
export const marcarLeido = (id) => api.patch(`/mensajes/${encodeURIComponent(id)}/leido`);
