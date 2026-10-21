package mx.edu.itch.sigclem.auditoria.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.edu.itch.sigclem.auditoria.domain.AccionAuditoria;
import mx.edu.itch.sigclem.auditoria.domain.RegistroAuditoria;
import mx.edu.itch.sigclem.auditoria.repository.RegistroAuditoriaRepository;

/**
 * Registra en la bitácora las acciones sensibles del sistema. La bitácora es solo de inserción: ni el administrador
 * puede modificarla o borrarla desde la aplicación, para que sirva como evidencia.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditoriaService {

    private final RegistroAuditoriaRepository repository;

    /** Registra una acción atribuida al usuario autenticado actual. */
    public void registrar(AccionAuditoria accion, String resultado, String detalle) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String usuario = auth == null || !auth.isAuthenticated() ? null : auth.getName();
        String rol = auth == null ? null : auth.getAuthorities().stream()
                .map(Object::toString).filter(a -> a.startsWith("ROLE_")).findFirst()
                .map(a -> a.substring("ROLE_".length())).orElse(null);
        guardar(usuario, rol, accion, resultado, detalle);
    }

    /** Registra una acción atribuida a una cuenta concreta (por ejemplo, el inicio de sesión, antes de autenticar). */
    public void registrarDe(String usuario, String rol, AccionAuditoria accion, String resultado, String detalle) {
        guardar(usuario, rol, accion, resultado, detalle);
    }

    /**
     * En transacción propia para que el registro persista aunque la operación principal termine en error (por
     * ejemplo, un intento rechazado) y nunca haga fallar a la operación principal si la auditoría tuviera un problema.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void guardar(String usuario, String rol, AccionAuditoria accion, String resultado, String detalle) {
        try {
            repository.save(new RegistroAuditoria(recortar(usuario, 50), recortar(rol, 30), accion.name(), resultado,
                    recortar(detalle, 500), ipActual()));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar en la auditoría la acción {}: {}", accion, e.getMessage());
        }
    }

    private static String recortar(String valor, int maximo) {
        if (valor == null) {
            return null;
        }
        return valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }

    private static String ipActual() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes atributos) {
            HttpServletRequest req = atributos.getRequest();
            String adelante = req.getHeader("X-Forwarded-For");
            if (adelante != null && !adelante.isBlank()) {
                return adelante.split(",")[0].trim();
            }
            return req.getRemoteAddr();
        }
        return null;
    }
}
