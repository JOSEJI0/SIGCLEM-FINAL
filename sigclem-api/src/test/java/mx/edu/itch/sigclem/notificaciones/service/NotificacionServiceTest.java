package mx.edu.itch.sigclem.notificaciones.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.List;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.notificaciones.domain.Notificacion;
import mx.edu.itch.sigclem.notificaciones.dto.MensajeRequest;
import mx.edu.itch.sigclem.notificaciones.repository.NotificacionRepository;
import mx.edu.itch.sigclem.seguridad.domain.Rol;
import mx.edu.itch.sigclem.seguridad.domain.RolSistema;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class NotificacionServiceTest {

    @Mock
    private NotificacionRepository notificacionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @InjectMocks
    private NotificacionService service;

    @Test
    void enviarGuardaMensajeDirigidoALaJefaConElRemitente() {
        Usuario coordinador = usuario("coordinador", RolSistema.COORDINADOR);
        Usuario jefa = new Usuario("jefa.dgtv", "hash", org.mockito.Mockito.mock(Rol.class));
        when(usuarioRepository.findByNombreUsuario("coordinador")).thenReturn(Optional.of(coordinador));
        when(usuarioRepository.findFirstByRolNombreAndActivoTrueOrderByIdAsc("Jefa del DGTV"))
                .thenReturn(Optional.of(jefa));
        when(notificacionRepository.save(any(Notificacion.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        var respuesta = service.enviar(new MensajeRequest("  Informe  ", "  Favor de revisar.  "), "coordinador");

        assertEquals("Informe", respuesta.asunto());
        assertEquals("Favor de revisar.", respuesta.mensaje());
        assertEquals("coordinador", respuesta.remitente());
        assertEquals("jefa.dgtv", respuesta.destinatario());
        assertFalse(respuesta.leida());
        verify(notificacionRepository).save(any(Notificacion.class));
    }

    @Test
    void enviarRechazaRemitenteQueNoSeaCoordinador() {
        Usuario alumno = usuario("22520187", RolSistema.ALUMNO);
        when(usuarioRepository.findByNombreUsuario("22520187")).thenReturn(Optional.of(alumno));

        assertThrows(DatosInvalidosException.class,
                () -> service.enviar(new MensajeRequest("Asunto", "Mensaje"), "22520187"));

        verify(notificacionRepository, never()).save(any(Notificacion.class));
    }

    @Test
    void alumnoConsultaUnicamenteLasNotificacionesDirigidasASuCuenta() {
        Usuario alumno = usuario("22520187", RolSistema.ALUMNO);
        Notificacion notificacion = new Notificacion();
        notificacion.setDestino(alumno);
        notificacion.setAsunto("Evaluación diagnóstica programada");
        notificacion.setMensaje("Consulta la fecha y el enlace de tu evaluación.");
        notificacion.setFechaEnvio(LocalDateTime.now());
        when(usuarioRepository.findByNombreUsuario("22520187")).thenReturn(Optional.of(alumno));
        when(notificacionRepository.findTop100ByDestinoNombreUsuarioOrderByFechaEnvioDesc("22520187"))
                .thenReturn(List.of(notificacion));

        var respuestas = service.listarRecibidas("22520187");

        assertEquals(1, respuestas.size());
        assertEquals("22520187", respuestas.getFirst().destinatario());
        assertEquals(notificacion.getAsunto(), respuestas.getFirst().asunto());
        verify(notificacionRepository).findTop100ByDestinoNombreUsuarioOrderByFechaEnvioDesc("22520187");
    }

    @Test
    void otrasRolesNoPuedenUsarLaBandejaDeNotificacionesRecibidas() {
        Usuario facilitador = usuario("facilitador", RolSistema.FACILITADOR);
        when(usuarioRepository.findByNombreUsuario("facilitador")).thenReturn(Optional.of(facilitador));

        assertThrows(DatosInvalidosException.class, () -> service.listarRecibidas("facilitador"));

        verifyNoInteractions(notificacionRepository);
    }

    private static Usuario usuario(String nombreUsuario, RolSistema rolSistema) {
        Rol rol = org.mockito.Mockito.mock(Rol.class);
        when(rol.comoRolSistema()).thenReturn(rolSistema);
        return new Usuario(nombreUsuario, "hash", rol);
    }
}
