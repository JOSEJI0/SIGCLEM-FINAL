package mx.edu.itch.sigclem.examenes.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.domain.AvanceNivel;
import mx.edu.itch.sigclem.alumnos.domain.EstatusAvance;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository;
import mx.edu.itch.sigclem.catalogos.domain.Carrera;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.examenes.domain.Examen;
import mx.edu.itch.sigclem.examenes.dto.CapturaResultadoRequest;
import mx.edu.itch.sigclem.examenes.repository.CriterioEvaluacionRepository;
import mx.edu.itch.sigclem.examenes.repository.ExamenRepository;
import mx.edu.itch.sigclem.examenes.repository.ResultadoCriterioRepository;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

/** Resultado de examen con asignación directa de nivel (sin rúbrica, sin tope de niveles y corregible). */
@ExtendWith(MockitoExtension.class)
class ExamenServiceResultadoTest {

    @Mock
    private ExamenRepository examenRepository;
    @Mock
    private ResultadoCriterioRepository resultadoCriterioRepository;
    @Mock
    private CriterioEvaluacionRepository criterioRepository;
    @Mock
    private AlumnoRepository alumnoRepository;
    @Mock
    private AvanceNivelRepository avanceRepository;
    @Mock
    private NivelRepository nivelRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @InjectMocks
    private ExamenService service;

    private final List<Nivel> niveles = new ArrayList<>();

    @Test
    void colocacionPermiteAsignarCualquierNivelSinTopeDeTres() {
        Map<Integer, EstatusAvance> avance = capturar("Colocación", 9, inicial());

        for (int n = 1; n <= 8; n++) {
            assertThat(avance.get(n)).as("nivel %d", n).isEqualTo(EstatusAvance.EXENTO);
        }
        assertThat(avance.get(9)).isEqualTo(EstatusAvance.HABILITADO);
        assertThat(avance.get(10)).isEqualTo(EstatusAvance.NO_PERMITIDO);
    }

    @Test
    void cuatroHabilidadesAcreditaHastaElNivelAsignadoYPuedeQuedarEnB1() {
        Map<Integer, EstatusAvance> avance = capturar("Cuatro habilidades", 10, inicial());

        assertThat(avance.values()).containsOnly(EstatusAvance.EXENTO);
    }

    @Test
    void corregirAUnNivelMenorRetiraLasExencionesSobrantesYHabilitaElSiguiente() {
        // Resultado anterior: colocación en Básico 4 (1 a 3 exentos).
        List<AvanceNivel> previo = inicial();
        for (AvanceNivel a : previo) {
            int n = a.getId().getIdNivel();
            a.setEstatus(n < 4 ? EstatusAvance.EXENTO : n == 4 ? EstatusAvance.HABILITADO : EstatusAvance.NO_PERMITIDO);
        }

        Map<Integer, EstatusAvance> avance = capturar("Colocación", 2, previo);

        assertThat(avance.get(1)).isEqualTo(EstatusAvance.EXENTO);
        assertThat(avance.get(2)).isEqualTo(EstatusAvance.HABILITADO);
        assertThat(avance.get(3)).isEqualTo(EstatusAvance.NO_PERMITIDO);
        assertThat(avance.get(4)).isEqualTo(EstatusAvance.NO_PERMITIDO);
    }

    @Test
    void conservaLosNivelesAcreditadosPorCursoYHabilitaElPrimeroPendiente() {
        // Acreditó Básico 1 a 3 por curso; un examen que asigna Básico 2 no debe dejarlo sin nivel por cursar.
        List<AvanceNivel> previo = inicial();
        for (AvanceNivel a : previo) {
            int n = a.getId().getIdNivel();
            a.setEstatus(n <= 3 ? EstatusAvance.ACREDITADO : n == 4 ? EstatusAvance.HABILITADO : EstatusAvance.NO_PERMITIDO);
        }

        Map<Integer, EstatusAvance> avance = capturar("Colocación", 2, previo);

        assertThat(avance.get(3)).isEqualTo(EstatusAvance.ACREDITADO);
        assertThat(avance.get(4)).isEqualTo(EstatusAvance.HABILITADO);
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, EstatusAvance> capturar(String tipo, int nivelAsignado, List<AvanceNivel> avancePrevio) {
        for (int n = 1; n <= 10; n++) {
            Nivel nivel = mock(Nivel.class);
            lenient().when(nivel.getId()).thenReturn(n);
            lenient().when(nivel.getNumero()).thenReturn(n);
            lenient().when(nivel.getNombre()).thenReturn("Nivel " + n);
            niveles.add(nivel);
        }
        Carrera carrera = mock(Carrera.class);
        Alumno alumno = new Alumno();
        alumno.setNumeroControl("22520001");
        alumno.setNombre("Ana");
        alumno.setApellidoPaterno("López");
        alumno.setCarrera(carrera);
        Examen examen = new Examen();
        examen.setId(7);
        examen.setAlumno(alumno);
        examen.setTipo(tipo);

        when(examenRepository.findConDetallesById(7)).thenReturn(Optional.of(examen));
        when(nivelRepository.findById(nivelAsignado)).thenReturn(Optional.of(niveles.get(nivelAsignado - 1)));
        when(nivelRepository.findAllByOrderByNumeroAsc()).thenReturn(niveles);
        when(avanceRepository.findByIdNumeroControl("22520001")).thenReturn(avancePrevio);
        when(examenRepository.save(examen)).thenReturn(examen);

        var dto = service.capturarResultado(7, new CapturaResultadoRequest(nivelAsignado, 80, null), "coordinador");

        assertThat(dto.concluido()).isTrue();
        assertThat(dto.puntajeTotal()).isEqualTo(80);
        ArgumentCaptor<Collection<AvanceNivel>> guardado = ArgumentCaptor.forClass(Collection.class);
        verify(avanceRepository).saveAll(guardado.capture());
        return guardado.getValue().stream()
                .collect(Collectors.toMap(a -> a.getId().getIdNivel(), AvanceNivel::getEstatus));
    }

    private static List<AvanceNivel> inicial() {
        List<AvanceNivel> avance = new ArrayList<>();
        for (int n = 1; n <= 10; n++) {
            avance.add(new AvanceNivel("22520001", n, n == 1 ? EstatusAvance.HABILITADO : EstatusAvance.NO_PERMITIDO));
        }
        return avance;
    }
}
