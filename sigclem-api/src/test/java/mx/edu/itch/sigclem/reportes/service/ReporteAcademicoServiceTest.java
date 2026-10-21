package mx.edu.itch.sigclem.reportes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.domain.Periodo;
import mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.grupos.domain.Inscripcion;
import mx.edu.itch.sigclem.grupos.repository.GrupoRepository;
import mx.edu.itch.sigclem.grupos.repository.InscripcionRepository;

@ExtendWith(MockitoExtension.class)
class ReporteAcademicoServiceTest {

    @Mock
    private PeriodoRepository periodoRepository;
    @Mock
    private GrupoRepository grupoRepository;
    @Mock
    private InscripcionRepository inscripcionRepository;
    @InjectMocks
    private ReporteAcademicoService service;

    @Test
    void resumeGruposInscripcionesYCalificacionesPublicadasPorNivel() {
        Periodo periodo = periodo(1, "Enero-Junio 2026");
        Nivel nivelBasico1 = nivel(1, "Básico 1");
        Grupo cerrado = grupo(periodo, nivelBasico1, "Cerrada");
        Grupo abierto = grupo(periodo, nivelBasico1, "En captura");
        Grupo sinAlumnos = grupo(periodo, nivel(2, "Básico 2"), "Cerrada");
        when(periodoRepository.findById(1)).thenReturn(Optional.of(periodo));
        when(grupoRepository.findByPeriodoId(1)).thenReturn(List.of(cerrado, abierto, sinAlumnos));
        when(inscripcionRepository.findByGrupo_Periodo_Id(1)).thenReturn(List.of(
                inscripcion("alumno-1", cerrado, 90),
                inscripcion("alumno-2", cerrado, 60),
                inscripcion("alumno-1", abierto, 100)));

        var reporte = service.obtenerReporte(1);

        assertThat(reporte.grupos()).isEqualTo(3);
        assertThat(reporte.actasCerradas()).isEqualTo(2);
        assertThat(reporte.alumnosInscritos()).isEqualTo(3);
        assertThat(reporte.alumnosUnicos()).isEqualTo(2);
        assertThat(reporte.calificacionesPendientes()).isEqualTo(1);
        assertThat(reporte.promedioPublicado()).isEqualTo(75.0);
        assertThat(reporte.acreditados()).isEqualTo(1);
        assertThat(reporte.noAcreditados()).isEqualTo(1);
        assertThat(reporte.porNivel()).hasSize(2);
        // RF-18: desglose por carrera y nivel (los alumnos de prueba no tienen carrera asignada).
        assertThat(reporte.porCarreraYNivel()).singleElement().satisfies(fila -> {
            assertThat(fila.carrera()).isEqualTo("Sin carrera");
            assertThat(fila.nivel()).isEqualTo("Básico 1");
            assertThat(fila.alumnosInscritos()).isEqualTo(3);
            assertThat(fila.acreditados()).isEqualTo(1);
            assertThat(fila.noAcreditados()).isEqualTo(1);
            assertThat(fila.calificacionesPendientes()).isEqualTo(1);
        });
    }

    @Test
    void usaPeriodoActualYSiNoExisteSeleccionaElMasReciente() {
        Periodo reciente = periodo(2, "Agosto-Diciembre 2026");
        when(periodoRepository
                .findFirstByFechaInicioLessThanEqualAndFechaFinGreaterThanEqualOrderByFechaInicioDesc(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.empty());
        when(periodoRepository.findAllByOrderByFechaInicioDesc()).thenReturn(List.of(reciente));
        when(grupoRepository.findByPeriodoId(2)).thenReturn(List.of());
        when(inscripcionRepository.findByGrupo_Periodo_Id(2)).thenReturn(List.of());

        assertThat(service.obtenerReporte(null).periodo().id()).isEqualTo(2);
    }

    @Test
    void rechazaPeriodoInexistente() {
        when(periodoRepository.findById(77)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.obtenerReporte(77))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private Periodo periodo(Integer id, String nombre) {
        Periodo periodo = new Periodo();
        periodo.setId(id);
        periodo.setNombre(nombre);
        periodo.setFechaInicio(LocalDate.of(2026, 1, 1));
        periodo.setFechaFin(LocalDate.of(2026, 6, 30));
        return periodo;
    }

    private Nivel nivel(Integer numero, String nombre) {
        Nivel nivel = mock(Nivel.class);
        when(nivel.getNumero()).thenReturn(numero);
        when(nivel.getNombre()).thenReturn(nombre);
        return nivel;
    }

    private Grupo grupo(Periodo periodo, Nivel nivel, String estatusActa) {
        Grupo grupo = new Grupo();
        grupo.setPeriodo(periodo);
        grupo.setNivel(nivel);
        grupo.setEstatusActa(estatusActa);
        return grupo;
    }

    private Inscripcion inscripcion(String numeroControl, Grupo grupo, Integer calificacion) {
        var alumno = new mx.edu.itch.sigclem.alumnos.domain.Alumno();
        alumno.setNumeroControl(numeroControl);
        Inscripcion inscripcion = new Inscripcion();
        inscripcion.setAlumno(alumno);
        inscripcion.setGrupo(grupo);
        inscripcion.setCalificacionFinal(calificacion);
        return inscripcion;
    }
}
