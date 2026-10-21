package mx.edu.itch.sigclem.seguridad.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import mx.edu.itch.sigclem.comun.excepcion.DemasiadosIntentosException;

/**
 * Bloqueo temporal de la cuenta tras cinco intentos fallidos (Tabla 60, A07).
 * Se guarda en memoria porque el sistema se ejecuta en una sola instancia.
 */
@Service
public class IntentosLoginService {

    static final int MAX_INTENTOS = 5;
    static final Duration BLOQUEO = Duration.ofMinutes(15);

    private record Registro(int fallos, Instant bloqueadoHasta) {
    }

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();

    public void verificarNoBloqueado(String usuario) {
        Registro r = registros.get(clave(usuario));
        if (r != null && r.bloqueadoHasta() != null && Instant.now().isBefore(r.bloqueadoHasta())) {
            throw new DemasiadosIntentosException(
                    "Demasiados intentos fallidos. Intenta de nuevo en unos minutos.");
        }
    }

    public void registrarFallo(String usuario) {
        registros.compute(clave(usuario), (k, r) -> {
            int fallos = (r == null || r.bloqueadoHasta() != null) ? 1 : r.fallos() + 1;
            return fallos >= MAX_INTENTOS ? new Registro(0, Instant.now().plus(BLOQUEO)) : new Registro(fallos, null);
        });
    }

    public void registrarExito(String usuario) {
        registros.remove(clave(usuario));
    }

    private static String clave(String usuario) {
        return usuario == null ? "" : usuario.trim().toLowerCase();
    }
}
