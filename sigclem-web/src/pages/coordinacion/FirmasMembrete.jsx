import { useCallback, useEffect, useState } from 'react';
import { Eye, FileText, ImagePlus, PenLine, Save, Trash2 } from 'lucide-react';
import { mensajeError } from '../../api/cliente';
import {
  actualizarConfiguracionConstancias, eliminarImagenConstancia, guardarImagenConstancia, obtenerConfiguracionConstancias,
  obtenerImagenConstancia, vistaPreviaConstancia,
} from '../../api/constancias';
import { Alerta, Boton, Campo, Cargando, Tarjeta, claseEntrada } from '../../components/ui';
import { useVisorDocumento } from '../../components/VisorDocumento';

const FIRMANTES = [
  { clave: 'subdirector', titulo: 'Subdirección de Planeación y Vinculación', cargos: ['Subdirectora', 'Subdirector'] },
  { clave: 'jefeDgtv', titulo: 'Departamento de Gestión Tecnológica y Vinculación', cargos: ['Jefa', 'Jefe'] },
  { clave: 'coordinador', titulo: 'Coordinación de Lenguas Extranjeras y Maternas', cargos: ['Coordinadora', 'Coordinador'] },
];

const IMAGENES = [
  { tipo: 'ENCABEZADO', ruta: 'encabezado', titulo: 'Encabezado del oficio', ayuda: 'Franja superior: logos de Educación y TecNM y el emblema del año. Recomendado: imagen horizontal (≈ 7:1).' },
  { tipo: 'PIE', ruta: 'pie', titulo: 'Pie de página del oficio', ayuda: 'Franja inferior: imagen del año, dirección y logos de certificaciones. Recomendado: imagen horizontal (≈ 5:1).' },
  { tipo: 'LOGO_COORDINACION', ruta: 'logo-coordinacion', titulo: 'Logo de la coordinación', ayuda: 'Aparece en la constancia de avance por nivel.' },
];

function fechaHora(iso) {
  return iso ? new Date(iso).toLocaleString('es-MX') : null;
}


