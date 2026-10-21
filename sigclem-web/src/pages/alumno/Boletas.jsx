import { useEffect, useState } from 'react';
import { FileText, Printer } from 'lucide-react';
import { obtenerMisBoletas } from '../../api/boletas';
import { mensajeError } from '../../api/cliente';
import { Alerta, Boton, Cargando, Insignia, Tarjeta } from '../../components/ui';

function fecha(iso) {
  return iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('es-MX') : '—';
}

function tono(resultado) {
  if (resultado === 'Acreditado') return 'ok';
  if (resultado === 'No acreditado') return 'error';
  return 'aviso';
}

export default function Boletas() {
  const [boletas, setBoletas] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    obtenerMisBoletas()
      .then(setBoletas)
      .catch((e) => setError(mensajeError(e)))
      .finally(() => setCargando(false));
  }, []);

  if (cargando) return <Cargando />;

  return (
    <div className="grid gap-[18px]">
      <div className="flex flex-wrap items-center justify-between gap-3 print:hidden">
        <div>
          <h1 className="flex items-center gap-2 text-xl font-bold"><FileText className="h-5 w-5" /> Mis boletas</h1>
          <p className="mt-1 text-sm text-tenue">Consulta tus calificaciones por periodo académico.</p>
        </div>
        {boletas.length > 0 && (
          <Boton variante="secundario" type="button" onClick={() => window.print()}>
            <Printer className="h-4 w-4" /> Imprimir
          </Boton>
        )}
      </div>

      {error && <Alerta tipo="error">{error}</Alerta>}
      {!error && boletas.length === 0 ? (
        <Tarjeta>
          <div className="py-8 text-center">
            <FileText className="mx-auto mb-2 h-10 w-10 text-tenue opacity-60" />
            <p className="font-semibold">Aún no tienes calificaciones registradas.</p>
            <p className="mt-1 text-xs text-tenue">Las boletas aparecerán cuando se registre tu inscripción a un grupo.</p>
          </div>
        </Tarjeta>
      ) : boletas.length > 0 ? (
        <Tarjeta titulo="Historial académico" icono={FileText} extra={`${boletas.length} cursos`}>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-left">
              <thead><tr className="border-b border-linea text-xs text-tenue">
                <th className="py-2 pr-3">Periodo</th><th className="py-2 pr-3">Nivel</th>
                <th className="py-2 pr-3">Grupo</th><th className="py-2 pr-3">Oportunidad</th>
                <th className="py-2 pr-3">Calificación</th><th className="py-2 pr-3">Resultado</th>
                <th className="py-2">Cierre de acta</th>
              </tr></thead>
              <tbody>{boletas.map((boleta) => (
                <tr className="border-b border-linea last:border-0" key={boleta.idInscripcion}>
                  <td className="py-3 pr-3">
                    <div className="font-medium">{boleta.periodo}</div>
                    <div className="text-[11px] text-tenue">{fecha(boleta.fechaInicio)} – {fecha(boleta.fechaFin)}</div>
                  </td>
                  <td className="py-3 pr-3">{boleta.nivel}. {boleta.nombreNivel}</td>
                  <td className="py-3 pr-3">{boleta.grupo}</td>
                  <td className="py-3 pr-3">{boleta.oportunidad === 'R1' ? 'Repetición' : 'Ordinaria'}</td>
                  <td className="py-3 pr-3 font-semibold">{boleta.calificacion ?? '—'}</td>
                  <td className="py-3 pr-3"><Insignia tono={tono(boleta.resultado)}>{boleta.resultado}</Insignia></td>
                  <td className="py-3">{fecha(boleta.fechaCierreActa)}</td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        </Tarjeta>
      ) : null}
    </div>
  );
}
