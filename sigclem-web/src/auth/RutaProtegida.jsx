import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';
import { rutaInicio } from './roles';

export default function RutaProtegida({ roles = [] }) {
  const { perfil, modoPruebas } = useAuth();
  const { pathname } = useLocation();

  if (!perfil) {
    return <Navigate to="/login" replace state={{ desde: pathname }} />;
  }

  if (roles.length > 0 && !roles.includes(perfil.rol)) {
    // Modo pruebas: al cambiar de perfil desde el encabezado se pasa al inicio del nuevo rol.
    return <Navigate to={modoPruebas ? rutaInicio(perfil.rol) : '/no-autorizado'} replace />;
  }

  return <Outlet />;
}
