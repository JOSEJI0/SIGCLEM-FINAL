import { useEffect, useState } from 'react';
import { ChevronDown, Download, Eye, FileText, Upload } from 'lucide-react';
import { mensajeError } from '../api/cliente';
import {
  descargarPlantillaConstancia, estadoPlantillasConstancia, guardarPlantillaConstancia, marcadoresPlantillaConstancia,
} from '../api/constancias';
import { Alerta, Boton, Tarjeta } from './ui';

const PLANTILLAS = [
  { tipo: 'ingles', titulo: 'Acreditación por lengua extranjera (inglés)' },
  { tipo: 'lengua-materna', titulo: 'Acreditación por lengua materna' },
];

function guardarArchivo(blob, nombre) {
  const url = URL.createObjectURL(blob);
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = nombre;
  enlace.click();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

/**
 * Plantillas Word de las constancias de acreditación. La coordinación sube el .docx tal como lo pidieron (texto,
 * membretes, imágenes) y escribe marcadores como {{NOMBRE}} donde van los datos; al emitir, el sistema copia el
 * documento y sustituye los marcadores con los datos del alumno, el folio y la fecha.
 */
export default function PlantillasConstancia({ soloLectura = false }) {
  const [estado, setEstado] = useState(null);
  const [marcadores, setMarcadores] = useState([]);
  const [verMarcadores, setVerMarcadores] = useState(false);
  const [ocupado, setOcupado] = useState('');
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');

  useEffect(() => {
    Promise.all([estadoPlantillasConstancia(), marcadoresPlantillaConstancia()])
      .then(([e, m]) => { setEstado(e); setMarcadores(m); })
      .catch((e) => setError(mensajeError(e)));
  }, []);

  async function subir(plantilla, archivo) {
    if (!archivo) return;
    setOcupado(`subir-${plantilla.tipo}`);
    setError('');
    setAviso('');
    try {
      const datos = new FormData();
      datos.append('archivo', archivo);
      setEstado(await guardarPlantillaConstancia(plantilla.tipo, datos));
      setAviso(`Plantilla «${plantilla.titulo}» guardada. Las próximas constancias se generarán con ella.`);
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setOcupado('');
    }
  }

  async function descargar(plantilla, cual) {
    setOcupado(`${cual}-${plantilla.tipo}`);
    setError('');
    try {
      const nombres = { archivo: 'plantilla', ejemplo: 'plantilla-ejemplo', 'vista-previa': 'vista-previa' };
      guardarArchivo(await descargarPlantillaConstancia(plantilla.tipo, cual), `${nombres[cual]}-${plantilla.tipo}.docx`);
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setOcupado('');
    }
  }

  return (
    <Tarjeta titulo="Plantillas Word de las constancias de acreditación" icono={FileText}>
      <p className="text-[13px] text-tenue">
        Sube el documento Word tal como lo pidieron (texto, encabezado, pie e imágenes). Donde vaya un dato del alumno
        escribe un marcador entre llaves dobles, por ejemplo <code>{'{{NOMBRE}}'}</code>, <code>{'{{NUMERO_CONTROL}}'}</code>,{' '}
        <code>{'{{CARRERA}}'}</code>, <code>{'{{FOLIO}}'}</code> o <code>{'{{FECHA}}'}</code>. Al emitir, el sistema copia el
        documento completo y cambia cada marcador por el dato correspondiente, así que puedes modificar la redacción
        cuando lo necesites sin cambiar el sistema.
      </p>
      {error && <Alerta tipo="error" className="mt-3">{error}</Alerta>}
      {aviso && <Alerta tipo="ok" className="mt-3">{aviso}</Alerta>}

      <div className="mt-3 grid gap-3 md:grid-cols-2">
        {PLANTILLAS.map((plantilla) => {
          const e = estado?.[plantilla.tipo];
          return (
            <div key={plantilla.tipo} className="grid content-start gap-2 rounded-lg border border-linea p-3">
              <p className="text-sm font-semibold">{plantilla.titulo}</p>
              <p className="text-xs text-tenue">
                {!e ? 'Consultando…'
                  : e.cargada ? `Plantilla cargada por la coordinación · ${e.modo === 'Marcadores'
                    ? `${e.marcadores.length} marcadores` : 'formato anterior sin marcadores'}`
                    : 'Sin plantilla propia: se usa la plantilla de ejemplo del sistema.'}
              </p>
              {e && e.cargada && e.modo !== 'Marcadores' && (
                <Alerta tipo="aviso">
                  Esta plantilla no tiene marcadores y depende de frases exactas: si cambia la redacción dejará de
                  funcionar. Descarga la de ejemplo, ajústala y vuelve a subirla.
                </Alerta>
              )}
              <div className="flex flex-wrap gap-2">
                {!soloLectura && (
                  <label className={`inline-flex cursor-pointer items-center gap-2 rounded-lg bg-marino px-3 py-2 text-[13px] font-semibold text-white hover:opacity-90 ${ocupado ? 'pointer-events-none opacity-60' : ''}`}>
                    <Upload className="h-4 w-4" />
                    {ocupado === `subir-${plantilla.tipo}` ? 'Revisando…' : e?.cargada ? 'Reemplazar Word' : 'Subir Word'}
                    <input type="file" className="sr-only" disabled={ocupado !== ''}
                      accept=".docx,application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                      onChange={(ev) => { subir(plantilla, ev.target.files?.[0]); ev.target.value = ''; }} />
                  </label>
                )}
                <Boton variante="secundario" type="button" disabled={ocupado !== ''}
                  onClick={() => descargar(plantilla, 'vista-previa')}>
                  <Eye className="h-4 w-4" /> Vista previa
                </Boton>
                {!soloLectura && (
                  <>
                    <Boton variante="secundario" type="button" disabled={ocupado !== ''}
                      onClick={() => descargar(plantilla, 'archivo')}>
                      <Download className="h-4 w-4" /> Descargar vigente
                    </Boton>
                    <Boton variante="secundario" type="button" disabled={ocupado !== ''}
                      onClick={() => descargar(plantilla, 'ejemplo')}>
                      <Download className="h-4 w-4" /> Plantilla de ejemplo
                    </Boton>
                  </>
                )}
              </div>
            </div>
          );
        })}
      </div>

      <button type="button" className="mt-3 inline-flex items-center gap-1 text-[13px] font-semibold text-marino"
        aria-expanded={verMarcadores} onClick={() => setVerMarcadores(!verMarcadores)}>
        <ChevronDown className={`h-4 w-4 transition ${verMarcadores ? 'rotate-180' : ''}`} />
        {verMarcadores ? 'Ocultar marcadores disponibles' : `Ver los ${marcadores.length} marcadores disponibles`}
      </button>
      {verMarcadores && (
        <div className="mt-2 overflow-x-auto">
          <table className="w-full min-w-[520px] text-left text-[13px]">
            <thead className="border-b border-linea text-xs text-tenue">
              <tr><th className="px-2 py-2">Marcador</th><th className="px-2 py-2">Qué escribe el sistema</th></tr>
            </thead>
            <tbody>
              {marcadores.map((m) => (
                <tr key={m.clave} className="border-b border-linea last:border-0">
                  <td className="whitespace-nowrap px-2 py-2 font-mono text-xs">{`{{${m.clave}}}`}</td>
                  <td className="px-2 py-2">{m.descripcion}{m.soloIngles && <span className="text-tenue"> Solo inglés.</span>}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="mt-2 text-xs text-tenue">
            Obligatorios: {'{{NOMBRE}}'}, {'{{NUMERO_CONTROL}}'}, {'{{FOLIO}}'} y una fecha ({'{{FECHA}}'} o {'{{FECHA_LETRA}}'}).
            Al subir el Word se genera una constancia de prueba: si un marcador está mal escrito, el sistema lo indica y
            conserva la plantilla anterior.
          </p>
        </div>
      )}
    </Tarjeta>
  );
}
