package mx.edu.itch.sigclem.diagnosticos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.comun.excepcion.ConflictoException;
import mx.edu.itch.sigclem.diagnosticos.domain.EvaluacionDiagnostica;
import mx.edu.itch.sigclem.diagnosticos.dto.ProgramarDiagnosticoRequest;
import mx.edu.itch.sigclem.diagnosticos.dto.ResultadoDiagnosticoRequest;
import mx.edu.itch.sigclem.diagnosticos.repository.EvaluacionDiagnosticaRepository;
import mx.edu.itch.sigclem.notificaciones.domain.Notificacion;
import mx.edu.itch.sigclem.notificaciones.repository.NotificacionRepository;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class EvaluacionDiagnosticaServiceTest {

    @Mock
    private EvaluacionDiagnosticaRepository diagnosticoRepository;
    @Mock
    private AlumnoRepository alumnoRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private NotificacionRepository notificacionRepository;
    @InjectMocks
    private EvaluacionDiagnosticaService service;

    @Test
    void solicitaDiagnosticoYRechazaOtraSolicitudActiva() {
        Usuario usuario = usuario("20260001", 1);
        Alumno alumno = alumno("20260001", usuario);
        when(usuarioRepository.findByNombreUsuario("20260001")).thenReturn(Optional.of(usuario));
        when(alumnoRepository.findByUsuarioId(1)).thenReturn(Optional.of(alumno));
        when(diagnosticoRepository.existsByAlumnoNumeroControlAndEstatusIn(any(), any())).thenReturn(false);
        when(diagnosticoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        Usuario coordinador = usuario("coordinador", 2);
        when(usuarioRepository.findFirstByRolNombreAndActivoTrueOrderByIdAsc("Coordinador"))
                .thenReturn(Optional.of(coordinador));
        when(notificacionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var creada = service.solicitar("20260001");

        assertThat(creada.estatus()).isEqualTo("Solicitada");
        assertThat(creada.numeroControl()).isEqualTo("20260001");
        ArgumentCaptor<Notificacion> notificacion = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository).save(notificacion.capture());
        assertThat(notificacion.getValue().getDestino()).isSameAs(coordinador);
        assertThat(notificacion.getValue().getDiagnostico()).isNotNull();
        assertThat(notificacion.getValue().isLeida()).isFalse();
        when(diagnosticoRepository.existsByAlumnoNumeroControlAndEstatusIn(any(), any())).thenReturn(true);
        assertThatThrownBy(() -> service.solicitar("20260001"))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    void programaYRegistraResultadoDeUnaEvaluacion() {
        Usuario estudiante = usuario("20260001", 1);
        Alumno alumno = alumno("20260001", estudiante);
        EvaluacionDiagnostica diagnostico = new EvaluacionDiagnostica();
        diagnostico.setId(3);
        diagnostico.setAlumno(alumno);
        diagnostico.setEstatus("Solicitada");
        Usuario coordinador = usuario("coordinacion", 2);
        when(diagnosticoRepository.findConAlumnoById(3)).thenReturn(Optional.of(diagnostico));
        when(usuarioRepository.findByNombreUsuario("coordinacion")).thenReturn(Optional.of(coordinador));
        when(diagnosticoRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(notificacionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var programada = service.programar(3,
                new ProgramarDiagnosticoRequest(LocalDateTime.now().plusDays(1), "https://meet.example.org"), "coordinacion");
        var concluida = service.registrarResultado(3,
                new ResultadoDiagnosticoRequest(85, "Colocación", "Buen desempeño."));

        assertThat(programada.estatus()).isEqualTo("Programada");
        assertThat(programada.enlace()).isEqualTo("https://meet.example.org");
        assertThat(concluida.estatus()).isEqualTo("Concluida");
        assertThat(concluida.puntaje()).isEqualTo(85);
        assertThat(concluida.recomendacion()).isEqualTo("Colocación");
        ArgumentCaptor<Notificacion> notificaciones = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository, org.mockito.Mockito.times(2)).save(notificaciones.capture());
        assertThat(notificaciones.getAllValues())
                .allSatisfy(notificacion -> {
                    assertThat(notificacion.getDestino()).isSameAs(estudiante);
                    assertThat(notificacion.getDiagnostico()).isSameAs(diagnostico);
                    assertThat(notificacion.isLeida()).isFalse();
                });
        assertThat(notificaciones.getAllValues().get(0).getMensaje()).contains("meet.example.org");
        assertThat(notificaciones.getAllValues().get(1).getMensaje()).contains("Colocación");
    }

    @Test
    void validaQueElEnlaceDeProgramacionSeaHttpOS() {
        assertThat(new ProgramarDiagnosticoRequest(
                LocalDateTime.now().plusDays(1), "https://meet.example.org").isEnlaceValido()).isTrue();
        assertThat(new ProgramarDiagnosticoRequest(
                LocalDateTime.now().plusDays(1), "javascript:alert(1)").isEnlaceValido()).isFalse();
    }

    private Usuario usuario(String nombre, Integer id) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombreUsuario(nombre);
        return usuario;
    }

    private Alumno alumno(String numeroControl, Usuario usuario) {
        Alumno alumno = new Alumno();
        alumno.setNumeroControl(numeroControl);
        alumno.setUsuario(usuario);
        alumno.setNombre("Ana");
        alumno.setApellidoPaterno("López");
        return alumno;
    }
}
