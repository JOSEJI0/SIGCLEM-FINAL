import { Link } from 'react-router-dom';
import { ShieldAlert } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { rutaInicio } from '../auth/roles';

function Mensaje({ titulo, texto }) {
  const { perfil } = useAuth();
  return (
    <div className="flex min-h-full items-center justify-center p-6">
      <div className="max-w-md rounded-2xl border border-linea bg-white p-8 text-center">
        <ShieldAlert className="mx-auto h-9 w-9 text-marino" />
        <h1 className="mt-3 text-xl font-bold">{titulo}</h1>
        <p className="mt-2 text-tenue">{texto}</p>
        <Link to={perfil ? rutaInicio(perfil.rol) : '/login'} className="mt-5 inline-block font-semibold text-marino hover:underline">
          Volver al inicio
        </Link>
      </div>
    </div>
  );
}

export const NoAutorizado = () => <Mensaje titulo="Acceso no permitido" texto="Tu rol no tiene acceso a esta sección." />;
export const NoEncontrado = () => <Mensaje titulo="Página no encontrada" texto="La dirección que buscas no existe." />;
