import { Alerta } from '../../components/ui';
import PanelIndicadores from '../../components/PanelIndicadores';

const RUTAS_SUPERVISION = {
  alumnos: '/supervision/alumnos', facilitadores: '/supervision/facilitadores', grupos: '/supervision/grupos',
  reportes: '/supervision/reportes', pagos: '/supervision/pagos', constancias: '/supervision/constancias',
  'lenguas-maternas': '/supervision/lenguas-maternas', examenes: '/supervision/examenes',
  diagnosticos: '/supervision/diagnosticos',
};

/** Panorama general de la jefa del DGTV (Figura 45): indicadores y gráficas, sin acciones de registro ni autorización. */
export default function PanelSupervision() {
  return (
    <>
      <Alerta tipo="info">
        <b>Modo de solo consulta.</b> Puedes revisar toda la información del sistema y los mensajes de la coordinación;
        las operaciones las realiza la coordinación. Haz clic en cualquier indicador para ver su detalle.
      </Alerta>
      <PanelIndicadores rutas={RUTAS_SUPERVISION} />
    </>
  );
}
