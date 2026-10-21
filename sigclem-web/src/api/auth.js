import { api, CABECERA_USUARIO_PRUEBA } from './cliente';

export async function iniciarSesion(usuario, contrasena) {
  const { data } = await api.post('/auth/login', { usuario, contrasena });
  return data; // { token, tipo, expiraEnSegundos, perfil }
}

export async function obtenerPerfil() {
  const { data } = await api.get('/auth/perfil');
  return data;
}

/** Modo pruebas: obtiene el perfil de una cuenta sin contraseña. */
export async function obtenerPerfilPrueba(usuario) {
  const { data } = await api.get('/auth/perfil', { headers: { [CABECERA_USUARIO_PRUEBA]: usuario } });
  return data;
}
