package mx.edu.itch.sigclem.reportes.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.catalogos.domain.Periodo;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.dto.PeriodoDTO;
import mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.grupos.domain.Inscripcion;
import mx.edu.itch.sigclem.grupos.repository.GrupoRepository;
import mx.edu.itch.sigclem.grupos.repository.InscripcionRepository;
import mx.edu.itch.sigclem.reportes.dto.ReporteAcademicoDTO;
import mx.edu.itch.sigclem.reportes.dto.ReporteAlumnoGrupoDTO;
import mx.edu.itch.sigclem.reportes.dto.ReporteCarreraNivelDTO;
import mx.edu.itch.sigclem.reportes.dto.ReporteGrupoDTO;
import mx.edu.itch.sigclem.reportes.dto.ReporteLenguaMaternaDTO;
import mx.edu.itch.sigclem.reportes.dto.ReporteNivelDTO;
import mx.edu.itch.sigclem.lenguasmaternas.repository.RegistroLenguaMaternaRepository;

@Service
@RequiredArgsConstructor
public class ReporteAcademicoService {

    private static final int CALIFICACION_MINIMA_APROBATORIA = 70;

    private final PeriodoRepository periodoRepository;
    private final GrupoRepository grupoRepository;
    private final InscripcionRepository inscripcionRepository;
    private final RegistroLenguaMaternaRepository registroLenguaMaternaRepository;

    @Transactional(readOnly = true)
    public ReporteAcademicoDTO obtenerReporte(Integer periodoId) {
        Periodo periodo = periodoId == null ? obtenerPeriodoPredeterminado() : buscarPeriodo(periodoId);
        List<Grupo> grupos = grupoRepository.findByPeriodoId(periodo.getId());
        List<Inscripcion> inscripciones = inscripcionRepository.findByGrupo_Periodo_Id(periodo.getId());
        Map<Integer, AcumuladoNivel> niveles = new TreeMap<>();
        // RF-18: clave "carrera|número de nivel" ordenada por carrera y luego por nivel.
        Map<String, AcumuladoNivel> carreraNivel = new TreeMap<>();
        Map<Integer, List<Inscripcion>> inscripcionesPorGrupo = new java.util.HashMap<>();
        Set<String> alumnos = new HashSet<>();
        int actasCerradas = 0;
        TotalesCalificacion totales = new TotalesCalificacion();

        for (Grupo grupo : grupos) {
            acumuladoNivel(niveles, grupo.getNivel()).grupos++;
            if (grupo.getNivelSecundario() != null) {
                acumuladoNivel(niveles, grupo.getNivelSecundario()).grupos++;
            }
            if ("Cerrada".equals(grupo.getEstatusActa())) {
                actasCerradas++;
            }
        }

        for (Inscripcion inscripcion : inscripciones) {
            Grupo grupo = inscripcion.getGrupo();
            inscripcionesPorGrupo.computeIfAbsent(grupo.getId(), ignored -> new ArrayList<>()).add(inscripcion);
            String carrera = inscripcion.getAlumno().getCarrera() == null
                    ? "Sin carrera" : inscripcion.getAlumno().getCarrera().getNombre();
            String numeroControl = inscripcion.getAlumno().getNumeroControl();
            if (numeroControl != null) alumnos.add(numeroControl);
            agregarNivel(inscripcion, grupo.getNivel(), inscripcion.getCalificacionFinal(),
                    inscripcion.isAvancePendiente(), carrera, grupo, niveles, carreraNivel, totales);
            if (grupo.getNivelSecundario() != null) {
                agregarNivel(inscripcion, grupo.getNivelSecundario(), inscripcion.getCalificacionSecundaria(),
                        inscripcion.isAvancePendienteSecundario(), carrera, grupo, niveles, carreraNivel, totales);
            }
        }

        List<ReporteNivelDTO> porNivel = niveles.values().stream().map(AcumuladoNivel::aDto).toList();
        List<ReporteGrupoDTO> porGrupo = grupos.stream()
                .sorted(java.util.Comparator.comparing(Grupo::getClave).thenComparing(Grupo::getId))
                .map(grupo -> reporteGrupo(grupo, inscripcionesPorGrupo.getOrDefault(grupo.getId(), List.of())))
                .toList();
        List<ReporteLenguaMaternaDTO> alumnosLenguaMaterna =
                registroLenguaMaternaRepository.findByFechaRegistroBetweenOrderByFechaRegistroDescIdDesc(
                                periodo.getFechaInicio(), periodo.getFechaFin())
                        .stream()
                        .map(registro -> new ReporteLenguaMaternaDTO(
                                registro.getAlumno().getNumeroControl(),
                                registro.getAlumno().nombreCompleto(),
                                registro.getAlumno().getCarrera() == null
                                        ? "Sin carrera" : registro.getAlumno().getCarrera().getNombre(),
                                registro.getAlumno().getSemestre(),
                                registro.getAlumno().getSexo(),
                                registro.getAlumno().getEstatus(),
                                registro.getLengua().getNombre(),
                                registro.getNivelDominio(),
                                registro.getEstatus(),
                                registro.getFechaRegistro()))
                        .toList();
        return new ReporteAcademicoDTO(
                PeriodoDTO.desde(periodo),
                grupos.size(),
                actasCerradas,
                inscripciones.size(),
                alumnos.size(),
                totales.pendientes,
                promedio(totales.suma, totales.cantidad),
                totales.acreditados,
                totales.noAcreditados,
                porNivel,
                carreraNivel.values().stream().map(AcumuladoNivel::aDtoCarrera).toList(),
                porGrupo,
                alumnosLenguaMaterna);
    }

