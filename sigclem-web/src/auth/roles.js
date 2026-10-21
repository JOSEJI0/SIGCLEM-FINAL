export const ROLES = {
  ALUMNO: 'ALUMNO',
  FACILITADOR: 'FACILITADOR',
  COORDINADOR: 'COORDINADOR',
  JEFA_DGTV: 'JEFA_DGTV',
  ADMIN: 'ADMIN',
};

export const ETIQUETA_ROL = {
  ALUMNO: 'Alumno',
  FACILITADOR: 'Facilitador',
  COORDINADOR: 'Coordinador de lenguas',
  JEFA_DGTV: 'Jefa del DGTV',
  ADMIN: 'Administrador',
};

// El administrador usa el área de coordinación (hace todo lo del coordinador) más la pantalla de auditoría.
const INICIO_POR_ROL = {
  ALUMNO: '/alumno',
  FACILITADOR: '/facilitador',
  COORDINADOR: '/coordinacion',
  JEFA_DGTV: '/supervision',
  ADMIN: '/coordinacion',
};

/** El administrador tiene los mismos permisos de escritura que el coordinador. */
export const esCoordinacion = (rol) => rol === ROLES.COORDINADOR || rol === ROLES.ADMIN;

/** Ruta inicial de cada rol (Figura 36, mapa de navegación). */
export function rutaInicio(rol) {
  return INICIO_POR_ROL[rol] || '/login';
}

/** La jefa del DGTV solo consulta (cambio 01). */
export const esSoloConsulta = (rol) => rol === ROLES.JEFA_DGTV;
