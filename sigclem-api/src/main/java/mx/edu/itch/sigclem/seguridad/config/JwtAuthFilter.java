package mx.edu.itch.sigclem.seguridad.config;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.edu.itch.sigclem.seguridad.service.CustomUserDetailsService;
import mx.edu.itch.sigclem.seguridad.service.JwtService;

/**
 * Reconstruye la identidad del usuario a partir del token Bearer de cada petición (Figura 34).
 * No es un @Component para que solo se ejecute dentro de la cadena de Spring Security.
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String cabecera = request.getHeader("Authorization");
        if (cabecera != null && cabecera.startsWith(PREFIJO)
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.extraerUsuario(cabecera.substring(PREFIJO.length())).ifPresent(usuario -> {
                try {
                    UserDetails detalles = userDetailsService.loadUserByUsername(usuario);
                    if (detalles.isEnabled()) {
                        var auth = new UsernamePasswordAuthenticationToken(detalles, null, detalles.getAuthorities());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } catch (UsernameNotFoundException ex) {
                    log.warn("Token válido de un usuario inexistente: {}", usuario);
                }
            });
        }
        chain.doFilter(request, response);
    }
}
