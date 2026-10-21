import { AlertCircle, CheckCircle2, Info, TriangleAlert } from 'lucide-react';
import { clasesTono, tonoEstatus } from '../utils/estatus';

export function Tarjeta({ titulo, icono: Icono, extra, acciones, children, className = '' }) {
  return (
    <section className={`rounded-[10px] border border-linea bg-white p-[18px] ${className}`}>
      {titulo && (
        <h2 className="mb-3 flex flex-wrap items-center gap-2 text-[14.5px] font-semibold">
          {Icono && <Icono className="h-4 w-4" aria-hidden />}
          {titulo}
          {extra && <span className={`text-xs font-normal text-tenue ${acciones ? '' : 'ml-auto'}`}>{extra}</span>}
          {acciones && <span className="ml-auto">{acciones}</span>}
        </h2>
      )}
      {children}
    </section>
  );
}

export function Insignia({ estatus, tono, children }) {
  const t = tono || tonoEstatus(estatus);
  return (
    <span className={`inline-block whitespace-nowrap rounded-full px-2.5 py-0.5 text-[11.5px] font-semibold ${clasesTono(t)}`}>
      {children || estatus}
    </span>
  );
}

const ICONOS_ALERTA = { info: Info, aviso: TriangleAlert, error: AlertCircle, ok: CheckCircle2 };
const CLASES_ALERTA = {
  info: 'bg-info-fondo text-[#1C4A7E] border-[#C7DAF0]',
  aviso: 'bg-aviso-fondo text-[#7A5210] border-[#F1D9A9]',
  error: 'bg-error-fondo text-[#8E2A20] border-[#F0C3BD]',
  ok: 'bg-ok-fondo text-[#155E3D] border-[#BFE3CF]',
};

export function Alerta({ tipo = 'info', children, className = '' }) {
  const Icono = ICONOS_ALERTA[tipo];
  return (
    <div role={tipo === 'error' ? 'alert' : 'status'}
      className={`flex items-start gap-2.5 rounded-lg border px-3.5 py-2.5 text-[13px] ${CLASES_ALERTA[tipo]} ${className}`}>
      <Icono className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
      <div>{children}</div>
    </div>
  );
}

export function Boton({ variante = 'primario', className = '', children, ...props }) {
  const estilos = {
    primario: 'bg-marino text-white hover:bg-marino-oscuro',
    secundario: 'bg-white border border-[#C9D1DD] text-tinta hover:bg-fondo',
    peligro: 'bg-white border border-[#E3B3AD] text-error hover:bg-error-fondo',
    exito: 'bg-ok text-white hover:brightness-95',
  };
  return (
    <button
      className={`inline-flex items-center justify-center gap-2 rounded-lg px-3.5 py-2 text-[13px] font-semibold transition disabled:cursor-not-allowed disabled:opacity-55 ${estilos[variante]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}

export function Campo({ etiqueta, error, ayuda, id, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-xs font-semibold text-[#3A4556]">{etiqueta}</label>
      {children}
      {error ? <small className="text-xs text-error">{error}</small>
        : ayuda ? <small className="text-xs text-tenue">{ayuda}</small> : null}
    </div>
  );
}

export const claseEntrada =
  'w-full rounded-lg border border-[#C9D1DD] bg-white px-3 py-2 text-[13.5px] outline-none transition focus:border-marino focus:ring-2 focus:ring-marino/15 aria-[invalid=true]:border-error';

export function Cargando({ texto = 'Cargando…' }) {
  return <p className="py-6 text-center text-tenue">{texto}</p>;
}
