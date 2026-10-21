export const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado'];
const DIAS_CORTOS = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb'];
const ALTO_HORA = 44;

/** Colores por nivel (1 a 10) para distinguir los grupos en el calendario. */
const COLORES = [
  'bg-[#E6EFF9] border-[#2563A6] text-[#1C4A7E]', 'bg-[#E4F4EC] border-[#1E8E5A] text-[#155E3D]',
  'bg-[#FDF3E1] border-[#B7791F] text-[#7A5210]', 'bg-[#EFE9F8] border-[#6B46C1] text-[#4C2F91]',
  'bg-[#FBE8E6] border-[#C0392B] text-[#8E2A20]', 'bg-[#E3F5F7] border-[#0E7C86] text-[#0A5960]',
  'bg-[#F7EAF3] border-[#A23B84] text-[#7A2B63]', 'bg-[#EEF3E1] border-[#5F7A1E] text-[#46591A]',
  'bg-[#F3ECE3] border-[#8A5A2B] text-[#664220]', 'bg-[#E8EAF6] border-[#3F51B5] text-[#2D3A82]',
];
export const colorNivel = (numero) => COLORES[((numero || 1) - 1) % COLORES.length];

export const minutos = (hhmm) => {
  const [h, m] = String(hhmm).split(':').map(Number);
  return h * 60 + (m || 0);
};
export const horaCorta = (hhmm) => String(hhmm || '').slice(0, 5);

/**
 * Calendario semanal (lunes a sábado). {@code sesiones}: [{ clave, dia, inicio, fin, titulo, detalle, color,
 * atenuada, onClick }]. Si se recibe {@code onSeleccionarHueco}, cada hora vacía es clicable para agregar una sesión.
 */
export default function CalendarioSemanal({ sesiones = [], onSeleccionarHueco, horaMinima = 7, horaMaxima = 21 }) {
  const inicios = sesiones.map((s) => Math.floor(minutos(s.inicio) / 60));
  const fines = sesiones.map((s) => Math.ceil(minutos(s.fin) / 60));
  const desde = Math.min(horaMinima, ...inicios);
  const hasta = Math.max(horaMaxima, ...fines);
  const horas = Array.from({ length: hasta - desde }, (_, i) => desde + i);

  return (
    <div className="overflow-x-auto rounded-lg border border-linea">
      <div className="grid min-w-[640px] grid-cols-[52px_repeat(6,minmax(0,1fr))]">
        <div className="sticky top-0 z-10 border-b border-linea bg-white" />
        {DIAS_CORTOS.map((d) => (
          <div key={d} className="border-b border-l border-linea bg-white py-2 text-center text-xs font-semibold text-tenue">{d}</div>
        ))}

        <div className="relative" style={{ height: horas.length * ALTO_HORA }}>
          {horas.map((h, i) => (
            <div key={h} className="absolute right-1.5 -translate-y-1/2 text-[10.5px] text-tenue" style={{ top: i * ALTO_HORA }}>
              {i === 0 ? '' : `${String(h).padStart(2, '0')}:00`}
            </div>
          ))}
        </div>

        {DIAS.map((_, indice) => {
          const dia = indice + 1;
          return (
            <div key={dia} className="relative border-l border-linea" style={{ height: horas.length * ALTO_HORA }}>
              {horas.map((h, i) => (
                onSeleccionarHueco ? (
                  <button key={h} type="button" onClick={() => onSeleccionarHueco(dia, h)}
                    title={`Agregar sesión el ${DIAS[indice].toLowerCase()} a las ${String(h).padStart(2, '0')}:00`}
                    className="absolute inset-x-0 border-t border-dashed border-linea transition hover:bg-marino/5"
                    style={{ top: i * ALTO_HORA, height: ALTO_HORA }} />
                ) : (
                  <div key={h} className="absolute inset-x-0 border-t border-dashed border-linea" style={{ top: i * ALTO_HORA }} />
                )
              ))}
              {sesiones.filter((s) => Number(s.dia) === dia).map((s) => {
                const top = ((minutos(s.inicio) - desde * 60) / 60) * ALTO_HORA;
                const alto = Math.max(((minutos(s.fin) - minutos(s.inicio)) / 60) * ALTO_HORA - 2, 18);
                const Contenedor = s.onClick ? 'button' : 'div';
                return (
                  <Contenedor key={s.clave} type={s.onClick ? 'button' : undefined} onClick={s.onClick}
                    title={`${s.titulo} · ${horaCorta(s.inicio)}–${horaCorta(s.fin)}${s.detalle ? ` · ${s.detalle}` : ''}`}
                    className={`absolute inset-x-1 z-[1] overflow-hidden rounded-md border-l-[3px] px-1.5 py-1 text-left text-[11px] leading-tight shadow-sm ${s.color || colorNivel(1)} ${s.atenuada ? 'opacity-45' : ''} ${s.onClick ? 'cursor-pointer hover:brightness-95' : ''}`}
                    style={{ top: top + 1, height: alto }}>
                    <div className="truncate font-semibold">{s.titulo}</div>
                    <div className="truncate opacity-80">{horaCorta(s.inicio)}–{horaCorta(s.fin)}</div>
                    {s.detalle && alto > 50 && <div className="truncate opacity-80">{s.detalle}</div>}
                  </Contenedor>
                );
              })}
            </div>
          );
        })}
      </div>
    </div>
  );
}
