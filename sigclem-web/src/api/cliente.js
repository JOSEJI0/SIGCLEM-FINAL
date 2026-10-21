import axios from 'axios';

export const CLAVE_SESION = 'sigclem.sesion';
export const CABECERA_USUARIO_PRUEBA = 'X-Usuario-Prueba';
export const URL_BASE_API = import.meta.env.VITE_API_URL || '/api/v1';
export const EVENTO_SESION_EXPIRADA = 'sigclem:sesion-expirada';

/** Cliente HTTP único de la SPA (componente "Cliente API" de la Figura 29). */
export const api = axios.create({
  baseURL: URL_BASE_API,
  timeout: 20000,
});

export function leerSesion() {
  try {
    const sesion = JSON.parse(localStorage.getItem(CLAVE_SESION));
    if (sesion?.token === 'dev-token') return null; // sesión simulada de versiones anteriores (el backend la rechaza)
    // Sesión normal (token JWT) o sesión del modo pruebas (solo el nombre de la cuenta, sin contraseña).
    if ((!sesion?.token && !sesion?.usuarioPrueba) || !sesion?.expiraEn || Date.now() >= sesion.expiraEn) return null;
    return sesion;
  } catch {
    return null;
  }
}

api.interceptors.request.use((config) => {
  const sesion = leerSesion();
  if (sesion?.token) config.headers.Authorization = `Bearer ${sesion.token}`;
  else if (sesion?.usuarioPrueba && !config.headers[CABECERA_USUARIO_PRUEBA]) {
    config.headers[CABECERA_USUARIO_PRUEBA] = sesion.usuarioPrueba;
  }
  return config;
});

api.interceptors.response.use(
  (respuesta) => respuesta,
  (error) => {
    const url = error.config?.url || '';
    if (error.response?.status === 401 && !url.includes('/auth/login')) {
      // Una sesión del modo pruebas rechazada significa que la API ya exige login: se vuelve al inicio sin
      // el aviso de «sesión terminada».
      const sinAviso = Boolean(error.config?.headers?.[CABECERA_USUARIO_PRUEBA]);
      window.dispatchEvent(new CustomEvent(EVENTO_SESION_EXPIRADA, { detail: { sinAviso } }));
    }
    return Promise.reject(error);
  },
);

/**
 * Obtiene un mensaje legible a partir de una respuesta de error RFC 7807
 * ({ title, detail, codigo, errores }).
 */
export function mensajeError(error, porDefecto = 'No fue posible completar la operación.') {
  const datos = error?.response?.data;
  if (!error?.response) return 'No hay conexión con el servidor. Verifica tu red e intenta de nuevo.';
  if (datos?.errores) return Object.values(datos.errores).join(' ');
  return datos?.detail || datos?.title || porDefecto;
}

export function erroresDeCampo(error) {
  return error?.response?.data?.errores || {};
}
