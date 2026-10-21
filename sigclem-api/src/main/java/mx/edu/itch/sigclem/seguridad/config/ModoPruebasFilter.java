package mx.edu.itch.sigclem.seguridad.config;

import java.io.IOException;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import mx.edu.itch.sigclem.seguridad.domain.RolSistema;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;
import mx.edu.itch.sigclem.seguridad.service.CustomUserDetailsService;

/**
 * MODO PRUEBAS (solo desarrollo y demostraciones): permite consumir la API sin iniciar sesión.
 *
 * <p>Si la petición no trae un token JWT válido, se autentica como el usuario indicado en la cabecera
 * {@value #CABECERA} (o en el parámetro {@value #PARAMETRO}); si no se indica ninguno, se usa el usuario
 * por omisión (el configurado o, en su defecto, la primera cuenta activa de coordinación). Los roles y las reglas de negocio se siguen aplicando: para probar un
 * endpoint de alumno se envía su número de control, para uno de facilitador su correo, etc.</p>
 *
 * <p>Se activa con {@code app.modo-pruebas=true} (variable {@code MODO_PRUEBAS}). Debe desactivarse en producción.</p>
 */
@Slf4j
public class ModoPruebasFilter extends OncePerRequestFilter {

    public static final String CABECERA = "X-Usuario-Prueba";
    public static final String PARAMETRO = "usuarioPrueba";

    private final CustomUserDetailsService userDetailsService;
    private final UsuarioRepository usuarioRepository;
    private final RespuestasSeguridad respuestas;
    private final String usuarioPorOmision;

    public ModoPruebasFilter(CustomUserDetailsService userDetailsService, UsuarioRepository usuarioRepository,
            RespuestasSeguridad respuestas, String usuarioPorOmision) {
        this.userDetailsService = userDetailsService;
        this.usuarioRepository = usuarioRepository;
        this.respuestas = respuestas;
        this.usuarioPorOmision = usuarioPorOmision == null ? "" : usuarioPorOmision.trim();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Solo la API; la documentación y los recursos estáticos no requieren identidad.
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        return !ruta.startsWith("/api/") || ruta.startsWith("/api/v1/docs");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication actual = SecurityContextHolder.getContext().getAuthentication();
        boolean sinIdentidad = actual == null || actual instanceof AnonymousAuthenticationToken
                || !actual.isAuthenticated();
        if (sinIdentidad) {
            String solicitado = valor(request.getHeader(CABECERA));
            if (solicitado.isEmpty() && "GET".equalsIgnoreCase(request.getMethod())) {
                // Útil para abrir descargas (PDF, comprobantes) directamente en el navegador.
                solicitado = valor(request.getParameter(PARAMETRO));
            }
            boolean explicito = !solicitado.isEmpty();
            String usuario = explicito ? solicitado : usuarioPorOmision();
            if (!usuario.isEmpty()) {
                try {
                    UserDetails detalles = userDetailsService.loadUserByUsername(usuario);
                    if (!detalles.isEnabled()) {
                        respuestas.escribirError(response, request, 403, "PRU-002", "Usuario de prueba inactivo",
                                "La cuenta '" + usuario + "' está desactivada.");
                        return;
                    }
                    var auth = new UsernamePasswordAuthenticationToken(detalles, null, detalles.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } catch (UsernameNotFoundException ex) {
                    if (explicito) {
                        respuestas.escribirError(response, request, 400, "PRU-001", "Usuario de prueba inexistente",
                                "No existe la cuenta '" + usuario + "'. Consulta GET /api/v1/pruebas/usuarios.");
                        return;
                    }
                    log.warn("Modo pruebas: la cuenta por omisión '{}' no existe; la petición sigue sin identidad.",
                            usuario);
                }
            }
        }
        chain.doFilter(request, response);
    }

    /** Usuario configurado o, si no existe, la primera cuenta activa de coordinación. */
    private String usuarioPorOmision() {
        if (!usuarioPorOmision.isEmpty() && usuarioRepository.existsByNombreUsuario(usuarioPorOmision)) {
            return usuarioPorOmision;
        }
        return usuarioRepository.findFirstByRolNombreAndActivoTrueOrderByIdAsc(RolSistema.COORDINADOR.nombreBd())
                .map(Usuario::getNombreUsuario)
                .orElse("");
    }

    private static String valor(String texto) {
        return texto == null ? "" : texto.trim();
    }
}
