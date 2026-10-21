import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import { ROLES, rutaInicio } from './auth/roles';
import RutaProtegida from './auth/RutaProtegida';
import LayoutPrincipal from './layout/LayoutPrincipal';
import Login from './pages/Login';
import { NoAutorizado, NoEncontrado } from './pages/Errores';
import Expediente from './pages/alumno/Expediente';
import ExamenesAlumno from './pages/alumno/ExamenesAlumno';
import Diagnostico from './pages/alumno/Diagnostico';
import PagosAlumno from './pages/alumno/PagosAlumno';
import LenguaMaterna from './pages/alumno/LenguaMaterna';
import CursosDisponibles from './pages/alumno/CursosDisponibles';
import Boletas from './pages/alumno/Boletas';
import ConstanciasAlumno from './pages/alumno/Constancias';
import PanelCoordinador from './pages/coordinacion/PanelCoordinador';
import Alumnos from './pages/coordinacion/Alumnos';
import DiagnosticosExamenes from './pages/coordinacion/DiagnosticosExamenes';
import EvaluacionesDiagnosticas from './pages/coordinacion/EvaluacionesDiagnosticas';
import Facilitadores from './pages/coordinacion/Facilitadores';
import Grupos from './pages/coordinacion/Grupos';
import Pagos from './pages/coordinacion/Pagos';
import PanelSupervision from './pages/supervision/PanelSupervision';
import FacilitadoresSupervision from './pages/supervision/FacilitadoresSupervision';
import PagosSupervision from './pages/supervision/PagosSupervision';
import GruposSupervision from './pages/supervision/GruposSupervision';
import Mensajes from './pages/Mensajes';
import LenguasMaternas from './pages/coordinacion/LenguasMaternas';
import Reportes from './pages/Reportes';
import ConstanciasCoordinacion from './pages/coordinacion/Constancias';
import FirmasMembrete from './pages/coordinacion/FirmasMembrete';
import Cuentas from './pages/coordinacion/Cuentas';
import Auditoria from './pages/coordinacion/Auditoria';
import PanelFacilitador from './pages/facilitador/PanelFacilitador';
import GruposFacilitador from './pages/facilitador/GruposFacilitador';
import MisGrupos from './pages/facilitador/MisGrupos';
import PerfilFacilitador from './pages/facilitador/PerfilFacilitador';

/** El apartado de lengua materna solo existe para quien respondió en su registro que habla una. */
function SoloHablantesLenguaMaterna({ children }) {
  const { perfil } = useAuth();
  return perfil?.hablaLenguaMaterna === false ? <Navigate to="/alumno" replace /> : children;
}

function Inicio() {
  const { perfil } = useAuth();
  return <Navigate to={perfil ? rutaInicio(perfil.rol) : '/login'} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Inicio />} />
      <Route path="/login" element={<Login />} />
      <Route path="/no-autorizado" element={<NoAutorizado />} />

      <Route element={<RutaProtegida roles={[ROLES.ALUMNO]} />}>
        <Route path="/alumno" element={<LayoutPrincipal />}>
          <Route index element={<Expediente />} />
          <Route path="cursos" element={<CursosDisponibles />} />
          <Route path="mensajes" element={<Mensajes />} />
          <Route path="examenes" element={<ExamenesAlumno />} />
          <Route path="diagnostico" element={<Diagnostico />} />
          <Route path="pagos" element={<PagosAlumno />} />
          <Route path="boletas" element={<Boletas />} />
          <Route path="constancias" element={<ConstanciasAlumno />} />
          <Route path="lengua-materna" element={<SoloHablantesLenguaMaterna><LenguaMaterna /></SoloHablantesLenguaMaterna>} />
        </Route>
      </Route>

      <Route element={<RutaProtegida roles={[ROLES.FACILITADOR]} />}>
        <Route path="/facilitador" element={<LayoutPrincipal />}>
          <Route index element={<PanelFacilitador />} />
          <Route path="grupos" element={<MisGrupos />} />
          <Route path="actas" element={<GruposFacilitador />} />
          <Route path="perfil" element={<PerfilFacilitador />} />
          <Route path="mensajes" element={<Mensajes />} />
        </Route>
      </Route>

      <Route element={<RutaProtegida roles={[ROLES.COORDINADOR, ROLES.ADMIN]} />}>
        <Route path="/coordinacion" element={<LayoutPrincipal />}>
          <Route index element={<PanelCoordinador />} />
          <Route path="alumnos" element={<Alumnos />} />
          <Route path="diagnosticos" element={<DiagnosticosExamenes />} />
          <Route path="evaluaciones-diagnosticas" element={<EvaluacionesDiagnosticas />} />
          <Route path="facilitadores" element={<Facilitadores />} />
          <Route path="grupos" element={<Grupos />} />
          <Route path="comprobantes" element={<Pagos />} />
          <Route path="constancias" element={<ConstanciasCoordinacion />} />
          <Route path="firmas-membrete" element={<FirmasMembrete />} />
          <Route path="cuentas" element={<Cuentas />} />
          <Route path="lenguas-maternas" element={<LenguasMaternas />} />
          <Route path="reportes" element={<Reportes />} />
          <Route path="mensajes" element={<Mensajes />} />
        </Route>
      </Route>

      {/* Solo el administrador: bitácora de auditoría. */}
      <Route element={<RutaProtegida roles={[ROLES.ADMIN]} />}>
        <Route path="/coordinacion" element={<LayoutPrincipal />}>
          <Route path="auditoria" element={<Auditoria />} />
        </Route>
      </Route>

      <Route element={<RutaProtegida roles={[ROLES.JEFA_DGTV]} />}>
        <Route path="/supervision" element={<LayoutPrincipal />}>
          <Route index element={<PanelSupervision />} />
          <Route path="alumnos" element={<Alumnos />} />
          <Route path="facilitadores" element={<FacilitadoresSupervision />} />
          <Route path="grupos" element={<GruposSupervision />} />
          <Route path="examenes" element={<DiagnosticosExamenes soloLectura />} />
          <Route path="diagnosticos" element={<EvaluacionesDiagnosticas soloLectura />} />
          <Route path="pagos" element={<PagosSupervision />} />
          <Route path="constancias" element={<ConstanciasCoordinacion soloLectura />} />
          <Route path="lenguas-maternas" element={<LenguasMaternas soloLectura />} />
          <Route path="reportes" element={<Reportes />} />
          <Route path="mensajes" element={<Mensajes />} />
        </Route>
      </Route>

      <Route path="*" element={<NoEncontrado />} />
    </Routes>
  );
}
