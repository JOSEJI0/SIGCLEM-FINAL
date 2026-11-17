package mx.edu.itch.sigclem.reportes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
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
import mx.edu.itch.sigclem.catalogos.domain.Modalidad;
import mx.edu.itch.sigclem.catalogos.domain.Aula;
import mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.facilitadores.domain.Facilitador;
import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.grupos.domain.Inscripcion;
import mx.edu.itch.sigclem.grupos.repository.GrupoRepository;
import mx.edu.itch.sigclem.grupos.repository.InscripcionRepository;
import mx.edu.itch.sigclem.lenguasmaternas.domain.LenguaMaterna;
import mx.edu.itch.sigclem.lenguasmaternas.domain.RegistroLenguaMaterna;
import mx.edu.itch.sigclem.lenguasmaternas.repository.RegistroLenguaMaternaRepository;

@ExtendWith(MockitoExtension.class)
class ReporteAcademicoServiceTest {

    @Mock
    private PeriodoRepository periodoRepository;
    @Mock
    private GrupoRepository grupoRepository;
    @Mock
    private InscripcionRepository inscripcionRepository;
    @Mock
    private RegistroLenguaMaternaRepository registroLenguaMaternaRepository;
    @InjectMocks
    private ReporteAcademicoService service;

    @org.junit.jupiter.api.BeforeEach
    void prepararRegistrosLenguaMaternaVacios() {
        lenient().when(registroLenguaMaternaRepository
                .findByFechaRegistroBetweenOrderByFechaRegistroDescIdDesc(any(), any()))
                .thenReturn(List.of());
    }

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
        assertThat(reporte.porGrupo()).hasSize(3);
        assertThat(reporte.porGrupo()).filteredOn(grupo -> "Cerrada".equals(grupo.estatusActa()))
                .filteredOn(grupo -> grupo.alumnosInscritos() == 2).singleElement().satisfies(grupo -> {
                    assertThat(grupo.modalidad()).isEqualTo("Presencial");
                    assertThat(grupo.facilitador()).isEqualTo("Luis Pérez");
                    assertThat(grupo.alumnos()).hasSize(2);
                    assertThat(grupo.alumnos()).extracting("resultado")
                            .containsExactlyInAnyOrder("Acreditado", "No acreditado");
                });
        assertThat(reporte.alumnosLenguaMaterna()).isEmpty();
    }

    @Test
    void incluyeAlumnosConRegistroDeLenguaMaternaEnElPeriodoAunqueNoTenganGrupo() {
        Periodo periodo = periodo(1, "Enero-Junio 2026");
        var carrera = mock(mx.edu.itch.sigclem.catalogos.domain.Carrera.class);
        when(carrera.getNombre()).thenReturn("Sistemas");
        var alumno = new mx.edu.itch.sigclem.alumnos.domain.Alumno();
        alumno.setNumeroControl("A12345678");
        alumno.setNombre("Ana");
        alumno.setApellidoPaterno("López");
        alumno.setCarrera(carrera);
        alumno.setSemestre(4);
        alumno.setSexo("M");
        alumno.setEstatus("Activo");
        var lengua = new LenguaMaterna();
        lengua.setNombre("Náhuatl");
        var registro = new RegistroLenguaMaterna();
        registro.setAlumno(alumno);
        registro.setLengua(lengua);
        registro.setNivelDominio("Avanzado");
        registro.setEstatus("Validado");
        registro.setFechaRegistro(LocalDate.of(2026, 3, 10));
        when(periodoRepository.findById(1)).thenReturn(Optional.of(periodo));
        when(grupoRepository.findByPeriodoId(1)).thenReturn(List.of());
        when(inscripcionRepository.findByGrupo_Periodo_Id(1)).thenReturn(List.of());
        when(registroLenguaMaternaRepository
                .findByFechaRegistroBetweenOrderByFechaRegistroDescIdDesc(
                        periodo.getFechaInicio(), periodo.getFechaFin()))
                .thenReturn(List.of(registro));

        var reporte = service.obtenerReporte(1);

        assertThat(reporte.alumnosLenguaMaterna()).singleElement().satisfies(estudiante -> {
            assertThat(estudiante.numeroControl()).isEqualTo("A12345678");
            assertThat(estudiante.nombreCompleto()).isEqualTo("Ana López");
            assertThat(estudiante.carrera()).isEqualTo("Sistemas");
            assertThat(estudiante.lengua()).isEqualTo("Náhuatl");
            assertThat(estudiante.estatusRegistro()).isEqualTo("Validado");
        });
        assertThat(reporte.alumnosInscritos()).isZero();
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
        grupo.setId(nivel.getNumero() * 10 + ("Cerrada".equals(estatusActa) ? 1 : 2));
        grupo.setClave("A");
        grupo.setPeriodo(periodo);
        grupo.setNivel(nivel);
        grupo.setEstatusActa(estatusActa);
        Modalidad modalidad = new Modalidad();
        modalidad.setNombre("Presencial");
        grupo.setModalidad(modalidad);
        Aula aula = new Aula();
        aula.setNombre("Aula 1");
        grupo.setAula(aula);
        Facilitador facilitador = new Facilitador();
        facilitador.setNombre("Luis");
        facilitador.setApellidoPaterno("Pérez");
        grupo.setFacilitador(facilitador);
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
