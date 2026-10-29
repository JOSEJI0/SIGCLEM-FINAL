import { useCallback, useEffect, useState } from 'react';
import { FileCheck2, Languages, Upload } from 'lucide-react';
import {
  descargarDocumentoLenguaMaterna,
  listarLenguasMaternas,
  listarMisLenguasMaternas,
  registrarLenguaMaterna,
} from '../../api/lenguasMaternas';
import { mensajeError } from '../../api/cliente';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../../components/ui';
import { useVisorDocumento } from '../../components/VisorDocumento';
import DocumentosLengua from '../../components/DocumentosLengua';


export default function LenguaMaterna() {
  const verDocumento = useVisorDocumento();
  const [catalogo, setCatalogo] = useState([]);
  const [registros, setRegistros] = useState([]);
  const [idLengua, setIdLengua] = useState('');
  const [nivelDominio, setNivelDominio] = useState('');
  const [documento, setDocumento] = useState(null);
  const [cargando, setCargando] = useState(true);
  const [guardando, setGuardando] = useState(false);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [archivos, setArchivos] = useState({ curp: null, domicilio: null, ine: null });
  const listo = idLengua && archivos.curp && archivos.domicilio && archivos.ine;


  const cargar = useCallback(async () => {
    setCargando(true);
    try {
      const [lenguas, propios] = await Promise.all([listarLenguasMaternas(), listarMisLenguasMaternas()]);
      setCatalogo(lenguas);
      setRegistros(propios);
      setError('');
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => { cargar(); }, [cargar]);

  const disponibles = catalogo.filter((lengua) => {
    const registro = registros.find((item) => item.idLengua === lengua.id);
    return !registro || registro.estatus === 'Rechazado';
  });

  async function enviar(evento) {
    evento.preventDefault();
    if (!documento) {
      setError('Selecciona el documento que respalda tu registro.');
      return;
    }
    setGuardando(true);
    setError('');
    setAviso('');
    try {
    await registrarLenguaMaterna({ idLengua: Number(idLengua), nivelDominio: nivelDominio.trim() || null }, archivos);
    setArchivos({ curp: null, domicilio: null, ine: null }
    );
    const ver = (registro, tipo, etiqueta) => verDocumento({
    titulo: `${etiqueta} · ${registro.lengua}`,
    nombre: `lengua-${registro.id}-${tipo}`,
    cargar: () => descargarDocumentoLenguaMaterna(registro.id, tipo),
    });
      setAviso('Registro enviado. Coordinación revisará el documento.');
      setIdLengua('');
      setNivelDominio('');
      setDocumento(null);
      evento.currentTarget.reset();
      await cargar();
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando(false);
    }
  }

  function descargar(registro) {
    verDocumento({
      titulo: 'Documento de lengua materna',
      nombre: `documento-lengua-${registro.id}`,
      cargar: () => descargarDocumentoLenguaMaterna(registro.id),
    });
  }

  if (cargando) return <Cargando />;

  return (
    <div className="grid gap-[18px]">
      <div>
        <h1 className="flex items-center gap-2 text-xl font-bold"><Languages className="h-5 w-5" /> Lengua materna</h1>
        <p className="mt-1 text-sm text-tenue">Registra tu lengua materna y consulta el resultado de su revisión.</p>
      </div>
      {error && <Alerta tipo="error">{error}</Alerta>}
      {aviso && <Alerta tipo="ok">{aviso}</Alerta>}

      <Tarjeta titulo="Registrar lengua materna" icono={Upload}>
        {disponibles.length === 0 ? (
          <p className="text-sm text-tenue">No hay lenguas disponibles para registrar.</p>
        ) : (
          <form className="grid gap-3 md:grid-cols-2" onSubmit={enviar}>
            <Campo etiqueta="Lengua" id="lenguaMaterna">
              <select className={claseEntrada} id="lenguaMaterna" required value={idLengua}
                onChange={(evento) => setIdLengua(evento.target.value)}>
                <option value="">Selecciona una lengua</option>
                {disponibles.map((lengua) => <option key={lengua.id} value={lengua.id}>{lengua.nombre}</option>)}
              </select>
            </Campo>
            <Campo etiqueta="Nivel de dominio (opcional)" id="nivelDominio">
              <input className={claseEntrada} id="nivelDominio" maxLength={20} value={nivelDominio}
                onChange={(evento) => setNivelDominio(evento.target.value)} />
            </Campo>
            {[['curp', 'CURP'], ['domicilio', 'Comprobante de domicilio'], ['ine', 'INE']].map(([clave, etiqueta]) => (
              <Campo key={clave} etiqueta={`${etiqueta} (PDF, PNG o JPEG; máx. 5 MB)`} id={`doc-${clave}`}>
                <input className={claseEntrada} id={`doc-${clave}`} type="file" required
                accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
                onChange={(e) => setArchivos((a) => ({ ...a, [clave]: e.target.files?.[0] || null }))} />
            </Campo>
            ))}
            <div className="flex items-end">
              <Boton type="submit" disabled={guardando || !idLengua}>
                {guardando ? 'Enviando…' : 'Enviar registro'}
              </Boton>
            </div>
          </form>
        )}
      </Tarjeta>

      <Tarjeta titulo="Mis registros" icono={FileCheck2}>
        {registros.length === 0 ? (
          <p className="text-sm text-tenue">Aún no tienes registros de lengua materna.</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[600px] text-left">
              <thead><tr className="border-b border-linea text-xs text-tenue">
                <th className="py-2 pr-4">Lengua</th><th className="py-2 pr-4">Nivel</th>
                <th className="py-2 pr-4">Fecha</th><th className="py-2 pr-4">Estatus</th><th className="py-2">Documento</th>
              </tr></thead>
              <tbody>{registros.map((registro) => (
                <tr className="border-b border-linea last:border-0" key={registro.id}>
                  <td className="py-3 pr-4 font-medium">{registro.lengua}</td>
                  <td className="py-3 pr-4">{registro.nivelDominio || '—'}</td>
                  <td className="py-3 pr-4">{registro.fechaRegistro}</td>
                  <td className="py-3 pr-4"><Insignia estatus={registro.estatus} /></td>
                  <td className="py-3">
                    <Boton variante="secundario" type="button" onClick={() => descargar(registro)}>Ver documento</Boton>
                  </td>
                </tr>
              ))}</tbody>
            </table>
          </div>
        )}
      </Tarjeta>
    </div>
  );
}
