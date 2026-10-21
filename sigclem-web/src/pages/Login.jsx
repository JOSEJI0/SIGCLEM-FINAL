import { useEffect, useState } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ArrowRight, Award, Eye, EyeOff, IdCard, Languages, Lock, QrCode, User } from 'lucide-react';
import { useAuth } from '../auth/AuthContext';
import { rutaInicio } from '../auth/roles';
import { mensajeError } from '../api/cliente';
import { Alerta, Boton, Campo, Cargando, claseEntrada } from '../components/ui';
import { obtenerPerfilesDemo } from '../auth/perfilesDemo';
import { consultarModoPruebas } from '../api/pruebas';
import logoTecnmBlanco from '../assets/logos/tecnm-blanco.png';
import logoClemBlanco from '../assets/logos/clem-blanco.png';
import logoClemColor from '../assets/logos/clem-color.png';

const MODULOS = [
  { icono: IdCard, texto: 'Expediente digital y avance reticular de inglés' },
  { icono: QrCode, texto: 'Validación de comprobantes de pago con código QR' },
  { icono: Award, texto: 'Constancias de acreditación con folio único' },
  { icono: Languages, texto: "Lenguas maternas: náhuatl, me'phaa y tu'un savi" },
];

/** CU-01 Iniciar sesión (Figura 37). */
export default function Login() {
  const { perfil, login, entrarComo, expirada } = useAuth();
  const navegar = useNavigate();
  const { state } = useLocation();
  const [usuario, setUsuario] = useState('');
  const [contrasena, setContrasena] = useState('');
  const [ver, setVer] = useState(false);
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  // 'consultando' mientras se pregunta a la API; 'pruebas' entra directo sin login; 'normal' muestra el formulario.
  const [modo, setModo] = useState('consultando');
  const [errorEntrada, setErrorEntrada] = useState('');

  useEffect(() => {
    let vigente = true;
    consultarModoPruebas().then(async (estado) => {
      if (!vigente) return;
      if (!estado?.modoPruebas) {
        setModo('normal');
        return;
      }
      setModo('pruebas');
      try {
        const [inicial] = await obtenerPerfilesDemo();
        if (!inicial) throw new Error('No hay cuentas de prueba disponibles.');
        const p = await entrarComo(inicial.usuario);
        if (vigente) navegar(rutaInicio(p.rol), { replace: true });
      } catch (e) {
        if (vigente) setErrorEntrada(mensajeError(e, 'No fue posible entrar al sistema.'));
      }
    });
    return () => { vigente = false; };
  }, []);

  if (perfil) return <Navigate to={rutaInicio(perfil.rol)} replace />;
  if (modo !== 'normal') {
    return (
      <div className="flex min-h-full items-center justify-center bg-[#F7F8FB] p-4">
        {errorEntrada ? <Alerta tipo="error">{errorEntrada}</Alerta> : <Cargando texto="Entrando a SIGCLEM…" />}
      </div>
    );
  }

  const enviar = async (e) => {
    e.preventDefault();
    if (!usuario.trim() || !contrasena) {
      setError('Ingresa tu usuario y tu contraseña.');
      return;
    }
    setEnviando(true);
    setError('');
    try {
      const p = await login(usuario.trim(), contrasena);
      const destino = state?.desde?.startsWith(rutaInicio(p.rol)) ? state.desde : rutaInicio(p.rol);
      navegar(destino, { replace: true });
    } catch (err) {
      setError(mensajeError(err, 'No fue posible iniciar sesión.'));
    } finally {
      setEnviando(false);
    }
  };

  return (
    <div className="grid min-h-full lg:grid-cols-[1.1fr_1fr]">
      <section className="relative hidden flex-col overflow-hidden bg-gradient-to-br from-marino to-[#0F2344] px-14 py-8 text-white lg:flex">
        {/* Motivo decorativo: el trazo del logo de la Coordinación, muy tenue, rellena el fondo. */}
        <img src={logoClemBlanco} alt="" aria-hidden
          className="pointer-events-none absolute -bottom-[190px] -right-28 w-[560px] max-w-none opacity-[0.06]" />

        <header className="relative flex items-center gap-4 border-b border-white/15 pb-5">
          <img src={logoTecnmBlanco} alt="Tecnológico Nacional de México" className="h-14 w-auto" />
          <div className="border-l border-white/25 pl-4 text-[12.5px] font-semibold uppercase leading-snug tracking-[1.5px] text-[#D8B98C]">
            Instituto Tecnológico<br />de Chilpancingo
          </div>
        </header>

        <div className="relative flex flex-1 flex-col justify-center gap-[3.5vh] py-[3vh]">
          <img src={logoClemBlanco} alt="Coordinación de Lenguas Extranjeras y Maternas"
            className="h-auto max-h-[24vh] w-auto max-w-[380px] self-start object-contain" />
          <div>
            <h1 className="text-5xl font-bold tracking-wide">SIGCLEM</h1>
            <p className="mt-3 max-w-lg text-base leading-relaxed text-[#C9D3E6]">
              Sistema web para la gestión de la información académica y administrativa de la Coordinación de Lenguas
              Extranjeras y Maternas.
            </p>
          </div>
          <ul className="grid max-w-xl grid-cols-2 gap-3 text-[13px] text-[#E3E9F5]">
            {MODULOS.map(({ icono: Icono, texto }) => (
              <li key={texto} className="flex items-start gap-3 rounded-xl border border-white/10 bg-white/[0.06] p-3.5">
                <Icono className="mt-0.5 h-5 w-5 shrink-0 text-[#D8B98C]" aria-hidden />
                <span className="leading-snug">{texto}</span>
              </li>
            ))}
          </ul>
        </div>

        <footer className="relative flex items-center justify-between border-t border-white/15 pt-5 text-xs text-[#9FB0CE]">
          <span>Coordinación de Lenguas Extranjeras y Maternas · Chilpancingo, Gro.</span>
          <span>TecNM · ITCH</span>
        </footer>
      </section>

      <section className="flex flex-col items-center justify-center gap-5 bg-[#F7F8FB] p-4">
        {/* En pantallas pequeñas no se muestra el panel azul: el logo encabeza el formulario. */}
        <img src={logoClemColor} alt="Coordinación de Lenguas Extranjeras y Maternas" className="w-56 lg:hidden" />
        <form onSubmit={enviar} noValidate className="w-full max-w-[400px] rounded-2xl border border-linea bg-white p-8 shadow-[0_10px_30px_rgba(20,40,80,.08)]">
          <h2 className="text-[22px] font-bold">Iniciar sesión</h2>
          <p className="mb-6 mt-1 text-tenue">Ingresa con la cuenta que te asignó la coordinación.</p>
          <div className="grid gap-4">
            {expirada && <Alerta tipo="aviso">Tu sesión terminó. Vuelve a iniciar sesión.</Alerta>}
            {error && <Alerta tipo="error">{error}</Alerta>}
            <Campo etiqueta="Usuario" id="usuario" ayuda="Número de control o usuario institucional">
              <div className="relative">
                <User className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-tenue" aria-hidden />
                <input id="usuario" autoComplete="username" className={`${claseEntrada} pl-9`} value={usuario}
                  onChange={(e) => setUsuario(e.target.value)} />
              </div>
            </Campo>
            <Campo etiqueta="Contraseña" id="contrasena">
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-tenue" aria-hidden />
                <input id="contrasena" type={ver ? 'text' : 'password'} autoComplete="current-password"
                  className={`${claseEntrada} px-9`} value={contrasena} onChange={(e) => setContrasena(e.target.value)} />
                <button type="button" onClick={() => setVer(!ver)} className="absolute right-3 top-1/2 -translate-y-1/2 text-tenue"
                  aria-label={ver ? 'Ocultar contraseña' : 'Mostrar contraseña'}>
                  {ver ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
            </Campo>
            <Boton type="submit" disabled={enviando} className="mt-1 py-2.5">
              {enviando ? 'Ingresando…' : <>Ingresar <ArrowRight className="h-4 w-4" /></>}
            </Boton>
            <p className="text-center text-xs text-tenue">
              ¿Olvidaste tu contraseña? Solicita el restablecimiento en la coordinación.
            </p>
            <p className="border-t border-linea pt-4 text-center text-[13px] text-tenue">
              ¿Aún no tienes cuenta? La coordinación de lenguas la crea y te entrega tus datos de acceso.
            </p>
          </div>
        </form>
      </section>
    </div>
  );
}
