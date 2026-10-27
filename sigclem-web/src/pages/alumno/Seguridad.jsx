import { ShieldCheck } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { Alerta } from '../../components/ui';
import CambiarMiContrasena from '../../components/CambiarMiContrasena';

export default function SeguridadAlumno() {
  const { modoPruebas } = useAuth();

  return (
    <div className="grid gap-[18px]">
      <div>
        <h1 className="flex items-center gap-2 text-xl font-bold">
          <ShieldCheck className="h-5 w-5" /> Seguridad de la cuenta
        </h1>
        <p className="mt-1 text-sm text-tenue">Actualiza la contraseña de tu cuenta confirmando primero la contraseña actual.</p>
      </div>
      {modoPruebas
        ? <Alerta tipo="info">Para cambiar la contraseña, inicia sesión con tu cuenta y contraseña reales. El modo de pruebas no utiliza contraseñas.</Alerta>
        : <CambiarMiContrasena />}
    </div>
  );
}
