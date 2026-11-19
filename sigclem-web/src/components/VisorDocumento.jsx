import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import { Download, ExternalLink, FileText, Maximize2, X, ZoomIn, ZoomOut } from 'lucide-react';
import { mensajeErrorArchivo } from '../api/cliente';
import { Alerta, Boton, Cargando } from './ui';

const VisorContext = createContext(null);

const EXTENSIONES = { 'application/pdf': 'pdf', 'image/png': 'png', 'image/jpeg': 'jpg' };

/** Convierte la respuesta de {@code cargar()} en { url, tipo } (la URL se libera con {@code URL.revokeObjectURL}). */
async function obtenerDocumento(cargar) {
  const respuesta = await cargar();
  const blob = respuesta instanceof Blob ? respuesta : respuesta?.data;
  return { url: URL.createObjectURL(blob), tipo: blob.type || 'application/pdf' };
}

/**
 * Carga un documento (Blob) para mostrarlo con {@link AreaDocumento}. Devuelve { url, tipo, cargando, error }.
 * {@code cargar} se vuelve a llamar solo cuando cambia {@code clave}.
 */
export function useDocumento(clave, cargar) {
  const [estado, setEstado] = useState({ cargando: true });
  const cargarRef = useRef(cargar);
  cargarRef.current = cargar;

  useEffect(() => {
    let url = null;
    let cancelado = false;
    setEstado({ cargando: true });
    obtenerDocumento(() => cargarRef.current())
      .then((documento) => {
        url = documento.url;
        if (cancelado) URL.revokeObjectURL(url);
        else setEstado({ ...documento, cargando: false });
      })
      .catch(async (e) => {
        const error = await mensajeErrorArchivo(e, 'No se pudo cargar el documento.');
        if (!cancelado) setEstado({ cargando: false, error });
      });
    return () => {
      cancelado = true;
      if (url) URL.revokeObjectURL(url);
    };
  }, [clave]);

  return estado;
}

/** Nombre de archivo para descargar el documento, con la extensión que corresponde a su tipo real. */
export function nombreArchivo(nombre, tipo) {
  return `${(nombre || 'documento').replace(/\.(pdf|png|jpe?g)$/i, '')}.${EXTENSIONES[tipo] || 'pdf'}`;
}

export function descargarUrl(url, archivo) {
  const enlace = document.createElement('a');
  enlace.href = url;
  enlace.download = archivo;
  document.body.appendChild(enlace);
  enlace.click();
  enlace.remove();
}

/**
 * Visor de documentos del sistema (actas, constancias, comprobantes): muestra el archivo dentro de una ventana y,
 * desde ahí, permite descargarlo o abrirlo en otra pestaña.
 */
export function VisorDocumentoProvider({ children }) {
  const [documento, setDocumento] = useState(null); // { titulo, nombre, url, tipo, cargando, error }
  const urlActual = useRef(null);

  const liberar = useCallback(() => {
    if (urlActual.current) URL.revokeObjectURL(urlActual.current);
    urlActual.current = null;
  }, []);

  const cerrar = useCallback(() => {
    liberar();
    setDocumento(null);
  }, [liberar]);

  /**
   * @param titulo  texto de la ventana
   * @param nombre  nombre del archivo al descargar (sin extensión: se agrega según el tipo)
   * @param cargar  función que devuelve una promesa con el Blob del documento
   */
  const mostrar = useCallback(async ({ titulo, nombre, cargar }) => {
    liberar();
    setDocumento({ titulo, nombre, cargando: true });
    try {
      const { url, tipo } = await obtenerDocumento(cargar);
      urlActual.current = url;
      setDocumento({ titulo, nombre, url, tipo, cargando: false });
    } catch (e) {
      setDocumento({ titulo, nombre, cargando: false, error: await mensajeErrorArchivo(e, 'No se pudo cargar el documento.') });
    }
  }, [liberar]);

  useEffect(() => liberar, [liberar]);

  return (
    <VisorContext.Provider value={mostrar}>
      {children}
      {documento && <Visor documento={documento} onCerrar={cerrar} />}
    </VisorContext.Provider>
  );
}

