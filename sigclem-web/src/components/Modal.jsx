import { useEffect, useState } from 'react';
import { TriangleAlert, X } from 'lucide-react';
import { Boton, claseEntrada } from './ui';

/** Ventana modal genérica. */
export function Modal({ titulo, onCerrar, children, ancho = 'max-w-lg' }) {
  useEffect(() => {
    const alPulsar = (e) => e.key === 'Escape' && onCerrar();
    window.addEventListener('keydown', alPulsar);
    return () => window.removeEventListener('keydown', alPulsar);
  }, [onCerrar]);
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" onClick={onCerrar}>
      <div role="dialog" aria-modal="true" aria-label={titulo}
        className={`max-h-[92vh] w-full overflow-y-auto rounded-xl bg-white p-5 shadow-xl ${ancho}`}
        onClick={(e) => e.stopPropagation()}>
        <div className="mb-4 flex items-center justify-between gap-3">
          <h3 className="text-base font-semibold">{titulo}</h3>
          <button type="button" onClick={onCerrar} aria-label="Cerrar" className="text-tenue hover:text-tinta">
            <X className="h-5 w-5" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}

/**
 * Confirmación doble para acciones delicadas: primero se pide confirmar y después escribir una palabra clave
 * para evitar borrados por equivocación.
 */
export function ConfirmacionDoble({ titulo, mensaje, palabra = 'BORRAR', textoBoton = 'Borrar', onConfirmar, onCerrar }) {
  const [paso, setPaso] = useState(1);
  const [texto, setTexto] = useState('');
  const [ocupado, setOcupado] = useState(false);
  const confirmar = async () => {
    setOcupado(true);
    try {
      await onConfirmar();
    } finally {
      setOcupado(false);
    }
  };
  return (
    <Modal titulo={titulo} onCerrar={onCerrar} ancho="max-w-md">
      <div className="flex gap-3 rounded-lg bg-aviso-fondo p-3 text-[13px] text-[#7A5210]">
        <TriangleAlert className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
        <div>{mensaje}</div>
      </div>
      {paso === 1 ? (
        <div className="mt-5 flex justify-end gap-2">
          <Boton variante="secundario" onClick={onCerrar}>Cancelar</Boton>
          <Boton variante="peligro" onClick={() => setPaso(2)}>Sí, continuar</Boton>
        </div>
      ) : (
        <div className="mt-4 grid gap-3">
          <label htmlFor="confirmacion-doble" className="text-[13px]">
            Para confirmar, escribe <b>{palabra}</b>:
          </label>
          <input id="confirmacion-doble" className={claseEntrada} value={texto} autoFocus
            onChange={(e) => setTexto(e.target.value.toUpperCase())} />
          <div className="flex justify-end gap-2">
            <Boton variante="secundario" onClick={onCerrar}>Cancelar</Boton>
            <Boton variante="peligro" disabled={texto !== palabra || ocupado} onClick={confirmar}>
              {ocupado ? 'Procesando…' : textoBoton}
            </Boton>
          </div>
        </div>
      )}
    </Modal>
  );
}
