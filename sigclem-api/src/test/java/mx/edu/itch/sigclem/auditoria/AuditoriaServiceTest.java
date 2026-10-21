package mx.edu.itch.sigclem.auditoria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import mx.edu.itch.sigclem.auditoria.domain.AccionAuditoria;
import mx.edu.itch.sigclem.auditoria.domain.RegistroAuditoria;
import mx.edu.itch.sigclem.auditoria.repository.RegistroAuditoriaRepository;
import mx.edu.itch.sigclem.auditoria.service.AuditoriaService;

@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private RegistroAuditoriaRepository repository;
    @InjectMocks
    private AuditoriaService service;

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registraLaAccionConElUsuarioYRolAutenticados() {
        when(repository.save(org.mockito.ArgumentMatchers.any(RegistroAuditoria.class))).thenAnswer(i -> i.getArgument(0));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin.jose", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        service.registrar(AccionAuditoria.CONTRASENA_ASIGNADA, RegistroAuditoria.OK, "Contraseña asignada al alumno A1.");

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(repository).save(captor.capture());
        RegistroAuditoria r = captor.getValue();
        assertEquals("admin.jose", r.getUsuario());
        assertEquals("ADMIN", r.getRol());
        assertEquals("CONTRASENA_ASIGNADA", r.getAccion());
        assertEquals(RegistroAuditoria.OK, r.getResultado());
    }

    @Test
    void registraUnIntentoFallidoSinUsuarioAutenticado() {
        when(repository.save(org.mockito.ArgumentMatchers.any(RegistroAuditoria.class))).thenAnswer(i -> i.getArgument(0));

        service.registrarDe("fulano", null, AccionAuditoria.INICIO_SESION_FALLIDO, RegistroAuditoria.RECHAZADO,
                "Usuario o contraseña incorrectos.");

        ArgumentCaptor<RegistroAuditoria> captor = ArgumentCaptor.forClass(RegistroAuditoria.class);
        verify(repository).save(captor.capture());
        RegistroAuditoria r = captor.getValue();
        assertEquals("fulano", r.getUsuario());
        assertNull(r.getRol());
        assertEquals(RegistroAuditoria.RECHAZADO, r.getResultado());
    }
}