/** Devuelve {@code mostrar({ titulo, nombre, cargar })} para abrir un documento en el visor. */
export function useVisorDocumento() {
  const mostrar = useContext(VisorContext);
  if (!mostrar) throw new Error('useVisorDocumento debe usarse dentro de VisorDocumentoProvider');
  return mostrar;
}

/**
 * Dibuja las páginas del PDF con pdf.js (se carga solo al abrir el visor). Funciona igual en computadoras y en
 * celulares, donde el visor de PDF del navegador no se muestra dentro de una ventana.
 */
function PaginasPdf({ url, zoom, onPaginas }) {
  const contenedor = useRef(null);
  const [estado, setEstado] = useState('cargando');

  useEffect(() => {
    let cancelado = false;
    let tarea = null;
    (async () => {
      const pdfjs = await import('pdfjs-dist');
      const { default: worker } = await import('pdfjs-dist/build/pdf.worker.min.mjs?url');
      pdfjs.GlobalWorkerOptions.workerSrc = worker;
      tarea = pdfjs.getDocument(url);
      const pdf = await tarea.promise;
      if (cancelado) return;
      onPaginas?.(pdf.numPages);
      const destino = contenedor.current;
      destino.replaceChildren();
      const densidad = window.devicePixelRatio || 1;
      const anchoDisponible = Math.max(destino.clientWidth - 24, 280);
      for (let numero = 1; numero <= pdf.numPages && !cancelado; numero++) {
        const pagina = await pdf.getPage(numero);
        const base = pagina.getViewport({ scale: 1 });
        const escala = (anchoDisponible / base.width) * zoom;
        const vista = pagina.getViewport({ scale: escala * densidad });
        const lienzo = document.createElement('canvas');
        lienzo.width = Math.floor(vista.width);
        lienzo.height = Math.floor(vista.height);
        lienzo.style.width = `${Math.floor(vista.width / densidad)}px`;
        lienzo.className = 'mx-auto mb-3 block max-w-none bg-white shadow-md';
        lienzo.setAttribute('aria-label', `Página ${numero} de ${pdf.numPages}`);
        destino.appendChild(lienzo);
        await pagina.render({ canvasContext: lienzo.getContext('2d'), viewport: vista }).promise;
        if (numero === 1 && !cancelado) setEstado('listo');
      }
    })().catch(() => { if (!cancelado) setEstado('error'); });
    return () => {
      cancelado = true;
      tarea?.destroy();
    };
  }, [url, zoom, onPaginas]);

  return (
    <div className="h-full overflow-auto p-3">
      {estado === 'cargando' && <p className="py-6 text-center text-sm text-white/80">Mostrando documento…</p>}
      {estado === 'error' && (
        <div className="mx-auto max-w-md rounded-lg bg-white p-4">
          <Alerta tipo="aviso">No se pudo mostrar la vista previa. Usa «Descargar» para abrir el archivo.</Alerta>
        </div>
      )}
      <div ref={contenedor} />
    </div>
  );
}

const ZOOM = [0.5, 0.75, 1, 1.25, 1.5, 2, 3];

/** Botones de zoom (alejar, porcentaje, acercar, ajustar) para {@link AreaDocumento}. */
export function ControlesZoom({ zoom, onZoom }) {
  const indice = ZOOM.indexOf(zoom);
  return (
    <div className="flex items-center gap-1" aria-label="Zoom">
      <button type="button" className="rounded-md p-1.5 text-tenue hover:bg-black/5 disabled:opacity-40"
        disabled={indice <= 0} onClick={() => onZoom(ZOOM[indice - 1])} aria-label="Alejar"><ZoomOut className="h-4 w-4" /></button>
      <span className="w-11 text-center text-xs text-tenue">{Math.round(zoom * 100)}%</span>
      <button type="button" className="rounded-md p-1.5 text-tenue hover:bg-black/5 disabled:opacity-40"
        disabled={indice >= ZOOM.length - 1} onClick={() => onZoom(ZOOM[indice + 1])} aria-label="Acercar"><ZoomIn className="h-4 w-4" /></button>
      <button type="button" className="rounded-md p-1.5 text-tenue hover:bg-black/5" onClick={() => onZoom(1)}
        aria-label="Ajustar al ancho" title="Ajustar al ancho"><Maximize2 className="h-4 w-4" /></button>
    </div>
  );
}

