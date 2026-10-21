import { useEffect, useMemo, useState } from 'react';
import { Boton } from './ui';

/**
 * Paginación en el navegador para listas largas que la API entrega completas (cargos, comprobantes):
 * evita dibujar miles de filas o tarjetas a la vez. Regresa a la primera página cuando cambia la lista.
 */
export function usePaginacion(elementos, tamano = 50) {
  const [pagina, setPagina] = useState(0);
  const lista = elementos || [];
  const totalPaginas = Math.max(1, Math.ceil(lista.length / tamano));
  useEffect(() => { setPagina(0); }, [lista.length, elementos]);
  const actual = Math.min(pagina, totalPaginas - 1);
  const visibles = useMemo(() => lista.slice(actual * tamano, (actual + 1) * tamano), [lista, actual, tamano]);
  return { visibles, pagina: actual, totalPaginas, total: lista.length, tamano, setPagina };
}

export default function Paginador({ paginacion }) {
  const { pagina, totalPaginas, total, tamano, setPagina } = paginacion;
  if (total <= tamano) return null;
  const desde = pagina * tamano + 1;
  const hasta = Math.min(total, (pagina + 1) * tamano);
  return (
    <div className="mt-4 flex items-center justify-between gap-3">
      <span className="text-xs text-tenue">
        {desde}–{hasta} de {total} · Página {pagina + 1} de {totalPaginas}
      </span>
      <div className="flex gap-2">
        <Boton variante="secundario" type="button" disabled={pagina === 0} onClick={() => setPagina(pagina - 1)}>
          Anterior
        </Boton>
        <Boton variante="secundario" type="button" disabled={pagina >= totalPaginas - 1} onClick={() => setPagina(pagina + 1)}>
          Siguiente
        </Boton>
      </div>
    </div>
  );
}
