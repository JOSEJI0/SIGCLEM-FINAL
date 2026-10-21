import { ShieldCheck } from 'lucide-react';
import { Insignia } from './ui';

const TONO_SAT = {
  Vigente: 'ok',
  Cancelado: 'error',
  'No encontrado': 'error',
  'No disponible': 'aviso',
  'Sin verificar': 'neutro',
};

const TONO_COTEJO = {
  Coincide: 'ok',
  Parcial: 'aviso',
  'No coincide': 'error',
  'Sin texto': 'neutro',
};

/** Verificación automática del comprobante: QR leído en el servidor, estado ante el SAT y cotejo de la descripción. */
export function tieneAlertaVerificacion(comprobante) {
  // «Parcial»: el recibo es del alumno pero de otro concepto (p. ej. reinscripción), o no trae su número de control.
  return ['Cancelado', 'No encontrado'].includes(comprobante.estadoSat)
    || ['No coincide', 'Parcial'].includes(comprobante.cotejoDescripcion);
}

export default function VerificacionComprobante({ comprobante, detallado = false }) {
  if (!comprobante.estadoSat && !comprobante.cotejoDescripcion) return null;
  return (
    <div className="grid gap-1.5 text-xs">
      <div className="flex flex-wrap items-center gap-1.5">
        <ShieldCheck className="h-3.5 w-3.5 text-tenue" aria-hidden />
        {comprobante.origenDatos && <Insignia tono="info">Datos: {comprobante.origenDatos}</Insignia>}
        {comprobante.estadoSat && (
          <Insignia tono={TONO_SAT[comprobante.estadoSat] || 'neutro'}>SAT: {comprobante.estadoSat}</Insignia>
        )}
        {comprobante.cotejoDescripcion && (
          <Insignia tono={TONO_COTEJO[comprobante.cotejoDescripcion] || 'neutro'}>
            Descripción: {comprobante.cotejoDescripcion}
          </Insignia>
        )}
      </div>
      {detallado && (
        <dl className="grid gap-1 text-tenue">
          {comprobante.detalleSat && (
            <div><dt className="inline font-semibold">SAT: </dt><dd className="inline">{comprobante.detalleSat}
              {comprobante.fechaConsultaSat && ` (consultado ${new Date(comprobante.fechaConsultaSat).toLocaleString('es-MX')})`}</dd></div>
          )}
          {comprobante.detalleCotejo && (
            <div><dt className="inline font-semibold">Cotejo: </dt><dd className="inline">{comprobante.detalleCotejo}</dd></div>
          )}
          {comprobante.descripcionLeida && (
            <div><dt className="inline font-semibold">Descripción leída: </dt><dd className="inline">«{comprobante.descripcionLeida}»</dd></div>
          )}
        </dl>
      )}
    </div>
  );
}