/** Área gris donde se muestra el documento cargado: páginas del PDF o la imagen (JPG, PNG), con zoom. */
export function AreaDocumento({ documento, titulo, zoom = 1, onPaginas, textoCarga = 'Cargando documento…' }) {
  const { url, tipo, cargando, error } = documento;
  return (
    <div className="h-full min-h-0 bg-[#525659]">
      {cargando ? (
        <div className="flex h-full items-center justify-center bg-white"><Cargando texto={textoCarga} /></div>
      ) : error ? (
        <div className="bg-white p-4"><Alerta tipo="error">{error}</Alerta></div>
      ) : tipo?.startsWith('image/') ? (
        <div className={`flex h-full overflow-auto p-4 ${zoom === 1 ? 'items-center justify-center' : 'items-start'}`}>
          <img src={url} alt={titulo}
            className={`rounded bg-white shadow ${zoom === 1 ? 'max-h-full max-w-full object-contain' : 'max-w-none'}`}
            style={zoom === 1 ? undefined : { width: `${zoom * 100}%` }} />
        </div>
      ) : (
        <PaginasPdf url={url} zoom={zoom} onPaginas={onPaginas} />
      )}
    </div>
  );
}

function Visor({ documento, onCerrar }) {
  const { titulo, nombre, url, tipo } = documento;
  const [zoom, setZoom] = useState(1);
  const [paginas, setPaginas] = useState(0);
  const archivo = nombreArchivo(nombre, tipo);
  const esImagen = tipo?.startsWith('image/');

  useEffect(() => {
    const alPulsar = (e) => e.key === 'Escape' && onCerrar();
    window.addEventListener('keydown', alPulsar);
    const desbordeAnterior = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', alPulsar);
      document.body.style.overflow = desbordeAnterior;
    };
  }, [onCerrar]);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-2 sm:p-4" onClick={onCerrar}>
      <div role="dialog" aria-modal="true" aria-label={titulo}
        className="flex h-[94vh] w-full max-w-5xl flex-col overflow-hidden rounded-xl bg-white shadow-xl"
        onClick={(e) => e.stopPropagation()}>
        <div className="flex flex-wrap items-center justify-between gap-2 border-b border-linea px-4 py-3">
          <h3 className="flex min-w-0 items-center gap-2 text-base font-semibold">
            <FileText className="h-5 w-5 shrink-0 text-marino" aria-hidden />
            <span className="truncate">{titulo}</span>
          </h3>
          <div className="flex items-center gap-2">
            {url && <div className="mr-1 hidden sm:block"><ControlesZoom zoom={zoom} onZoom={setZoom} /></div>}
            <Boton type="button" disabled={!url} onClick={() => descargarUrl(url, archivo)}>
              <Download className="h-4 w-4" /> Descargar
            </Boton>
            <Boton variante="secundario" type="button" disabled={!url} onClick={() => window.open(url, '_blank', 'noopener')}
              title="Abrir en una pestaña nueva">
              <ExternalLink className="h-4 w-4" /> <span className="hidden sm:inline">Abrir en pestaña</span>
            </Boton>
            <button type="button" onClick={onCerrar} aria-label="Cerrar visor" className="rounded-md p-1.5 text-tenue hover:bg-black/5 hover:text-tinta">
              <X className="h-5 w-5" />
            </button>
          </div>
        </div>
        <div className="min-h-0 flex-1">
          <AreaDocumento documento={documento} titulo={titulo} zoom={zoom} onPaginas={setPaginas} textoCarga="Generando documento…" />
        </div>
        <p className="border-t border-linea px-4 py-2 text-xs text-tenue">
          {archivo}{paginas > 0 && !esImagen ? ` · ${paginas} ${paginas === 1 ? 'página' : 'páginas'}` : ''}
        </p>
      </div>
    </div>
  );
}
