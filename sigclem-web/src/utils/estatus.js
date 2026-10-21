/** Colores de estado de la guía de estilo (Tabla 64). */
const CLASES = {
  ok: 'bg-ok-fondo text-ok',
  aviso: 'bg-aviso-fondo text-aviso',
  error: 'bg-error-fondo text-error',
  info: 'bg-info-fondo text-info',
  neutro: 'bg-neutro-fondo text-neutro',
};

const TONO_POR_ESTATUS = {
  Acreditado: 'ok', Validado: 'ok', Emitida: 'ok', Concluida: 'ok', Concluido: 'ok',
  Cursando: 'aviso', Pendiente: 'aviso', Solicitada: 'aviso', Habilitado: 'aviso', Programada: 'info', Programado: 'info',
  Liquidada: 'ok', Asignada: 'ok', Parcial: 'aviso',
  Rechazado: 'error', Rechazada: 'error', Reprobado: 'error',
  Exento: 'info', Autorizada: 'info',
  'No permitido': 'neutro',
  'Nivel pendiente': 'aviso', 'Nivel pendiente de pago': 'aviso',
};

export function tonoEstatus(estatus) {
  return TONO_POR_ESTATUS[estatus] || 'neutro';
}

export function clasesTono(tono) {
  return CLASES[tono] || CLASES.neutro;
}
