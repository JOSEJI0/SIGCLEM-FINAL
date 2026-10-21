import { Fragment, useState } from 'react';
import { Award, BookOpen, ClipboardList, CreditCard, Languages, Network } from 'lucide-react';
import { Insignia, Tarjeta } from './ui';
import AvanceReticular from './AvanceReticular';

const fecha = (iso) => (iso ? new Date(iso.length === 10 ? `${iso}T00:00:00` : iso).toLocaleDateString('es-MX') : '—');
const pesos = (v) => (v == null ? '—' : Number(v).toLocaleString('es-MX', { style: 'currency', currency: 'MXN' }));

function Vacio({ children }) {
  return <p className="py-4 text-[13px] text-tenue">{children}</p>;
}

function Tabla({ columnas, filas }) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[560px] text-left text-[13px]">
        <thead className="border-b border-linea text-xs text-tenue">
          <tr>{columnas.map((c) => <th key={c} className="px-2 py-2">{c}</th>)}</tr>
        </thead>
        <tbody>
          {filas.map((fila, i) => (
            <tr key={i} className="border-b border-linea last:border-0">{fila.map((celda, j) => <td key={j} className="px-2 py-2.5">{celda}</td>)}</tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Ficha única del alumno (coordinación y jefa del DGTV): datos, avance y todo su historial por pestañas. */
export default function ExpedienteIntegral({ datos }) {
  const [pestana, setPestana] = useState('avance');
  const { expediente: e, historial, cargos, examenes, diagnosticos, constancias, lenguasMaternas } = datos;
  const saldo = cargos.reduce((s, c) => s + Number(c.saldoPendiente || 0), 0);

  const PESTANAS = [
    ['avance', 'Avance', Network],
    ['historial', `Cursos (${historial.length})`, BookOpen],
    ['pagos', `Pagos (${cargos.length})`, CreditCard],
    ['examenes', `Exámenes y diagnósticos (${examenes.length + diagnosticos.length})`, ClipboardList],
    ['constancias', `Constancias (${constancias.length})`, Award],
    ...(e.hablaLenguaMaterna || lenguasMaternas.length ? [['lengua', `Lengua materna (${lenguasMaternas.length})`, Languages]] : []),
  ];

  const datosGenerales = [
    ['Nombre', e.nombreCompleto], ['Número de control', e.numeroControl],
    ['Sexo', { H: 'Hombre', M: 'Mujer' }[e.sexo] || '—'], ['Carrera', e.carrera],
    ['Semestre', `${e.semestre}.º`], ['Correo', e.correo || '—'], ['Fecha de alta', fecha(e.fechaInscripcion)],
    ['Estatus', e.estatus === 'Baja' ? `Baja${e.motivoBaja ? ` (${e.motivoBaja})` : ''}` : 'Activo'],
    ['Nivel actual', e.formaLiberacion
      ? <span key="n"><b>{e.nivelActual}</b> <span className="text-xs text-tenue">(liberado por {e.formaLiberacion})</span></span>
      : <b key="n">{e.nivelActual}</b>],
    ['Niveles cubiertos', `${e.nivelesCubiertos} de ${e.avance.length}`],
    ['Saldo pendiente', saldo > 0 ? <b key="s" className="text-aviso">{pesos(saldo)}</b> : 'Sin adeudos'],
    ['Lengua materna', e.hablaLenguaMaterna ? 'Sí habla' : 'No'],
  ];

  return (
    <Tarjeta titulo={`Expediente integral · ${e.nombreCompleto}`} icono={BookOpen}>
      <dl className="mb-4 grid gap-x-4 gap-y-2 text-[13px] sm:grid-cols-2 lg:grid-cols-5">
        {datosGenerales.map(([k, v]) => (
          <Fragment key={k}><div><dt className="text-xs text-tenue">{k}</dt><dd className="font-medium">{v}</dd></div></Fragment>
        ))}
      </dl>
      <div className="mb-4 flex flex-wrap gap-1 border-b border-linea" role="tablist">
        {PESTANAS.map(([clave, texto, Icono]) => (
          <button key={clave} type="button" role="tab" aria-selected={pestana === clave} onClick={() => setPestana(clave)}
            className={`-mb-px inline-flex items-center gap-1.5 border-b-2 px-3 py-2 text-[13px] font-semibold ${pestana === clave ? 'border-marino text-marino' : 'border-transparent text-tenue hover:text-tinta'}`}>
            <Icono className="h-3.5 w-3.5" /> {texto}
          </button>
        ))}
      </div>

      {pestana === 'avance' && (
        <>
          <AvanceReticular avance={e.avance} compacto />
          <p className="mt-2 text-xs text-tenue">Pasa el mouse sobre un nivel para ver su calificación.</p>
        </>
      )}

      {pestana === 'historial' && (historial.length === 0 ? <Vacio>Aún no ha cursado ningún grupo.</Vacio> : (
        <Tabla columnas={['Periodo', 'Nivel', 'Grupo', 'Calificación', 'Resultado', 'Cierre de acta']}
          filas={historial.map((h) => [h.periodo, `${h.nivel}. ${h.nombreNivel}`, h.grupo, h.calificacion ?? '—',
            <Insignia key="r" estatus={h.resultado === 'No acreditado' ? 'Reprobado' : h.resultado === 'Acreditado' ? 'Acreditado' : 'Pendiente'}>{h.resultado}</Insignia>,
            fecha(h.fechaCierreActa)])} />
      ))}

      {pestana === 'pagos' && (cargos.length === 0 ? <Vacio>No tiene cargos emitidos.</Vacio> : (
        <Tabla columnas={['Concepto', 'Periodo', 'Emisión', 'Monto', 'Saldo', 'Estatus', 'Comprobantes']}
          filas={cargos.map((c) => [`${c.concepto}${c.nivel ? ` · ${c.nivel}` : ''}`, c.periodo, fecha(c.fechaEmision), pesos(c.montoEsperado),
            pesos(c.saldoPendiente), <Insignia key="e" estatus={c.estatusCargo} />,
            c.comprobantes.length ? c.comprobantes.map((p) => `${pesos(p.total)} ${p.estatus.toLowerCase()}`).join(', ') : '—'])} />
      ))}

      {pestana === 'examenes' && (
        examenes.length + diagnosticos.length === 0 ? <Vacio>Sin exámenes ni evaluaciones diagnósticas.</Vacio> : (
          <Tabla columnas={['Tipo', 'Fecha', 'Resultado', 'Puntaje', 'Observaciones']}
            filas={[
              ...examenes.map((x) => [`Examen de ${x.tipo}`, fecha(x.fechaHoraAplicacion),
                x.concluido ? <Insignia key="r" estatus="Concluido">Nivel asignado: {x.nombreNivelAsignado}</Insignia> : <Insignia key="r" estatus="Pendiente">Sin resultado</Insignia>,
                x.puntajeTotal ?? '—', x.observaciones || '—']),
              ...diagnosticos.map((d) => ['Evaluación diagnóstica', fecha(d.fechaHoraAplicacion || d.fechaSolicitud),
                d.estatus === 'Concluida' ? <Insignia key="r" estatus="Concluida">Recomienda: {d.recomendacion}</Insignia> : <Insignia key="r" estatus={d.estatus} />,
                d.puntaje ?? '—', d.observaciones || '—']),
            ]} />
        ))}

      {pestana === 'constancias' && (constancias.length === 0 ? <Vacio>No tiene constancias.</Vacio> : (
        <Tabla columnas={['Folio', 'Solicitud', 'Expedición', 'Niveles', 'MCER', 'Estatus']}
          filas={constancias.map((c) => [c.folio || '—', fecha(c.fechaSolicitud), fecha(c.fechaExpedicion), c.nivelesAcreditados,
            c.nivelMcer, <Insignia key="e" estatus={c.estatus} />])} />
      ))}

      {pestana === 'lengua' && (lenguasMaternas.length === 0 ? <Vacio>Declaró hablar una lengua materna, pero aún no registra su documento.</Vacio> : (
        <Tabla columnas={['Lengua', 'Nivel de dominio', 'Registro', 'Estatus']}
          filas={lenguasMaternas.map((r) => [r.lengua || r.nombreLengua, r.nivelDominio || '—', fecha(r.fechaRegistro), <Insignia key="e" estatus={r.estatus} />])} />
      ))}
    </Tarjeta>
  );
}
