import { api } from './cliente';

export const listarLenguasMaternas = () =>
  api.get('/lenguas-maternas/catalogo').then((respuesta) => respuesta.data);
export const crearLenguaMaterna = (datos) =>
  api.post('/lenguas-maternas/catalogo', datos).then((respuesta) => respuesta.data);
export const listarMisLenguasMaternas = () =>
  api.get('/lenguas-maternas/registros/mios').then((respuesta) => respuesta.data);
export const registrarLenguaMaterna = (datos, documento) => {
  const formulario = new FormData();
  formulario.append('idLengua', datos.idLengua ?? '');
  if (datos.nivelDominio) formulario.append('nivelDominio', datos.nivelDominio);
  formulario.append('documento', documento);
  return api.post('/lenguas-maternas/registros', formulario).then((respuesta) => respuesta.data);
};
export const listarRegistrosLenguasMaternas = () =>
  api.get('/lenguas-maternas/registros').then((respuesta) => respuesta.data);
export const cambiarEstatusLenguaMaterna = (id, estatus) =>
  api.patch(`/lenguas-maternas/registros/${encodeURIComponent(id)}/estatus`, { estatus })
    .then((respuesta) => respuesta.data);
export const descargarDocumentoLenguaMaterna = (id) =>
  api.get(`/lenguas-maternas/registros/${encodeURIComponent(id)}/documento`, { responseType: 'blob' })
    .then((respuesta) => respuesta.data);