    private ReporteGrupoDTO reporteGrupo(Grupo grupo, List<Inscripcion> inscripciones) {
        String curso;
        if (grupo.isIntensivo()) {
            curso = "Intensivo · 10 niveles";
        } else if (grupo.getNivelSecundario() != null) {
            curso = grupo.getNivel().getNombre() + " + " + grupo.getNivelSecundario().getNombre();
        } else {
            curso = grupo.getNivel().getNombre();
        }

        List<ReporteAlumnoGrupoDTO> alumnos = inscripciones.stream()
                .sorted(java.util.Comparator.comparing(i -> i.getAlumno().nombreCompleto()))
                .map(inscripcion -> new ReporteAlumnoGrupoDTO(
                        inscripcion.getAlumno().getNumeroControl(),
                        inscripcion.getAlumno().nombreCompleto(),
                        inscripcion.getAlumno().getCarrera() == null
                                ? "Sin carrera" : inscripcion.getAlumno().getCarrera().getNombre(),
                        inscripcion.getAlumno().getSemestre(),
                        inscripcion.getAlumno().getSexo(),
                        inscripcion.getAlumno().getEstatus(),
                        inscripcion.getCalificacionFinal(),
                        resultado(inscripcion.getCalificacionFinal(), grupo.getEstatusActa(),
                                inscripcion.isAvancePendiente()),
                        grupo.getNivelSecundario() == null ? null : inscripcion.getCalificacionSecundaria(),
                        grupo.getNivelSecundario() == null ? null : resultado(
                                inscripcion.getCalificacionSecundaria(), grupo.getEstatusActa(),
                                inscripcion.isAvancePendienteSecundario())))
                .toList();
        List<String> horarios = grupo.getHorarios().stream()
                .sorted(java.util.Comparator.comparing(h -> h.getDiaSemana() * 10000
                        + h.getHoraInicio().toSecondOfDay() / 60))
                .map(h -> dia(h.getDiaSemana()) + " "
                        + h.getHoraInicio().toString() + "–" + h.getHoraFin())
                .toList();

        return new ReporteGrupoDTO(
                grupo.getId(),
                grupo.getClave(),
                curso,
                grupo.getModalidad().getNombre(),
                grupo.getAula().getNombre(),
                grupo.getFacilitador().nombreCompleto(),
                grupo.getCupo(),
                inscripciones.size(),
                grupo.getFechaInicioCaptura(),
                grupo.getFechaFinCaptura(),
                grupo.getEstatusActa(),
                grupo.getFechaCierreActa(),
                grupo.getCerradaPor(),
                grupo.getObservacionCierre(),
                horarios,
                alumnos);
    }

    private String resultado(Integer calificacion, String estatusActa, boolean avancePendiente) {
        if (!"Cerrada".equals(estatusActa) || calificacion == null) return "Pendiente de calificación";
        if (avancePendiente) return "Calificación publicada; avance pendiente por pago";
        return calificacion >= CALIFICACION_MINIMA_APROBATORIA ? "Acreditado" : "No acreditado";
    }

