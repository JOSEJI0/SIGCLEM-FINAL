import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { CLAVE_SESION, EVENTO_SESION_EXPIRADA, leerSesion } from '../api/cliente';
import { iniciarSesion, obtenerPerfil, obtenerPerfilPrueba } from '../api/auth';
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [sesion, setSesion] = useState(() => leerSesion());
  const [expirada, setExpirada] = useState(false);

  useEffect(() => {
    const manejarSesionExpirada = (evento) => {
      setExpirada(!evento.detail?.sinAviso);
      setSesion(null);
      localStorage.removeItem(CLAVE_SESION);
    };

    window.addEventListener(EVENTO_SESION_EXPIRADA, manejarSesionExpirada);
    return () => window.removeEventListener(EVENTO_SESION_EXPIRADA, manejarSesionExpirada);
  }, []);

  // Al abrir el sistema se refresca el perfil guardado (por ejemplo, si el alumno habla una lengua materna).
  useEffect(() => {
    const guardada = leerSesion();
    if (!guardada) return;
    const consulta = guardada.usuarioPrueba ? obtenerPerfilPrueba(guardada.usuarioPrueba) : obtenerPerfil();
    consulta.then((perfil) => {
      const actual = leerSesion();
      if (!actual || actual.perfil?.usuario !== perfil.usuario) return;
      const nueva = { ...actual, perfil };
      localStorage.setItem(CLAVE_SESION, JSON.stringify(nueva));
      setSesion(nueva);
    }).catch(() => {});
  }, []);

  /**
   * Modo pruebas: entra como cualquier cuenta sin contraseña. La API recibe el usuario en la cabecera
   * X-Usuario-Prueba (el token "dev-token" anterior era rechazado por el backend y cerraba la sesión).
   */
  const entrarComo = useCallback(async (usuario) => {
    const perfilPrueba = await obtenerPerfilPrueba(usuario);
    const nuevaSesion = {
      usuarioPrueba: perfilPrueba.usuario,
      perfil: perfilPrueba,
      expiraEn: Date.now() + 1000 * 60 * 60 * 12,
    };
    localStorage.setItem(CLAVE_SESION, JSON.stringify(nuevaSesion));
    setExpirada(false);
    setSesion(nuevaSesion);
    return perfilPrueba;
  }, []);

  const cerrarSesion = useCallback(() => {
    setExpirada(false);
    setSesion(null);
    localStorage.removeItem(CLAVE_SESION);
  }, []);

  const login = useCallback(async (usuario, contrasena) => {
    const datos = await iniciarSesion(usuario, contrasena);
    const nueva = {
      token: datos.token,
      perfil: datos.perfil,
      expiraEn: Date.now() + datos.expiraEnSegundos * 1000,
    };
    localStorage.setItem(CLAVE_SESION, JSON.stringify(nueva));
    setExpirada(false);
    setSesion(nueva);
    return nueva.perfil;
  }, []);

  const valor = useMemo(
    () => ({
      sesion,
      perfil: sesion?.perfil ?? null,
      login,
      cerrarSesion,
      entrarComo,
      modoPruebas: Boolean(sesion?.usuarioPrueba),
      expirada,
    }),
    [sesion, login, cerrarSesion, entrarComo, expirada],
  );
  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth debe usarse dentro de AuthProvider');
  return ctx;
}
