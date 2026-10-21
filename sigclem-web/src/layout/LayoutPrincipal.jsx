import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { Braces, Check, ChevronDown, FlaskConical, LogOut, Menu, Repeat, X } from 'lucide-react';
import { URL_DOCUMENTACION_API } from '../api/pruebas';
import { useAuth } from '../auth/AuthContext';
import { ETIQUETA_ROL, rutaInicio } from '../auth/roles';
import { obtenerPerfilesDemo } from '../auth/perfilesDemo';
import { contarNoLeidos } from '../api/mensajes';
import emblemaTecnm from '../assets/logos/tecnm-emblema-blanco.png';
import { IconoNotificaciones, MENUS } from './menus';

function iniciales(nombre = '') {
  return nombre.split(/\s+/).filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase() || 'U';
}

/** Distribución común: menú lateral por rol y barra superior (guía de estilo, Tabla 64). */
export default function LayoutPrincipal() {
  const { perfil, cerrarSesion, modoPruebas, entrarComo } = useAuth();
  const [perfilesDemo, setPerfilesDemo] = useState([]);
  const [menuPerfiles, setMenuPerfiles] = useState(false);
  const [cambiando, setCambiando] = useState(false);
  const [noLeidos, setNoLeidos] = useState(0);

  // Mensajes sin leer en la campana: se actualiza cada minuto y al leer un mensaje.
  useEffect(() => {
    if (!perfil) return undefined;
    const actualizar = () => contarNoLeidos().then(setNoLeidos).catch(() => {});
    actualizar();
    const intervalo = window.setInterval(actualizar, 60000);
    window.addEventListener('sigclem:mensajes-leidos', actualizar);
    return () => { window.clearInterval(intervalo); window.removeEventListener('sigclem:mensajes-leidos', actualizar); };
  }, [perfil]);
  const [abierto, setAbierto] = useState(false);
  const { pathname } = useLocation();
  const navegar = useNavigate();
  const menuRol = MENUS[perfil?.rol] || MENUS.COORDINADOR;
  const menu = { ...menuRol, elementos: menuRol.elementos.filter((e) => !e.requiereLenguaMaterna || perfil?.hablaLenguaMaterna) };
  const actual = [...menu.elementos].reverse().find((e) => (e.fin ? pathname === e.a : pathname.startsWith(e.a)));

  const salir = () => {
    cerrarSesion();
    navegar('/login', { replace: true });
  };

  // Modo pruebas: perfiles de alumno y coordinador para «Cambiar usuario».
  useEffect(() => {
    if (modoPruebas) obtenerPerfilesDemo().then(setPerfilesDemo).catch(() => setPerfilesDemo([]));
  }, [modoPruebas]);

  const cambiarA = async (usuario) => {
    setMenuPerfiles(false);
    if (usuario === perfil?.usuario) return;
    setCambiando(true);
    try {
      const p = await entrarComo(usuario);
      navegar(rutaInicio(p.rol), { replace: true });
    } finally {
      setCambiando(false);
    }
  };

  return (
    <div className="flex min-h-full">
      {abierto && <div className="fixed inset-0 z-30 bg-black/30 lg:hidden" onClick={() => setAbierto(false)} />}
      <aside className={`fixed inset-y-0 left-0 z-40 flex w-[236px] flex-col bg-marino text-white transition-transform lg:static lg:translate-x-0 ${abierto ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="border-b border-white/10 px-5 pb-4 pt-5">
          <div className="flex items-center justify-between">
            <span className="flex items-center gap-2.5">
              <img src={emblemaTecnm} alt="TecNM" className="h-9 w-auto" />
              <b className="text-xl tracking-wide">SIGCLEM</b>
            </span>
            <button className="lg:hidden" onClick={() => setAbierto(false)} aria-label="Cerrar menú"><X className="h-5 w-5" /></button>
          </div>
          <small className="mt-1 block text-[11px] leading-snug text-[#C9D3E6]">Coordinación de Lenguas Extranjeras y Maternas · ITCH</small>
        </div>
        <nav className="flex-1 overflow-y-auto px-2.5 py-3" aria-label="Menú principal">
          <div className="mx-2.5 mb-1.5 mt-3 text-[10.5px] uppercase tracking-wider text-[#9FB0CE]">{menu.seccion}</div>
          {menu.elementos.map((e) => (
            <div key={e.a}>
              {e.seccion && <div className="mx-2.5 mb-1.5 mt-4 text-[10.5px] uppercase tracking-wider text-[#9FB0CE]">{e.seccion}</div>}
              <NavLink
                to={e.a}
                end={e.fin}
                onClick={() => setAbierto(false)}
                className={({ isActive }) =>
                  `mb-0.5 flex items-center gap-2.5 rounded-lg px-2.5 py-2 transition ${isActive ? 'bg-white font-semibold text-marino' : 'text-[#DCE4F2] hover:bg-white/10'}`}
              >
                <e.icono className="h-4 w-4" aria-hidden />
                {e.etiqueta}
              </NavLink>
            </div>
          ))}
        </nav>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-[62px] items-center gap-4 border-b border-linea bg-white px-4 sm:px-6">
          <button className="lg:hidden" onClick={() => setAbierto(true)} aria-label="Abrir menú"><Menu className="h-5 w-5" /></button>
          <div>
            <h1 className="text-lg font-semibold leading-tight">{actual?.etiqueta ?? 'SIGCLEM'}</h1>
            <div className="text-xs text-tenue">{menu.seccion}</div>
          </div>

          <div className="ml-auto flex items-center gap-4">
            {modoPruebas && (
              <span className="hidden items-center gap-1.5 rounded-full bg-aviso-fondo px-2.5 py-1 text-[11.5px] font-semibold text-[#7A5210] md:inline-flex"
                title="Sesión sin contraseña (modo pruebas)">
                <FlaskConical className="h-3.5 w-3.5" aria-hidden />
              </span>
            )}
            {modoPruebas && (
              <a href={URL_DOCUMENTACION_API} target="_blank" rel="noreferrer" title="Ver todos los endpoints de la API"
                aria-label="Ver todos los endpoints de la API" className="text-tenue hover:text-marino">
                <Braces className="h-5 w-5" />
              </a>
            )}
            {perfil && (
              <Link to={`${rutaInicio(perfil.rol)}/mensajes`} aria-label={`Mensajes${noLeidos ? `: ${noLeidos} sin leer` : ''}`}
                title="Mensajes" className="relative text-tenue hover:text-marino">
                <IconoNotificaciones className="h-5 w-5" />
                {noLeidos > 0 && (
                  <span className="absolute -right-2 -top-1.5 min-w-[17px] rounded-full bg-error px-1 text-center text-[10px] font-bold leading-[17px] text-white">
                    {noLeidos > 99 ? '99+' : noLeidos}
                  </span>
                )}
              </Link>
            )}
            <div className="flex items-center gap-2.5">
              <div className="flex h-[34px] w-[34px] items-center justify-center rounded-full bg-marino text-[13px] font-semibold text-white">
                {iniciales(perfil?.nombre)}
              </div>
              <div className="hidden sm:block">
                <div className="text-[13px] font-semibold leading-tight">{perfil?.nombre}</div>
                <div className="text-[11.5px] text-tenue">{ETIQUETA_ROL[perfil?.rol]}</div>
              </div>
            </div>
            {modoPruebas ? (
              <div className="relative">
                <button
                  type="button"
                  onClick={() => setMenuPerfiles((v) => !v)}
                  disabled={cambiando}
                  aria-haspopup="menu"
                  aria-label="Cambiar usuario"
                  aria-expanded={menuPerfiles}
                  className="inline-flex items-center gap-2 whitespace-nowrap rounded-lg border border-[#C9D1DD] px-2.5 py-2 text-[13px] font-semibold text-marino transition hover:bg-fondo disabled:opacity-60 sm:px-3"
                >
                  <Repeat className="h-4 w-4" aria-hidden />
                  <span className="hidden sm:inline">{cambiando ? 'Cambiando…' : 'Cambiar usuario'}</span>
                  <ChevronDown className="h-3.5 w-3.5" aria-hidden />
                </button>
                {menuPerfiles && (
                  <>
                    <div className="fixed inset-0 z-40" onClick={() => setMenuPerfiles(false)} />
                    <div role="menu" className="absolute right-0 z-50 mt-2 w-64 rounded-xl border border-linea bg-white p-1.5 shadow-[0_10px_30px_rgba(20,40,80,.12)]">
                      {perfilesDemo.map((p) => (
                        <button key={p.id} type="button" role="menuitem" onClick={() => cambiarA(p.usuario)}
                          className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left hover:bg-fondo">
                          <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-marino text-[12px] font-semibold text-white">
                            {iniciales(p.nombre)}
                          </span>
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-[13px] font-semibold">{p.nombre}</span>
                            <span className="block text-[11.5px] text-tenue">{ETIQUETA_ROL[p.rol]}</span>
                          </span>
                          {p.usuario === perfil?.usuario && <Check className="h-4 w-4 text-marino" aria-label="Perfil actual" />}
                        </button>
                      ))}
                    </div>
                  </>
                )}
              </div>
            ) : (
              <button
                type="button"
                onClick={salir}
                className="inline-flex items-center gap-2 whitespace-nowrap rounded-lg border border-[#C9D1DD] px-2.5 py-2 text-[13px] font-semibold text-marino transition hover:bg-fondo sm:px-3"
                aria-label="Cerrar sesión"
                title="Cerrar sesión"
              >
                <LogOut className="h-4 w-4" aria-hidden />
                <span className="hidden sm:inline">Cerrar sesión</span>
              </button>
            )}
          </div>
        </header>
        <main className="flex flex-col gap-[18px] p-4 sm:px-6 sm:py-[22px]">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
