package mx.edu.itch.sigclem.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;
import mx.edu.itch.sigclem.seguridad.service.CuentaService;

@ExtendWith(MockitoExtension.class)
class CuentaServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AlumnoRepository alumnoRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private CuentaService service;

    @BeforeEach
    void preparar() {
        service = new CuentaService(usuarioRepository, alumnoRepository, encoder);
    }

    @Test
    void elCoordinadorCambiaSuContrasenaConLaActual() {
        Usuario coordinador = new Usuario("coordinador", encoder.encode("Inicial2026"), null);
        when(usuarioRepository.findByNombreUsuario("coordinador")).thenReturn(Optional.of(coordinador));

        service.cambiarPropia("coordinador", "Inicial2026", "Nueva2027x");

        assertTrue(encoder.matches("Nueva2027x", coordinador.getContrasenaHash()));
        verify(usuarioRepository).save(coordinador);
    }

    @Test
    void elAlumnoPuedeCambiarSuContrasenaInicialUsandoLaActual() {
        Usuario alumno = new Usuario("22520187", encoder.encode("22520187"), null);
        when(usuarioRepository.findByNombreUsuario("22520187")).thenReturn(Optional.of(alumno));

        service.cambiarPropia("22520187", "22520187", "Nueva2027x");

        assertTrue(encoder.matches("Nueva2027x", alumno.getContrasenaHash()));
        verify(usuarioRepository).save(alumno);
    }

    @Test
    void rechazaContrasenaActualIncorrectaODebil() {
        Usuario coordinador = new Usuario("coordinador", encoder.encode("Inicial2026"), null);
        when(usuarioRepository.findByNombreUsuario("coordinador")).thenReturn(Optional.of(coordinador));

        var e = assertThrows(DatosInvalidosException.class, () -> service.cambiarPropia("coordinador", "otra", "Nueva2027x"));
        assertEquals("CTA-002", e.getCodigo());
        assertThrows(DatosInvalidosException.class, () -> service.cambiarPropia("coordinador", "Inicial2026", "solotexto"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void asignaUnaNuevaContrasenaAlAlumno() {
        Usuario cuenta = new Usuario("22520187", encoder.encode("Olvidada1"), null);
        Alumno alumno = new Alumno();
        alumno.setUsuario(cuenta);
        when(alumnoRepository.findById("22520187")).thenReturn(Optional.of(alumno));

        service.asignarAlumno(" 22520187 ", "Temporal2026");

        assertTrue(encoder.matches("Temporal2026", cuenta.getContrasenaHash()));
    }

    @Test
    void permiteAsignarElNumeroDeControlComoContrasenaDelAlumno() {
        Usuario cuenta = new Usuario("22520187", encoder.encode("Olvidada1"), null);
        Alumno alumno = new Alumno();
        alumno.setUsuario(cuenta);
        when(alumnoRepository.findById("22520187")).thenReturn(Optional.of(alumno));

        service.asignarAlumno("22520187", "22520187");

        assertTrue(encoder.matches("22520187", cuenta.getContrasenaHash()));
        verify(usuarioRepository).save(cuenta);
    }

    @Test
    void permiteUnaContrasenaDeAlumnoSinRequisitosDeComposicion() {
        Usuario cuenta = new Usuario("22520187", encoder.encode("Olvidada1"), null);
        Alumno alumno = new Alumno();
        alumno.setUsuario(cuenta);
        when(alumnoRepository.findById("22520187")).thenReturn(Optional.of(alumno));

        service.asignarAlumno("22520187", "1234");

        assertTrue(encoder.matches("1234", cuenta.getContrasenaHash()));
    }

    @Test
    void rechazaContrasenasDeAlumnoVaciasOMayoresAlLimiteUtf8DeBcrypt() {
        assertThrows(DatosInvalidosException.class, () -> service.asignarAlumno("22520187", ""));
        assertThrows(DatosInvalidosException.class, () -> service.asignarAlumno("22520187", "á".repeat(37)));
        verify(alumnoRepository, never()).findById(any());
    }

    @Test
    void indicaCuandoElAlumnoNoExiste() {
        when(alumnoRepository.findById("99999999")).thenReturn(Optional.empty());

        assertThrows(RecursoNoEncontradoException.class, () -> service.asignarAlumno("99999999", "Temporal2026"));
    }
}
