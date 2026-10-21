package mx.edu.itch.sigclem.examenes.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.examenes.dto.SolicitudExamenRequest;
import mx.edu.itch.sigclem.examenes.repository.CriterioEvaluacionRepository;
import mx.edu.itch.sigclem.examenes.repository.ExamenRepository;
import mx.edu.itch.sigclem.examenes.repository.ResultadoCriterioRepository;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class ExamenServiceSecurityTest {

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

    @Test
    void rechazaSolicitudParaUnAlumnoDistintoAlAutenticadoAntesDeConsultarDatos() {
        SolicitudExamenRequest solicitud = new SolicitudExamenRequest(
                "alumno-ajeno", "Colocación", null);

        assertThatThrownBy(() -> service.solicitar(solicitud, "alumno-propio"))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(examenRepository, resultadoCriterioRepository, criterioRepository,
                alumnoRepository, avanceRepository, nivelRepository, usuarioRepository);
    }
}