    private String dia(Integer numero) {
        return switch (numero) {
            case 1 -> "Lunes";
            case 2 -> "Martes";
            case 3 -> "Miércoles";
            case 4 -> "Jueves";
            case 5 -> "Viernes";
            case 6 -> "Sábado";
            case 7 -> "Domingo";
            default -> "Día " + numero;
        };
    }

    private Periodo obtenerPeriodoPredeterminado() {
        LocalDate hoy = LocalDate.now();
        return periodoRepository
                .findFirstByFechaInicioLessThanEqualAndFechaFinGreaterThanEqualOrderByFechaInicioDesc(hoy, hoy)
                .or(() -> periodoRepository.findAllByOrderByFechaInicioDesc().stream().findFirst())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "REP-001", "No hay periodos académicos disponibles para generar el reporte."));
    }

    private Periodo buscarPeriodo(Integer id) {
        return periodoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("REP-002", "No se encontró el periodo indicado."));
    }

    private void agregarNivel(Inscripcion inscripcion, Nivel asignatura, Integer calificacion,
            boolean avancePendiente, String carrera, Grupo grupo, Map<Integer, AcumuladoNivel> niveles,
            Map<String, AcumuladoNivel> carreraNivel, TotalesCalificacion totales) {
        AcumuladoNivel nivel = acumuladoNivel(niveles, asignatura);
        nivel.alumnosInscritos++;
        AcumuladoNivel porCarrera = carreraNivel.computeIfAbsent(
                carrera + "|" + String.format("%02d", asignatura.getNumero()),
                ignored -> new AcumuladoNivel(asignatura.getNumero(), asignatura.getNombre(), carrera));
        porCarrera.alumnosInscritos++;
        if (!"Cerrada".equals(grupo.getEstatusActa()) || calificacion == null) {
            totales.pendientes++;
            nivel.calificacionesPendientes++;
            porCarrera.calificacionesPendientes++;
            return;
        }
        totales.suma += calificacion;
        totales.cantidad++;
        nivel.sumaCalificaciones += calificacion;
        nivel.totalCalificaciones++;
        porCarrera.sumaCalificaciones += calificacion;
        porCarrera.totalCalificaciones++;
        if (calificacion >= CALIFICACION_MINIMA_APROBATORIA) {
            totales.acreditados++;
            nivel.acreditados++;
            porCarrera.acreditados++;
        } else {
            totales.noAcreditados++;
            nivel.noAcreditados++;
            porCarrera.noAcreditados++;
        }
    }

    private AcumuladoNivel acumuladoNivel(Map<Integer, AcumuladoNivel> niveles, Nivel asignatura) {
        Integer numero = asignatura.getNumero();
        return niveles.computeIfAbsent(numero,
                ignored -> new AcumuladoNivel(numero, asignatura.getNombre()));
    }

    private Double promedio(int suma, int cantidad) {
        return cantidad == 0 ? null : (double) suma / cantidad;
    }

    private static final class TotalesCalificacion {
        private int pendientes;
        private int suma;
        private int cantidad;
        private int acreditados;
        private int noAcreditados;
    }

    private static final class AcumuladoNivel {
        private final Integer numeroNivel;
        private final String nivel;
        private final String carrera;
        private int grupos;
        private int alumnosInscritos;
        private int calificacionesPendientes;
        private int sumaCalificaciones;
        private int totalCalificaciones;
        private int acreditados;
        private int noAcreditados;

        private AcumuladoNivel(Integer numeroNivel, String nivel) {
            this(numeroNivel, nivel, null);
        }

        private AcumuladoNivel(Integer numeroNivel, String nivel, String carrera) {
            this.numeroNivel = numeroNivel;
            this.nivel = nivel;
            this.carrera = carrera;
        }

        private Double promedio() {
            return totalCalificaciones == 0 ? null : (double) sumaCalificaciones / totalCalificaciones;
        }

        private ReporteCarreraNivelDTO aDtoCarrera() {
            return new ReporteCarreraNivelDTO(carrera, numeroNivel, nivel, alumnosInscritos,
                    calificacionesPendientes, promedio(), acreditados, noAcreditados);
        }

        private ReporteNivelDTO aDto() {
            return new ReporteNivelDTO(numeroNivel, nivel, grupos, alumnosInscritos,
                    calificacionesPendientes, promedio(), acreditados, noAcreditados);
        }
    }
}
