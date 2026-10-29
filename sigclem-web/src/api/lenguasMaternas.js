import { api } from './cliente';

export const listarLenguasMaternas = () =>
  api.get('/lenguas-maternas/catalogo').then((respuesta) => respuesta.data);
export const crearLenguaMaterna = (datos) =>
  api.post('/lenguas-maternas/catalogo', datos).then((respuesta) => respuesta.data);
export const listarMisLenguasMaternas = () =>
  api.get('/lenguas-maternas/registros/mios').then((respuesta) => respuesta.data);
export const registrarLenguaMaterna = (datos, { curp, domicilio, ine }) => {
  const f = new FormData();
  f.append('idLengua', datos.idLengua ?? '');
  if (datos.nivelDominio) f.append('nivelDominio', datos.nivelDominio);
  f.append('curp', curp);
  f.append('domicilio', domicilio);
  f.append('ine', ine);
  return api.post('/lenguas-maternas/registros', f).then((r) => r.data);
};
export const listarRegistrosLenguasMaternas = () =>
  api.get('/lenguas-maternas/registros').then((respuesta) => respuesta.data);
export const cambiarEstatusLenguaMaterna = (id, estatus) =>
  api.patch(`/lenguas-maternas/registros/${encodeURIComponent(id)}/estatus`, { estatus })
    .then((respuesta) => respuesta.data);
export const descargarDocumentoLenguaMaterna = (id, tipo) =>
  api.get(`/lenguas-maternas/registros/${encodeURIComponent(id)}/documento${tipo ? `/${tipo}` : ''}`,
    { responseType: 'blob' }).then((r) => r.data);
export const subirDocumentosCoordinacion = (id, { intercultural, constancia }) => {
  const f = new FormData();
  if (intercultural) f.append('intercultural', intercultural);
  if (constancia) f.append('constancia', constancia);
  return api.post(`/lenguas-maternas/registros/${encodeURIComponent(id)}/documentos-coordinacion`, f).then((r) => r.data);
};