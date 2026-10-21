import { useCallback, useEffect, useMemo, useState } from 'react';
import { Bell, Inbox, Mail, MailOpen, PenSquare, Reply, Search, Send, Users } from 'lucide-react';
import { consultarDestinatarios, enviarMensaje, listarEnviados, listarRecibidos, marcarLeido } from '../api/mensajes';
import { mensajeError } from '../api/cliente';
import { Alerta, Boton, Campo, Cargando, Insignia, Tarjeta, claseEntrada } from '../components/ui';
import { Modal } from '../components/Modal';

const TONO_ROL = { Alumno: 'info', Facilitador: 'ok', Coordinador: 'aviso', 'Jefa del DGTV': 'neutro', Sistema: 'neutro' };

function fechaHora(fecha) {
  if (!fecha) return '';
  const d = new Date(fecha);
  const hoy = new Date();
  return d.toDateString() === hoy.toDateString()
    ? d.toLocaleTimeString('es-MX', { hour: '2-digit', minute: '2-digit' })
    : d.toLocaleDateString('es-MX', { day: 'numeric', month: 'short', year: 'numeric' });
}

/**
 * Mensajería: alumno ↔ facilitador, alumno ↔ coordinación, facilitador ↔ coordinación y coordinación → todos
 * (también por listas de difusión). Los avisos automáticos del sistema llegan a la misma bandeja.
 */
