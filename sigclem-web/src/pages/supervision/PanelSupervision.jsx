import PanelCoordinador from '../coordinacion/PanelCoordinador';

/** Panorama general de la jefa del DGTV: misma interfaz que el coordinador, en modo solo lectura. */
export default function PanelSupervision() {
  return <PanelCoordinador soloLectura />;
}
