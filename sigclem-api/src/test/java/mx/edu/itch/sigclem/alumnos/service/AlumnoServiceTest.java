package mx.edu.itch.sigclem.alumnos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;

import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.domain.AvanceNivel;
import mx.edu.itch.sigclem.alumnos.domain.EstatusAvance;
import mx.edu.itch.sigclem.alumnos.dto.AlumnoResumenDTO;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository;
import mx.edu.itch.sigclem.catalogos.domain.Carrera;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.repository.CarreraRepository;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.grupos.repository.InscripcionRepository;
import mx.edu.itch.sigclem.seguridad.repository.RolRepository;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class AlumnoServiceTest {

    @Mock
    private AlumnoRepository alumnoRepository;
    @Mock
    private AvanceNivelRepository avanceRepository;
    @Mock
    private CarreraRepository carreraRepository;
    @Mock
    private NivelRepository nivelRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RolRepository rolRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private InscripcionRepository inscripcionRepository;
    @Mock
    private mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository periodoRepository;
    @InjectMocks
    private AlumnoService service;

    private Alumno alumno;

    @BeforeEach
    void preparar() {
        Carrera carrera = mock(Carrera.class);
        lenient().when(carrera.getNombre()).thenReturn("Sistemas");
        alumno = new Alumno();
        alumno.setNumeroControl("A12345678");
        alumno.setNombre("Ana");
        alumno.setApellidoPaterno("López");
        alumno.setApellidoMaterno("García");
        alumno.setCarrera(carrera);
        alumno.setSemestre(4);
        alumno.setCorreo("ana@example.edu.mx");
        alumno.setFechaInscripcion(LocalDate.of(2026, 1, 15));
        // Básico 1 acreditado: su nivel actual es Básico 2.
        List<Nivel> niveles = List.of(nivel(1, "Básico 1"), nivel(2, "Básico 2"), nivel(3, "Básico 3"));
        lenient().when(nivelRepository.findAllByOrderByNumeroAsc()).thenReturn(niveles);
        lenient().when(avanceRepository.findByIdNumeroControlIn(any())).thenReturn(List.of(
                new AvanceNivel("A12345678", 1, EstatusAvance.ACREDITADO),
                new AvanceNivel("A12345678", 2, EstatusAvance.HABILITADO),
                new AvanceNivel("A12345678", 3, EstatusAvance.NO_PERMITIDO)));
    }

    @Test
    void buscarRecortaLosFiltrosYDevuelveResumenConNivelActual() {
        when(alumnoRepository.filtrar("", 3, "A123", "Ana López")).thenReturn(List.of(alumno));

        var resultado = service.buscar(null, 3, " A123 ", " Ana López ", null, null, null, PageRequest.of(0, 20));

        assertEquals(1, resultado.getTotalElements());
        assertEquals(new AlumnoResumenDTO("A12345678", "Ana López García", "Sistemas", 4, null, null, "ana@example.edu.mx",
                LocalDate.of(2026, 1, 15), 2, "Básico 2", false, "Activo"), resultado.getContent().getFirst());
        verify(alumnoRepository).filtrar("", 3, "A123", "Ana López");
    }

    @Test
    void filtraPorElNivelQueCursaActualmente() {
        when(alumnoRepository.filtrar("", null, "", "")).thenReturn(List.of(alumno));

        assertEquals(1, service.buscar(null, null, null, null, 2, null, null, PageRequest.of(0, 20)).getTotalElements());
        assertEquals(0, service.buscar(null, null, null, null, 3, null, null, PageRequest.of(0, 20)).getTotalElements());
    }

    @Test
    void elAlumnoActualizaSuSemestreYQuedaRegistradaLaFecha() {
        when(alumnoRepository.findConCarreraByNumeroControl("A12345678")).thenReturn(java.util.Optional.of(alumno));

        var expediente = service.actualizarSemestre("A12345678", 6);

        assertEquals(6, expediente.semestre());
        assertEquals(LocalDate.now(), expediente.fechaActualizacionSemestre());
        assertEquals(false, expediente.semestrePorConfirmar());
        verify(alumnoRepository).save(alumno);
    }

    @Test
    void elSemestreDebeEstarEntreUnoYDoce() {
        org.junit.jupiter.api.Assertions.assertThrows(
                mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException.class,
                () -> service.actualizarSemestre("A12345678", 13));
    }

    @Test
    void buscarLimitaElTamanoMaximoSolicitado() {
        when(alumnoRepository.filtrar("", null, "", "")).thenReturn(List.of(alumno));

        var resultado = service.buscar(null, null, null, null, null, null, null, PageRequest.of(0, 500));

        assertEquals(100, resultado.getSize());
    }

    @Test
    void darDeBajaDesactivaLaCuentaYRegistraElMotivo() {
        var usuario = new mx.edu.itch.sigclem.seguridad.domain.Usuario();
        usuario.setActivo(true);
        alumno.setUsuario(usuario);
        when(alumnoRepository.findConCarreraByNumeroControl("A12345678")).thenReturn(java.util.Optional.of(alumno));
        when(avanceRepository.findByIdNumeroControl("A12345678")).thenReturn(List.of(
                new AvanceNivel("A12345678", 1, EstatusAvance.ACREDITADO)));

        var exp = service.darDeBaja("A12345678", "Abandonó el curso");

        assertEquals("Baja", exp.estatus());
        assertEquals("Abandonó el curso", exp.motivoBaja());
        org.junit.jupiter.api.Assertions.assertFalse(usuario.isActivo());
        verify(alumnoRepository).save(alumno);
    }

    @Test
    void liberarIdiomaDejaLosNivelesExentosConLaFormaYAcreditaElIdioma() {
        when(alumnoRepository.findConCarreraByNumeroControl("A12345678")).thenReturn(java.util.Optional.of(alumno));
        var avance = new java.util.ArrayList<AvanceNivel>(List.of(
                new AvanceNivel("A12345678", 1, EstatusAvance.ACREDITADO),
                new AvanceNivel("A12345678", 2, EstatusAvance.HABILITADO),
                new AvanceNivel("A12345678", 3, EstatusAvance.NO_PERMITIDO)));
        when(avanceRepository.findByIdNumeroControl("A12345678")).thenReturn(avance);

        var exp = service.liberarIdioma("A12345678", "Examen global", "Folio 123");

        assertEquals("Examen global", exp.formaLiberacion());
        assertEquals("Idioma acreditado", exp.nivelActual());
        assertEquals(EstatusAvance.EXENTO, avance.get(1).getEstatus());
        assertEquals("Examen global", avance.get(1).getOrigen());
    }

    private static Nivel nivel(int numero, String nombre) {
        Nivel nivel = mock(Nivel.class);
        lenient().when(nivel.getId()).thenReturn(numero);
        lenient().when(nivel.getNumero()).thenReturn(numero);
        lenient().when(nivel.getNombre()).thenReturn(nombre);
        return nivel;
    }
}
