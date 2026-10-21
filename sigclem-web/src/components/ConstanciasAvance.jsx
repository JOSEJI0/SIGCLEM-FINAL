import { useEffect, useState } from 'react';
import { Eye, GraduationCap, Search } from 'lucide-react';
import { mensajeError } from '../api/cliente';
import {
  descargarConstanciaAvance, listarConstanciasAvanceDeAlumno, listarMisConstanciasAvance,
} from '../api/constancias';
import { Alerta, Boton, Campo, Tarjeta, claseEntrada } from './ui';
import { useVisorDocumento } from './VisorDocumento';


/**
 * Constancia de avance por nivel de inglés: un PDF por cada nivel acreditado con acta cerrada.
 * El alumno ve sus niveles; coordinación y jefatura buscan por número de control.
 */
export default function ConstanciasAvance({ propias = false }) {
  const verDocumento = useVisorDocumento();
  const [niveles, setNiveles] = useState(null);
  const [numeroControl, setNumeroControl] = useState('');
  const [buscando, setBuscando] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!propias) return;
    listarMisConstanciasAvance()
      .then(setNiveles)
      .catch((err) => setError(mensajeError(err)));
  }, [propias]);

  const buscar = async (evento) => {
    evento.preventDefault();
    if (!numeroControl.trim()) return;
    setBuscando(true);
    setError('');
    try {
      setNiveles(await listarConstanciasAvanceDeAlumno(numeroControl.trim()));
    } catch (err) {
      setNiveles(null);
      setError(mensajeError(err));
    } finally {
      setBuscando(false);
    }
  };

  const ver = (nivel) => verDocumento({
    titulo: `Constancia de avance · ${nivel.nivel} · ${nivel.nombreAlumno}`,
    nombre: `constancia-avance-${nivel.numeroControl}-nivel-${nivel.numeroNivel}`,
    cargar: () => descargarConstanciaAvance(nivel.idInscripcion),
  });

  return (
    <Tarjeta titulo="Constancias de avance por nivel" icono={GraduationCap}>
      <p className="mb-3 text-sm text-tenue">
        Formato del Foreign Languages Department con nivel, periodo, facilitador y calificación de cada nivel de inglés
        acreditado. Se imprime para las firmas y el sello de la coordinación.
      </p>
      {!propias && (
        <form className="mb-3 flex flex-wrap items-end gap-2" onSubmit={buscar}>
          <Campo etiqueta="Número de control" id="avance-numero-control">
            <input className={claseEntrada} id="avance-numero-control" maxLength="10" value={numeroControl}
              onChange={(e) => setNumeroControl(e.target.value.toUpperCase())} />
          </Campo>
          <Boton type="submit" variante="secundario" disabled={buscando || !numeroControl.trim()}>
            <Search className="h-4 w-4" /> {buscando ? 'Buscando…' : 'Buscar niveles acreditados'}
          </Boton>
        </form>
      )}
      {error && <Alerta tipo="error" className="mb-3">{error}</Alerta>}
      {niveles && niveles.length === 0 && (
        <p className="text-sm text-tenue">
          {propias ? 'Aún no tienes niveles acreditados con acta cerrada.' : 'El alumno no tiene niveles acreditados con acta cerrada.'}
        </p>
      )}
      {niveles && niveles.length > 0 && (
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="text-xs uppercase text-tenue">
              <tr>
                <th className="px-2 py-2">Nivel</th><th className="px-2 py-2">Periodo</th>
                <th className="px-2 py-2">Facilitador</th><th className="px-2 py-2">Calificación</th><th className="px-2 py-2" />
              </tr>
            </thead>
            <tbody>
              {niveles.map((nivel) => (
                <tr key={nivel.idInscripcion} className="border-t border-linea">
                  <td className="px-2 py-2.5 font-medium">{nivel.nivel}</td>
                  <td className="px-2 py-2.5">{nivel.periodo}</td>
                  <td className="px-2 py-2.5">{nivel.facilitador}</td>
                  <td className="px-2 py-2.5">{nivel.calificacion}</td>
                  <td className="px-2 py-2.5 text-right">
                    <Boton variante="secundario" type="button" onClick={() => ver(nivel)}>
                      <Eye className="h-4 w-4" /> Ver PDF
                    </Boton>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Tarjeta>
  );
}
