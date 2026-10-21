import { describe, expect, it } from 'vitest';
import { tonoEstatus, clasesTono } from './estatus';
import { rutaInicio, esSoloConsulta } from '../auth/roles';

describe('estatus', () => {
  it('asigna el tono de la guía de estilo', () => {
    expect(tonoEstatus('Acreditado')).toBe('ok');
    expect(tonoEstatus('No permitido')).toBe('neutro');
    expect(tonoEstatus('Rechazado')).toBe('error');
    expect(clasesTono('desconocido')).toContain('neutro');
  });
});

describe('roles', () => {
  it('dirige a cada rol a su panel', () => {
    expect(rutaInicio('COORDINADOR')).toBe('/coordinacion');
    expect(rutaInicio('JEFA_DGTV')).toBe('/supervision');
    expect(rutaInicio(undefined)).toBe('/login');
  });
  it('marca a la jefa del DGTV como solo consulta', () => {
    expect(esSoloConsulta('JEFA_DGTV')).toBe(true);
    expect(esSoloConsulta('COORDINADOR')).toBe(false);
  });
});
