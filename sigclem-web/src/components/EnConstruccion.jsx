import { Construction } from 'lucide-react';
import { Tarjeta } from './ui';

/** Marcador para las pantallas que se implementarán en los siguientes incrementos de la Fase 5. */
export default function EnConstruccion({ titulo, casosUso }) {
  return (
    <Tarjeta titulo={titulo} icono={Construction}>
      <p className="text-tenue">
        Esta pantalla se implementará en un incremento posterior de la Fase 5
        {casosUso ? <> (casos de uso <b className="text-tinta">{casosUso}</b>)</> : null}.
      </p>
    </Tarjeta>
  );
}
