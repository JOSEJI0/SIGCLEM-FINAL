import { useState } from 'react';
import { Table2 } from 'lucide-react';

/** Color de la serie de datos (validado: banda de luminosidad, croma y contraste sobre la superficie). */
const COLOR_SERIE = '#2563A6';

/** Cifra destacada (stat tile). */
export function Indicador({ etiqueta, valor, detalle, icono: Icono, tono = 'neutro' }) {
  const tonos = {
    neutro: 'bg-neutro-fondo text-tinta', info: 'bg-info-fondo text-info', ok: 'bg-ok-fondo text-ok',
    aviso: 'bg-aviso-fondo text-aviso', error: 'bg-error-fondo text-error',
  };
  return (
    <div className="flex items-center gap-3.5 rounded-[10px] border border-linea bg-white px-[18px] py-4">
      {Icono && <span className={`flex h-[42px] w-[42px] shrink-0 items-center justify-center rounded-[10px] ${tonos[tono]}`}><Icono className="h-5 w-5" aria-hidden /></span>}
      <span className="min-w-0">
        <span className="block text-2xl font-bold leading-none text-tinta">{valor}</span>
        <span className="mt-1 block text-xs text-tenue">{etiqueta}</span>
        {detalle && <span className="block text-[11px] text-tenue">{detalle}</span>}
      </span>
    </div>
  );
}

/**
 * Barras horizontales de una sola serie: valor en la punta, detalle al pasar el mouse o enfocar con teclado
 * y vista de tabla para consultar los datos sin depender del color ni del mouse.
 */
export function BarrasHorizontales({ titulo, datos, unidad = 'alumnos', etiquetaColumna = 'Categoría' }) {
  const [tabla, setTabla] = useState(false);
  const maximo = Math.max(1, ...datos.map((d) => d.valor));
  const total = datos.reduce((s, d) => s + d.valor, 0);

  return (
    <figure className="rounded-[10px] border border-linea bg-white p-[18px]">
      <figcaption className="mb-3 flex items-center justify-between gap-2">
        <span className="text-[14.5px] font-semibold text-tinta">{titulo}</span>
        <button type="button" onClick={() => setTabla(!tabla)} aria-pressed={tabla}
          className="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-semibold text-tenue hover:bg-fondo hover:text-tinta">
          <Table2 className="h-3.5 w-3.5" /> {tabla ? 'Ver gráfica' : 'Ver tabla'}
        </button>
      </figcaption>
      {datos.length === 0 ? <p className="text-sm text-tenue">Sin datos.</p> : tabla ? (
        <table className="w-full text-left text-[13px]">
          <thead className="border-b border-linea text-xs text-tenue">
            <tr><th className="py-1.5">{etiquetaColumna}</th><th className="py-1.5 text-right">{unidad[0].toUpperCase() + unidad.slice(1)}</th><th className="py-1.5 text-right">%</th></tr>
          </thead>
          <tbody>
            {datos.map((d) => (
              <tr key={d.etiqueta} className="border-b border-linea last:border-0">
                <td className="py-1.5">{d.etiqueta}</td>
                <td className="py-1.5 text-right font-semibold">{d.valor}</td>
                <td className="py-1.5 text-right text-tenue">{total ? Math.round((d.valor / total) * 100) : 0}%</td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <ul className="grid gap-1.5" role="list">
          {datos.map((d) => {
            const ancho = (d.valor / maximo) * 100;
            const texto = `${d.etiqueta}: ${d.valor} ${unidad}${total ? ` (${Math.round((d.valor / total) * 100)}%)` : ''}`;
            return (
              <li key={d.etiqueta} tabIndex={0} aria-label={texto}
                className="group relative grid grid-cols-[minmax(90px,35%)_1fr] items-center gap-3 rounded-md px-1 py-1 outline-none hover:bg-fondo focus-visible:ring-2 focus-visible:ring-marino/30">
                <span className="truncate text-[12.5px] text-tenue" title={d.etiqueta}>{d.etiqueta}</span>
                <span className="flex items-center gap-2">
                  {d.valor > 0 && (
                    <span className="block h-[16px] rounded-r-[4px] transition group-hover:brightness-110"
                      style={{ width: `${Math.max(ancho, 1.5)}%`, background: COLOR_SERIE }} aria-hidden />
                  )}
                  <span className="text-[12.5px] font-semibold text-tinta">{d.valor}</span>
                </span>
                <span role="tooltip"
                  className="pointer-events-none absolute -top-8 left-1/2 z-10 w-max -translate-x-1/2 rounded-md bg-tinta px-2.5 py-1 text-xs text-white opacity-0 shadow transition group-hover:opacity-100 group-focus:opacity-100">
                  <b>{d.valor}</b> {unidad} · {d.etiqueta}
                </span>
              </li>
            );
          })}
        </ul>
      )}
    </figure>
  );
}
