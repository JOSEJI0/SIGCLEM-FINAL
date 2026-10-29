import { FileText } from 'lucide-react';

export const DOCUMENTOS_LENGUA = [
  ['curp', 'CURP', 'tieneCurp'],
  ['domicilio', 'Comprobante de domicilio', 'tieneDomicilio'],
  ['ine', 'INE', 'tieneIne'],
  ['intercultural', 'Univ. Intercultural', 'tieneIntercultural'],
  ['constancia', 'Constancia de acreditación', 'tieneConstancia'],
];

export default function DocumentosLengua({ registro, onVer }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {DOCUMENTOS_LENGUA.map(([tipo, etiqueta, bandera]) => (registro[bandera] ? (
        <button key={tipo} type="button" onClick={() => onVer(registro, tipo, etiqueta)}
          className="inline-flex items-center gap-1 rounded-full bg-info-fondo px-2.5 py-1 text-[11.5px] font-semibold text-[#1C4A7E] hover:brightness-95">
          <FileText className="h-3 w-3" /> {etiqueta}
        </button>
      ) : (
        <span key={tipo} className="rounded-full bg-neutro-fondo px-2.5 py-1 text-[11.5px] text-neutro">{etiqueta}: pendiente</span>
      )))}
    </div>
  );
}