/** Firmantes, textos del oficio y membrete del año que usan las constancias (solo coordinación). */
export default function FirmasMembrete() {
  const verDocumento = useVisorDocumento();
  const [config, setConfig] = useState(null);
  const [formulario, setFormulario] = useState(null);
  const [vistas, setVistas] = useState({});
  const [guardando, setGuardando] = useState('');
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');

  const cargarVistas = useCallback(async (datos) => {
    const nuevas = {};
    for (const imagen of IMAGENES) {
      if (datos.imagenes?.[imagen.tipo]) {
        try {
          nuevas[imagen.tipo] = URL.createObjectURL(await obtenerImagenConstancia(imagen.ruta));
        } catch {
          // Sin vista previa: la imagen sigue registrada.
        }
      }
    }
    setVistas((previas) => {
      Object.values(previas).forEach((url) => URL.revokeObjectURL(url));
      return nuevas;
    });
  }, []);

  const aplicar = useCallback((datos) => {
    setConfig(datos);
    setFormulario({
      subdirectorNombre: datos.subdirectorNombre, subdirectorGenero: datos.subdirectorGenero,
      jefeDgtvNombre: datos.jefeDgtvNombre, jefeDgtvGenero: datos.jefeDgtvGenero,
      coordinadorNombre: datos.coordinadorNombre, coordinadorGenero: datos.coordinadorGenero,
      lema: datos.lema, registroCoordinacion: datos.registroCoordinacion,
      prefijoOficio: datos.prefijoOficio, copias: datos.copias,
    });
    cargarVistas(datos);
  }, [cargarVistas]);

  useEffect(() => {
    obtenerConfiguracionConstancias().then(aplicar).catch((e) => setError(mensajeError(e)));
  }, [aplicar]);

  const cambiar = (campo, valor) => setFormulario((previo) => ({ ...previo, [campo]: valor }));

  async function guardar(evento) {
    evento.preventDefault();
    setGuardando('datos');
    setError('');
    setAviso('');
    try {
      aplicar(await actualizarConfiguracionConstancias(formulario));
      setAviso('Firmantes y textos actualizados. Las constancias que se emitan a partir de ahora los usarán.');
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando('');
    }
  }

  async function subirImagen(imagen, archivo) {
    if (!archivo) return;
    setGuardando(imagen.tipo);
    setError('');
    setAviso('');
    try {
      const datos = new FormData();
      datos.append('archivo', archivo);
      aplicar(await guardarImagenConstancia(imagen.ruta, datos));
      setAviso(`${imagen.titulo} actualizado.`);
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando('');
    }
  }

  async function quitarImagen(imagen) {
    if (!window.confirm(`¿Quitar la imagen «${imagen.titulo}»? Las constancias usarán la predeterminada.`)) return;
    setGuardando(imagen.tipo);
    setError('');
    try {
      aplicar(await eliminarImagenConstancia(imagen.ruta));
      setAviso(`${imagen.titulo}: se usará la imagen predeterminada.`);
    } catch (e) {
      setError(mensajeError(e));
    } finally {
      setGuardando('');
    }
  }

  function vistaPrevia(formato) {
    verDocumento({
      titulo: `Vista previa · constancia de ${formato === 'avance' ? 'avance por nivel' : 'acreditación'}`,
      nombre: `vista-previa-${formato}`,
      cargar: () => vistaPreviaConstancia(formato),
    });
  }

  if (!formulario) return error ? <Alerta tipo="error">{error}</Alerta> : <Cargando />;

  return (
    <div className="grid gap-5">
      <div>
        <h1 className="flex items-center gap-2 text-xl font-bold"><PenLine className="h-5 w-5" /> Firmas y membrete</h1>
        <p className="mt-1 text-sm text-tenue">
          Personas que firman, textos del oficio e imágenes del año que se imprimen en las constancias de acreditación y de
          avance por nivel. Los cambios aplican a las constancias que se emitan después; las ya emitidas conservan su PDF.
        </p>
        {config?.fechaActualizacion && (
          <p className="mt-1 text-xs text-tenue">
            Última actualización: {fechaHora(config.fechaActualizacion)}{config.actualizadoPor ? ` por ${config.actualizadoPor}` : ''}.
          </p>
        )}
      </div>
      {error && <Alerta tipo="error">{error}</Alerta>}
      {aviso && <Alerta tipo="ok">{aviso}</Alerta>}

      <div className="flex flex-wrap gap-2">
        <Boton variante="secundario" type="button" disabled={guardando !== ''} onClick={() => vistaPrevia('acreditacion')}>
          <Eye className="h-4 w-4" /> Vista previa: constancia de acreditación
        </Boton>
        <Boton variante="secundario" type="button" disabled={guardando !== ''} onClick={() => vistaPrevia('avance')}>
          <Eye className="h-4 w-4" /> Vista previa: constancia de avance
        </Boton>
      </div>

      <form className="grid gap-5" onSubmit={guardar}>
        <Tarjeta titulo="Firmantes" icono={PenLine}>
          <div className="grid gap-4">
            {FIRMANTES.map((firmante) => (
              <fieldset key={firmante.clave} className="grid gap-3 rounded-lg border border-linea p-3 sm:grid-cols-[1fr_200px]">
                <legend className="px-1 text-sm font-semibold">{firmante.titulo}</legend>
                <Campo etiqueta="Nombre completo" id={`${firmante.clave}-nombre`}>
                  <input className={claseEntrada} id={`${firmante.clave}-nombre`} required maxLength="100"
                    value={formulario[`${firmante.clave}Nombre`]}
                    onChange={(e) => cambiar(`${firmante.clave}Nombre`, e.target.value)} />
                </Campo>
                <Campo etiqueta="Cargo" id={`${firmante.clave}-genero`}>
                  <select className={claseEntrada} id={`${firmante.clave}-genero`} value={formulario[`${firmante.clave}Genero`]}
                    onChange={(e) => cambiar(`${firmante.clave}Genero`, e.target.value)}>
                    <option value="F">{firmante.cargos[0]}</option>
                    <option value="M">{firmante.cargos[1]}</option>
                  </select>
                </Campo>
              </fieldset>
            ))}
          </div>
        </Tarjeta>

        <Tarjeta titulo="Textos del oficio de acreditación" icono={FileText}>
          <div className="grid gap-3 sm:grid-cols-2">
            <Campo etiqueta="Prefijo del número de oficio" id="prefijo" ayuda="Se completa con el folio y el año: PREFIJO/FOLIO/AÑO.">
              <input className={claseEntrada} id="prefijo" required maxLength="60" value={formulario.prefijoOficio}
                onChange={(e) => cambiar('prefijoOficio', e.target.value)} />
            </Campo>
            <Campo etiqueta="Registro de la coordinación" id="registro">
              <input className={claseEntrada} id="registro" required maxLength="100" value={formulario.registroCoordinacion}
                onChange={(e) => cambiar('registroCoordinacion', e.target.value)} />
            </Campo>
            <Campo etiqueta="Lema (bajo «ATENTAMENTE»)" id="lema" ayuda="Un renglón por línea.">
              <textarea className={claseEntrada} id="lema" rows="2" required maxLength="200" value={formulario.lema}
                onChange={(e) => cambiar('lema', e.target.value)} />
            </Campo>
            <Campo etiqueta="Copias e iniciales (c.c.p.)" id="copias" ayuda="Un renglón por línea; actualiza las iniciales al cambiar de firmantes.">
              <textarea className={claseEntrada} id="copias" rows="2" required maxLength="200" value={formulario.copias}
                onChange={(e) => cambiar('copias', e.target.value)} />
            </Campo>
          </div>
        </Tarjeta>

        <div>
          <Boton type="submit" disabled={guardando !== ''}>
            <Save className="h-4 w-4" /> {guardando === 'datos' ? 'Guardando…' : 'Guardar firmantes y textos'}
          </Boton>
        </div>
      </form>

      <Tarjeta titulo="Membrete del año" icono={ImagePlus}>
        <p className="mb-3 text-sm text-tenue">
          Cada año cambian los logos institucionales: sube aquí las franjas del membrete vigente (PNG o JPG de hasta 2 MB).
          Sin imagen, el oficio usa el logo del TecNM y la constancia de avance el logo de la coordinación incluidos en el sistema.
        </p>
        <div className="grid gap-4">
          {IMAGENES.map((imagen) => (
            <div key={imagen.tipo} className="grid gap-2 rounded-lg border border-linea p-3">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <p className="text-sm font-semibold">{imagen.titulo}</p>
                  <p className="text-xs text-tenue">{imagen.ayuda}</p>
                </div>
                <span className="text-xs text-tenue">
                  {config.imagenes?.[imagen.tipo] ? `Cargada el ${fechaHora(config.imagenes[imagen.tipo])}` : 'Usando la predeterminada'}
                </span>
              </div>
              {vistas[imagen.tipo] && (
                <img src={vistas[imagen.tipo]} alt={imagen.titulo} className="max-h-28 w-auto max-w-full rounded border border-linea bg-white object-contain p-1" />
              )}
              <div className="flex flex-wrap items-center gap-2">
                <label className="inline-flex cursor-pointer items-center gap-2 rounded-lg border border-linea px-3 py-2 text-sm font-medium hover:bg-black/5">
                  <ImagePlus className="h-4 w-4" />
                  {guardando === imagen.tipo ? 'Subiendo…' : config.imagenes?.[imagen.tipo] ? 'Reemplazar imagen' : 'Subir imagen'}
                  <input type="file" className="sr-only" accept="image/png,image/jpeg,.png,.jpg,.jpeg" disabled={guardando !== ''}
                    onChange={(e) => { subirImagen(imagen, e.target.files?.[0]); e.target.value = ''; }} />
                </label>
                {config.imagenes?.[imagen.tipo] && (
                  <Boton variante="secundario" type="button" disabled={guardando !== ''} onClick={() => quitarImagen(imagen)}>
                    <Trash2 className="h-4 w-4" /> Quitar
                  </Boton>
                )}
              </div>
            </div>
          ))}
        </div>
      </Tarjeta>
    </div>
  );
}
