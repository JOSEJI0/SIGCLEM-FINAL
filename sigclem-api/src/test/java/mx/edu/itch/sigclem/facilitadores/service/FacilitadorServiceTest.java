package mx.edu.itch.sigclem.facilitadores.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import mx.edu.itch.sigclem.comun.excepcion.ConflictoException;
import mx.edu.itch.sigclem.facilitadores.domain.Facilitador;
import mx.edu.itch.sigclem.facilitadores.dto.FacilitadorRequest;
import mx.edu.itch.sigclem.facilitadores.repository.FacilitadorRepository;
import mx.edu.itch.sigclem.seguridad.domain.Rol;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.RolRepository;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class FacilitadorServiceTest {

    @Mock
    private FacilitadorRepository facilitadorRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RolRepository rolRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @InjectMocks
    private FacilitadorService service;

    @Test
    void registrarNormalizaCorreoYDevuelveContrasenaTemporalSinGuardarlaEnClaro() {
        Rol rol = new Rol();
        when(facilitadorRepository.existsByCorreoIgnoreCase("docente@itch.edu.mx")).thenReturn(false);
        when(usuarioRepository.existsByNombreUsuario("docente@itch.edu.mx")).thenReturn(false);
        when(rolRepository.findByNombre("Facilitador")).thenReturn(Optional.of(rol));
        when(passwordEncoder.encode(any())).thenAnswer(invocacion -> "hash:" + invocacion.getArgument(0));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(facilitadorRepository.save(any(Facilitador.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        var creado = service.registrar(new FacilitadorRequest(
                "Ana", "López", "García", " Docente@ITCH.edu.mx ", "7471234567", null));

        assertEquals("docente@itch.edu.mx", creado.usuario());
        assertTrue(creado.contrasenaTemporal().matches("[A-Za-z0-9_-]{24}"));
        assertNotEquals("hash:" + creado.contrasenaTemporal(), creado.contrasenaTemporal());
        verify(passwordEncoder).encode(creado.contrasenaTemporal());
    }

    @Test
    void registrarConContrasenaAsignadaPorCoordinacionNoLaDevuelve() {
        when(facilitadorRepository.existsByCorreoIgnoreCase("docente@itch.edu.mx")).thenReturn(false);
        when(usuarioRepository.existsByNombreUsuario("docente@itch.edu.mx")).thenReturn(false);
        when(rolRepository.findByNombre("Facilitador")).thenReturn(Optional.of(new Rol()));
        when(passwordEncoder.encode("Docente2026!")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(facilitadorRepository.save(any(Facilitador.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        var creado = service.registrar(new FacilitadorRequest(
                "Ana", "López", null, "docente@itch.edu.mx", null, "Docente2026!"));

        assertEquals(null, creado.contrasenaTemporal());
        verify(passwordEncoder).encode("Docente2026!");
    }

    @Test
    void registrarRechazaCorreoDuplicadoAntesDeCrearLaCuenta() {
        when(facilitadorRepository.existsByCorreoIgnoreCase("docente@itch.edu.mx")).thenReturn(true);

        assertThrows(ConflictoException.class, () -> service.registrar(new FacilitadorRequest(
                "Ana", "López", null, "DOCENTE@ITCH.EDU.MX", null, null)));

        verifyNoInteractions(rolRepository, passwordEncoder);
    }
}
