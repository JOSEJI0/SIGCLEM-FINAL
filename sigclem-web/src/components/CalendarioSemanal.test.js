import { describe, expect, it } from 'vitest';
import { distribuirCarriles } from './CalendarioSemanal';

const sesion = (clave, inicio, fin) => ({ clave, dia: 6, inicio, fin });

describe('distribuirCarriles', () => {
  it('muestra en carriles separados cursos simultáneos del mismo día', () => {
    const resultado = distribuirCarriles([
      sesion('nivel-1', '09:00', '14:00'),
      sesion('nivel-2', '09:00', '14:00'),
      sesion('nivel-3', '09:00', '14:00'),
      sesion('nivel-4', '09:00', '14:00'),
    ]);

    expect(resultado.map(({ carril }) => carril)).toEqual([0, 1, 2, 3]);
    expect(resultado.map(({ totalCarriles }) => totalCarriles)).toEqual([4, 4, 4, 4]);
  });

  it('reutiliza el carril cuando una sesión termina antes de iniciar otra', () => {
    const resultado = distribuirCarriles([
      sesion('primera', '09:00', '11:00'),
      sesion('segunda', '10:00', '12:00'),
      sesion('tercera', '11:00', '13:00'),
    ]);

    expect(resultado.map(({ carril }) => carril)).toEqual([0, 1, 0]);
    expect(resultado.map(({ totalCarriles }) => totalCarriles)).toEqual([2, 2, 2]);
  });

  it('separa los bloques que no se traslapan', () => {
    const resultado = distribuirCarriles([
      sesion('mañana', '09:00', '10:00'),
      sesion('tarde', '10:00', '11:00'),
    ]);

    expect(resultado.map(({ carril, totalCarriles }) => [carril, totalCarriles])).toEqual([[0, 1], [0, 1]]);
  });
});
