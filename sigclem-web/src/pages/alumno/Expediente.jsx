import { Fragment, useEffect, useState } from 'react';
import { IdCard, Network, Pencil } from 'lucide-react';
import { useAuth } from '../../auth/AuthContext';
import { actualizarMiSemestre, obtenerExpediente } from '../../api/alumnos';
import { mensajeError } from '../../api/cliente';
import { Alerta, Boton, Cargando, Tarjeta, claseEntrada } from '../../components/ui';
import AvanceReticular from '../../components/AvanceReticular';

const SEMESTRES = Array.from({ length: 12 }, (_, i) => i + 1);

function fecha(iso) {
  return iso ? new Date(`${iso}T00:00:00`).toLocaleDateString('es-MX') : '—';
}

/** Selector del semestre con su botón; se usa en el aviso de confirmación y al editar desde los datos generales. */
function FormularioSemestre({ inicial, textoBoton, onGuardar, onCancelar }) {
  const [semestre, setSemestre] = useState(String(inicial));
  const [guardando, setGuardando] = useState(false);
  return (
    <form className="flex flex-wrap items-center gap-2" onSubmit={async (e) => {
      e.preventDefault();
      setGuardando(true);
      try { await onGuardar(Number(semestre)); } finally { setGuardando(false); }
    }}>
      <select aria-label="Semestre que cursas" className={`${claseEntrada} max-w-[220px]`} value={semestre}
        onChange={(e) => setSemestre(e.target.value)}>
        {SEMESTRES.map((s) => <option key={s} value={s}>{s}.º semestre</option>)}
      </select>
      <Boton type="submit" disabled={guardando}>{guardando ? 'Guardando…' : textoBoton}</Boton>
      {onCancelar && <Boton type="button" variante="secundario" onClick={onCancelar}>Cancelar</Boton>}
    </form>
  );
}

/** CU-03 Consultar expediente y avance reticular (Figura 38). El alumno mantiene actualizado su semestre. */
export default function Expediente() {
  const { perfil } = useAuth();
  const [exp, setExp] = useState(null);
  const [error, setError] = useState('');
  const [aviso, setAviso] = useState('');
  const [editando, setEditando] = useState(false);

  useEffect(() => {
    obtenerExpediente(perfil.usuario).then(setExp).catch((e) => setError(mensajeError(e)));
  }, [perfil.usuario]);

  const guardarSemestre = async (semestre) => {
    setError('');
    setAviso('');
    try {
      setExp(await actualizarMiSemestre(semestre));
      setEditando(false);
      setAviso(`Semestre actualizado: ${semestre}.º`);
    } catch (e) {
      setError(mensajeError(e));
    }
  };

  if (!exp) return error ? <Alerta tipo="error">{error}</Alerta> : <Cargando />;

  const porcentaje = Math.round((exp.nivelesCubiertos / exp.avance.length) * 100);
  const semestre = editando ? (
    <FormularioSemestre key="sem" inicial={exp.semestre} textoBoton="Guardar" onGuardar={guardarSemestre}
      onCancelar={() => setEditando(false)} />
  ) : (
    <span key="sem" className="inline-flex flex-wrap items-center gap-x-2">
      {exp.semestre}.º
      <button type="button" className="inline-flex items-center gap-1 text-xs font-semibold text-marino underline"
        onClick={() => setEditando(true)}><Pencil className="h-3 w-3" /> Actualizar</button>
      {exp.fechaActualizacionSemestre && (
        <span className="text-xs text-tenue">(actualizado el {fecha(exp.fechaActualizacionSemestre)})</span>
      )}
    </span>
  );
  const datos = [
    ['Número de control', <b key="nc">{exp.numeroControl}</b>],
    ['Nombre', exp.nombreCompleto],
    ['Carrera', exp.carrera],
    ['Semestre', semestre],
    ['Nivel actual', exp.formaLiberacion
      ? <span key="na"><b>{exp.nivelActual}</b> <span className="text-xs text-tenue">(liberado por {exp.formaLiberacion})</span></span>
      : <b key="na">{exp.nivelActual}</b>],
    ['Fecha de alta', fecha(exp.fechaInscripcion)],
  ];

  return (
    <div className="grid gap-[18px]">
      {error && <Alerta tipo="error">{error}</Alerta>}
      {aviso && <Alerta tipo="ok">{aviso}</Alerta>}
      {exp.semestrePorConfirmar && !editando && (
        <Alerta tipo="aviso">
          <div className="grid gap-2">
            <span><b>Confirma el semestre que cursas.</b> La coordinación lo usa para sus reportes y grupos; actualízalo
              al inicio de cada periodo.</span>
            <FormularioSemestre inicial={exp.semestre} textoBoton="Confirmar" onGuardar={guardarSemestre} />
          </div>
        </Alerta>
      )}
      <div className="grid grid-cols-1 gap-[18px] xl:grid-cols-[1fr_2fr]">
        <Tarjeta titulo="Datos generales" icono={IdCard}>
          <dl className="grid grid-cols-[140px_1fr] gap-x-3 gap-y-2">
            {datos.map(([k, v]) => (
              <Fragment key={k}><dt className="text-tenue">{k}</dt><dd>{v}</dd></Fragment>
            ))}
          </dl>
        </Tarjeta>
        <Tarjeta titulo="Avance reticular de inglés" icono={Network}
          extra={`${exp.nivelesCubiertos} de ${exp.avance.length} niveles acreditados o exentos`}>
          <div className="mb-3.5 h-2 rounded-md bg-neutro-fondo" role="progressbar" aria-valuenow={porcentaje} aria-valuemin={0} aria-valuemax={100}>
            <div className="h-2 rounded-md bg-ok" style={{ width: `${porcentaje}%` }} />
          </div>
          <AvanceReticular avance={exp.avance} />
          <p className="mt-3 text-xs text-tenue">
            Pasa el mouse sobre un nivel para ver su calificación. Solo puedes cursar el nivel siguiente al último
            acreditado o exento (validación secuencial, RF-04).
          </p>
        </Tarjeta>
      </div>
    </div>
  );
}
