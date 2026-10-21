package mx.edu.itch.sigclem.reportes.service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.catalogos.domain.Periodo;
import mx.edu.itch.sigclem.catalogos.dto.PeriodoDTO;
import mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.grupos.domain.Inscripcion;
import mx.edu.itch.sigclem.grupos.repository.GrupoRepository;
import mx.edu.itch.sigclem.grupos.repository.InscripcionRepository;
import mx.edu.itch.sigclem.reportes.dto.ReporteAcademicoDTO;
import mx.edu.itch.sigclem.reportes.dto.ReporteCarreraNivelDTO;
import mx.edu.itch.sigclem.reportes.dto.ReporteNivelDTO;

@Service
@RequiredArgsConstructor
public class ReporteAcademicoService {

    private static final int CALIFICACION_MINIMA_APROBATORIA = 70;

    private final PeriodoRepository periodoRepository;
    private final GrupoRepository grupoRepository;
    private final InscripcionRepository inscripcionRepository;

    @Transactional(readOnly = true)
    public ReporteAcademicoDTO obtenerReporte(Integer periodoId) {
        Periodo periodo = periodoId == null ? obtenerPeriodoPredeterminado() : buscarPeriodo(periodoId);
        List<Grupo> grupos = grupoRepository.findByPeriodoId(periodo.getId());
        List<Inscripcion> inscripciones = inscripcionRepository.findByGrupo_Periodo_Id(periodo.getId());
        Map<Integer, AcumuladoNivel> niveles = new TreeMap<>();
        // RF-18: clave "carrera|número de nivel" ordenada por carrera y luego por nivel.
        Map<String, AcumuladoNivel> carreraNivel = new TreeMap<>();
        Set<String> alumnos = new HashSet<>();
        int actasCerradas = 0;
        int pendientes = 0;
        int acreditados = 0;
        int noAcreditados = 0;
        int sumaCalificaciones = 0;
        int totalCalificaciones = 0;

        for (Grupo grupo : grupos) {
            AcumuladoNivel nivel = acumuladoNivel(niveles, grupo);
            nivel.grupos++;
            if ("Cerrada".equals(grupo.getEstatusActa())) {
                actasCerradas++;
            }
        }

        for (Inscripcion inscripcion : inscripciones) {
            Grupo grupo = inscripcion.getGrupo();
            AcumuladoNivel nivel = acumuladoNivel(niveles, grupo);
            nivel.alumnosInscritos++;
            String carrera = inscripcion.getAlumno().getCarrera() == null
                    ? "Sin carrera" : inscripcion.getAlumno().getCarrera().getNombre();
            AcumuladoNivel porCarrera = carreraNivel.computeIfAbsent(
                    carrera + "|" + String.format("%02d", grupo.getNivel().getNumero()),
                    ignored -> new AcumuladoNivel(grupo.getNivel().getNumero(), grupo.getNivel().getNombre(), carrera));
            porCarrera.alumnosInscritos++;
            String numeroControl = inscripcion.getAlumno().getNumeroControl();
            if (numeroControl != null) alumnos.add(numeroControl);

            Integer calificacion = inscripcion.getCalificacionFinal();
            if (!"Cerrada".equals(grupo.getEstatusActa()) || calificacion == null) {
                pendientes++;
                nivel.calificacionesPendientes++;
                porCarrera.calificacionesPendientes++;
                continue;
            }
            sumaCalificaciones += calificacion;
            totalCalificaciones++;
            nivel.sumaCalificaciones += calificacion;
            nivel.totalCalificaciones++;
            porCarrera.sumaCalificaciones += calificacion;
            porCarrera.totalCalificaciones++;
            if (calificacion >= CALIFICACION_MINIMA_APROBATORIA) {
                acreditados++;
                nivel.acreditados++;
                porCarrera.acreditados++;
            } else {
                noAcreditados++;
                nivel.noAcreditados++;
                porCarrera.noAcreditados++;
            }
        }

        List<ReporteNivelDTO> porNivel = niveles.values().stream().map(AcumuladoNivel::aDto).toList();
        return new ReporteAcademicoDTO(
                PeriodoDTO.desde(periodo),
                grupos.size(),
                actasCerradas,
                inscripciones.size(),
                alumnos.size(),
                pendientes,
                promedio(sumaCalificaciones, totalCalificaciones),
                acreditados,
                noAcreditados,
                porNivel,
                carreraNivel.values().stream().map(AcumuladoNivel::aDtoCarrera).toList());
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

    private AcumuladoNivel acumuladoNivel(Map<Integer, AcumuladoNivel> niveles, Grupo grupo) {
        Integer numero = grupo.getNivel().getNumero();
        return niveles.computeIfAbsent(numero,
                ignored -> new AcumuladoNivel(numero, grupo.getNivel().getNombre()));
    }

    private Double promedio(int suma, int cantidad) {
        return cantidad == 0 ? null : (double) suma / cantidad;
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
