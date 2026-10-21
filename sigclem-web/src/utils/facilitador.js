import { DIAS, horaCorta } from '../components/CalendarioSemanal';

export function fechaLocal(fecha = new Date()) {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;
}

/** Periodo vigente hoy; si no hay, el más reciente. */
export function periodoVigente(periodos) {
  const hoy = fechaLocal();
  return periodos.find((p) => p.fechaInicio <= hoy && p.fechaFin >= hoy)
    || [...periodos].sort((a, b) => (a.fechaInicio < b.fechaInicio ? 1 : -1))[0];
}

export const textoHorario = (horarios = []) =>
  horarios.map((h) => `${DIAS[h.diaSemana - 1]} ${horaCorta(h.horaInicio)}–${horaCorta(h.horaFin)}`).join(' · ');

/** Días que faltan para una fecha AAAA-MM-DD (negativo si ya pasó). */
export function diasHasta(fechaIso) {
  const [a, m, d] = fechaIso.split('-').map(Number);
  const hoy = new Date();
  const objetivo = new Date(a, m - 1, d);
  return Math.round((objetivo - new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate())) / 86400000);
}