export default function Mensajes() {
  const [vista, setVista] = useState('recibidos');
  const [recibidos, setRecibidos] = useState(null);
  const [enviados, setEnviados] = useState(null);
  const [destinatarios, setDestinatarios] = useState(null);
  const [abierto, setAbierto] = useState(null);
  const [redactar, setRedactar] = useState(null);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');

  const cargar = useCallback(async () => {
    try {
      const [r, e, d] = await Promise.all([listarRecibidos(), listarEnviados(), consultarDestinatarios()]);
      setRecibidos(r);
      setEnviados(e);
      setDestinatarios(d);
    } catch (err) {
      setError(mensajeError(err));
    }
  }, []);
  useEffect(() => { cargar(); }, [cargar]);

  const abrir = async (m) => {
    setAbierto(m);
    if (!m.leido) {
      try {
        await marcarLeido(m.id);
        setRecibidos((lista) => lista.map((x) => (x.id === m.id ? { ...x, leido: true } : x)));
        window.dispatchEvent(new Event('sigclem:mensajes-leidos'));
      } catch { /* la lectura se marcará en otro momento */ }
    }
  };

  const responder = (m) => setRedactar({
    modo: 'personas', seleccion: [m.idRemitente], difusion: '', asunto: m.asunto.startsWith('Re: ') ? m.asunto : `Re: ${m.asunto}`, mensaje: '',
  });

  if (!recibidos && !error) return <Cargando />;
  const sinLeer = (recibidos || []).filter((m) => !m.leido).length;
  const puedeResponder = (m) => destinatarios?.puedeEnviar && m.idRemitente
    && destinatarios.contactos.some((c) => c.idUsuario === m.idRemitente);

  return (
    <div className="grid gap-[18px]">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="inline-flex rounded-lg border border-linea bg-white p-0.5 text-xs">
          {[['recibidos', `Recibidos${sinLeer ? ` (${sinLeer} sin leer)` : ''}`, Inbox], ['enviados', 'Enviados', Send]].map(([v, t, Icono]) => (
            <button key={v} type="button" onClick={() => { setVista(v); setAbierto(null); }}
              className={`inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 font-semibold ${vista === v ? 'bg-marino text-white' : 'text-tenue hover:text-tinta'}`}>
              <Icono className="h-3.5 w-3.5" /> {t}
            </button>
          ))}
        </div>
        {destinatarios?.puedeEnviar ? (
          <Boton onClick={() => { setAviso(''); setRedactar({ modo: 'personas', seleccion: [], difusion: '', asunto: '', mensaje: '' }); }}>
            <PenSquare className="h-4 w-4" /> Redactar mensaje
          </Boton>
        ) : destinatarios && <span className="text-xs text-tenue">Acceso de solo consulta: puedes leer tus mensajes.</span>}
      </div>

      {error && <Alerta tipo="error">{error}</Alerta>}
      {aviso && <Alerta tipo="ok">{aviso}</Alerta>}

      {vista === 'recibidos' ? (
        <div className="grid grid-cols-1 gap-[18px] lg:grid-cols-[minmax(300px,0.9fr)_1.4fr]">
          <Tarjeta titulo="Bandeja de entrada" icono={Inbox} className="!p-0 [&>h2]:px-[18px] [&>h2]:pt-[18px]">
            {recibidos.length === 0 ? <p className="px-[18px] pb-[18px] text-sm text-tenue">No tienes mensajes.</p> : (
              <ul className="max-h-[65vh] divide-y divide-linea overflow-y-auto">
                {recibidos.map((m) => (
                  <li key={m.id}>
                    <button type="button" onClick={() => abrir(m)}
                      className={`flex w-full gap-3 px-[18px] py-3 text-left transition hover:bg-fondo ${abierto?.id === m.id ? 'bg-marino/5' : ''}`}>
                      <span className={`mt-1.5 h-2 w-2 shrink-0 rounded-full ${m.leido ? 'bg-transparent' : 'bg-marino'}`} aria-label={m.leido ? '' : 'Sin leer'} />
                      <span className="min-w-0 flex-1">
                        <span className="flex items-center justify-between gap-2">
                          <span className={`truncate text-[13px] ${m.leido ? '' : 'font-bold'}`}>{m.remitente}</span>
                          <span className="shrink-0 text-[11px] text-tenue">{fechaHora(m.fechaEnvio)}</span>
                        </span>
                        <span className={`block truncate text-[12.5px] ${m.leido ? 'text-tenue' : 'font-semibold'}`}>{m.asunto}</span>
                      </span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </Tarjeta>
          <Tarjeta>
            {!abierto ? (
              <div className="flex flex-col items-center gap-2 py-16 text-tenue">
                <MailOpen className="h-8 w-8" /> Selecciona un mensaje para leerlo.
              </div>
            ) : (
              <article>
                <div className="flex flex-wrap items-start justify-between gap-3 border-b border-linea pb-3">
                  <div>
                    <h3 className="text-lg font-semibold">{abierto.asunto}</h3>
                    <div className="mt-1 flex flex-wrap items-center gap-2 text-[13px] text-tenue">
                      {abierto.idRemitente ? <Mail className="h-3.5 w-3.5" /> : <Bell className="h-3.5 w-3.5" />}
                      <b className="text-tinta">{abierto.remitente}</b>
                      <Insignia tono={TONO_ROL[abierto.rolRemitente]}>{abierto.rolRemitente}</Insignia>
                      · {new Date(abierto.fechaEnvio).toLocaleString('es-MX', { dateStyle: 'long', timeStyle: 'short' })}
                    </div>
                  </div>
                  {puedeResponder(abierto) && <Boton variante="secundario" onClick={() => responder(abierto)}><Reply className="h-4 w-4" /> Responder</Boton>}
                </div>
                <p className="mt-4 whitespace-pre-wrap text-[14px] leading-relaxed">{abierto.mensaje}</p>
              </article>
            )}
          </Tarjeta>
        </div>
      ) : (
        <Tarjeta titulo="Mensajes enviados" icono={Send}>
          {enviados.length === 0 ? <p className="text-sm text-tenue">Aún no has enviado mensajes.</p> : (
            <ul className="divide-y divide-linea">
              {enviados.map((m) => (
                <li key={m.clave} className="py-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <span className="font-semibold">{m.asunto}</span>
                    <span className="text-xs text-tenue">{fechaHora(m.fechaEnvio)}</span>
                  </div>
                  <div className="mt-0.5 flex flex-wrap items-center gap-2 text-xs text-tenue">
                    <Users className="h-3.5 w-3.5" /> Para: {m.paraQuien}
                    <span>· Leído por {m.leidos} de {m.destinatarios}</span>
                  </div>
                  <p className="mt-1 line-clamp-2 whitespace-pre-wrap text-[13px]">{m.mensaje}</p>
                </li>
              ))}
            </ul>
          )}
        </Tarjeta>
      )}

      {redactar && (
        <Redactar borrador={redactar} setBorrador={setRedactar} destinatarios={destinatarios}
          onEnviado={async (n) => { setRedactar(null); setAviso(`Mensaje enviado a ${n} destinatario(s).`); setVista('enviados'); await cargar(); }} />
      )}
    </div>
  );
}

function Redactar({ borrador, setBorrador, destinatarios, onEnviado }) {
  const [filtro, setFiltro] = useState('');
  const [error, setError] = useState('');
  const [enviando, setEnviando] = useState(false);
  const cambiar = (campo, valor) => setBorrador({ ...borrador, [campo]: valor });
  const hayDifusion = destinatarios.difusiones.length > 0;

  const contactosFiltrados = useMemo(() => {
    const t = filtro.trim().toLowerCase();
    return destinatarios.contactos.filter((c) => !t || `${c.nombre} ${c.detalle} ${c.rol}`.toLowerCase().includes(t));
  }, [filtro, destinatarios.contactos]);
  const porRol = useMemo(() => {
    const grupos = {};
    contactosFiltrados.forEach((c) => { (grupos[c.rol] ||= []).push(c); });
    return Object.entries(grupos);
  }, [contactosFiltrados]);

  const alternar = (id) => cambiar('seleccion', borrador.seleccion.includes(id)
    ? borrador.seleccion.filter((x) => x !== id) : [...borrador.seleccion, id]);

  const enviar = async (e) => {
    e.preventDefault();
    const personas = borrador.modo === 'personas';
    if (personas && borrador.seleccion.length === 0) { setError('Elige al menos un destinatario.'); return; }
    if (!personas && !borrador.difusion) { setError('Elige una lista de difusión.'); return; }
    setEnviando(true);
    setError('');
    try {
      const r = await enviarMensaje({
        idsDestinatarios: personas ? borrador.seleccion : [],
        difusion: personas ? null : borrador.difusion,
        asunto: borrador.asunto.trim(),
        mensaje: borrador.mensaje.trim(),
      });
      await onEnviado(r.destinatarios);
    } catch (err) {
      setError(mensajeError(err));
    } finally {
      setEnviando(false);
    }
  };

  const nombresSeleccionados = destinatarios.contactos.filter((c) => borrador.seleccion.includes(c.idUsuario));

  return (
    <Modal titulo="Nuevo mensaje" onCerrar={() => setBorrador(null)} ancho="max-w-3xl">
      <form className="grid gap-4" onSubmit={enviar}>
        {error && <Alerta tipo="error">{error}</Alerta>}
        {hayDifusion && (
          <div className="inline-flex w-fit rounded-lg border border-linea bg-fondo p-0.5 text-xs">
            {[['personas', 'Elegir personas'], ['difusion', 'Lista de difusión']].map(([v, t]) => (
              <button key={v} type="button" onClick={() => cambiar('modo', v)}
                className={`rounded-md px-3 py-1.5 font-semibold ${borrador.modo === v ? 'bg-white text-marino shadow-sm' : 'text-tenue'}`}>{t}</button>
            ))}
          </div>
        )}

        {borrador.modo === 'personas' ? (
          <div className="grid gap-2">
            <div className="text-xs font-semibold text-[#3A4556]">
              Para: {nombresSeleccionados.length === 0 ? <span className="font-normal text-tenue">nadie seleccionado</span>
                : nombresSeleccionados.map((c) => c.nombre).join(', ')}
            </div>
            <div className="relative">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-tenue" />
              <input className={`${claseEntrada} pl-9`} placeholder="Buscar por nombre, rol o grupo" value={filtro} onChange={(e) => setFiltro(e.target.value)} />
            </div>
            <div className="max-h-56 overflow-y-auto rounded-lg border border-linea">
              {porRol.length === 0 && <p className="p-3 text-[13px] text-tenue">No hay contactos que coincidan.</p>}
              {porRol.map(([rol, lista]) => (
                <div key={rol}>
                  <div className="sticky top-0 bg-fondo px-3 py-1 text-[11px] font-semibold uppercase tracking-wider text-tenue">{rol}</div>
                  {lista.map((c) => (
                    <label key={c.idUsuario} className="flex cursor-pointer items-center gap-3 px-3 py-2 text-[13px] hover:bg-fondo">
                      <input type="checkbox" checked={borrador.seleccion.includes(c.idUsuario)} onChange={() => alternar(c.idUsuario)} />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-semibold">{c.nombre}</span>
                        <span className="block truncate text-xs text-tenue">{c.detalle}</span>
                      </span>
                    </label>
                  ))}
                </div>
              ))}
            </div>
          </div>
        ) : (
          <Campo etiqueta="Enviar a" id="difusion">
            <select id="difusion" className={claseEntrada} value={borrador.difusion} onChange={(e) => cambiar('difusion', e.target.value)}>
              <option value="">Selecciona una lista…</option>
              {destinatarios.difusiones.map((d) => <option key={d.clave} value={d.clave}>{d.etiqueta} ({d.destinatarios})</option>)}
            </select>
          </Campo>
        )}

        <Campo etiqueta="Asunto" id="asuntoMensaje">
          <input id="asuntoMensaje" className={claseEntrada} required maxLength="100" value={borrador.asunto} onChange={(e) => cambiar('asunto', e.target.value)} />
        </Campo>
        <Campo etiqueta="Mensaje" id="cuerpoMensaje" ayuda={`${borrador.mensaje.length} de 2000 caracteres`}>
          <textarea id="cuerpoMensaje" className={claseEntrada} rows="6" required maxLength="2000" value={borrador.mensaje}
            onChange={(e) => cambiar('mensaje', e.target.value)} />
        </Campo>
        <div className="flex justify-end gap-2">
          <Boton type="button" variante="secundario" onClick={() => setBorrador(null)}>Cancelar</Boton>
          <Boton type="submit" disabled={enviando}><Send className="h-4 w-4" /> {enviando ? 'Enviando…' : 'Enviar'}</Boton>
        </div>
      </form>
    </Modal>
  );
}
