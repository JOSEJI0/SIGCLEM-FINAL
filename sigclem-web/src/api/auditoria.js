import { api } from './cliente';

/** Bitácora de auditoría (solo administrador). Filtros opcionales: usuario y accion. */
export const consultarAuditoria = ({ usuario = '', accion = '', pagina = 0, tamano = 50 } = {}) => {
  const params = { page: pagina, size: tamano };
  if (usuario) params.usuario = usuario;
  if (accion) params.accion = accion;
  return api.get('/auditoria', { params }).then((r) => r.data);
};

export const listarAccionesAuditoria = () => api.get('/auditoria/acciones').then((r) => r.data);
