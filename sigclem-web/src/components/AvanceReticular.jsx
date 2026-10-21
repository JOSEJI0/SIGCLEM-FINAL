import { Insignia } from './ui';

/** Texto que aparece al pasar el mouse (o enfocar con teclado) sobre un nivel del avance reticular. */
function detalleNivel(n) {
  if (n.calificacion !== null && n.calificacion !== undefined) {
    return `Calificación final: ${n.calificacion}${n.estatus === 'Reprobado' ? ' (no aprobatoria)' : ''}`;
  }
  switch (n.estatus) {
    case 'Exento': return `Exento por ${(n.origen || 'examen').toLowerCase()}`;
    case 'Acreditado': return `Acreditado${n.origen ? ` por ${n.origen.toLowerCase()}` : ''}`;
    case 'Cursando': return 'En curso: aún sin calificación';
    case 'Habilitado': return 'Nivel disponible para inscribirse';
    default: return 'Sin calificación registrada';
  }
}

/** Avance de los diez niveles; al pasar el mouse sobre un nivel se muestra su calificación. */
export default function AvanceReticular({ avance, compacto = false }) {
  return (
    <ol className={`grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 ${compacto ? 'gap-2' : 'gap-2.5'}`}>
      {avance.map((n) => {
        const detalle = detalleNivel(n);
        return (
          <li key={n.numero} tabIndex={0} aria-label={`${n.nombre}: ${n.estatus}. ${detalle}`}
            className={`group relative rounded-[9px] border border-linea p-3 outline-none transition hover:border-marino/40 hover:shadow-sm focus-visible:ring-2 focus-visible:ring-marino/30 ${n.estatus === 'No permitido' ? 'bg-[#FAFBFC]' : 'bg-white'}`}>
            <div className="text-[11px] text-tenue">Nivel {n.numero} · {n.nivelMcer}</div>
            <div className="mb-2 mt-0.5 flex items-center justify-between gap-1 font-semibold">
              <span>{n.nombre}</span>
              {n.calificacion !== null && n.calificacion !== undefined && (
                <span className="rounded bg-fondo px-1.5 text-[11px] font-semibold text-tenue">{n.calificacion}</span>
              )}
            </div>
            <Insignia estatus={n.estatus} />
            <div role="tooltip"
              className="pointer-events-none absolute bottom-full left-1/2 z-20 mb-2 w-max max-w-[220px] -translate-x-1/2 rounded-lg bg-tinta px-3 py-2 text-center text-xs font-medium text-white opacity-0 shadow-lg transition group-hover:opacity-100 group-focus:opacity-100">
              {detalle}
              <span className="absolute left-1/2 top-full -translate-x-1/2 border-4 border-transparent border-t-tinta" />
            </div>
          </li>
        );
      })}
    </ol>
  );
}
