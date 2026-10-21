package mx.edu.itch.sigclem.seguridad.config;

import java.io.IOException;
import java.time.OffsetDateTime;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import mx.edu.itch.sigclem.comun.excepcion.ManejadorGlobalExcepciones;

/**
 * Respuestas 401 y 403 generadas por la cadena de filtros, antes de llegar a los controladores,
 * con el mismo formato RFC 7807 que el manejador global.
 */
@Component
public class RespuestasSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException ex) throws IOException {
        escribir(response, request, 401, "AUTH-004", "No autenticado",
                "Inicia sesión para acceder a este recurso.");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException ex) throws IOException {
        escribir(response, request, 403, "AUTH-003", "Acceso denegado",
                "Tu rol no tiene permiso para realizar esta operación.");
    }

    /** Escribe un error RFC 7807 desde un filtro (lo usa también el modo pruebas). */
    public void escribirError(HttpServletResponse response, HttpServletRequest request, int estado, String codigo,
            String titulo, String detalle) throws IOException {
        escribir(response, request, estado, codigo, titulo, detalle);
    }

    private void escribir(HttpServletResponse response, HttpServletRequest request, int estado, String codigo,
            String titulo, String detalle) throws IOException {
        response.setStatus(estado);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String json = "{\"type\":\"" + ManejadorGlobalExcepciones.BASE_TIPOS + codigo.toLowerCase() + "\","
                + "\"title\":\"" + escapar(titulo) + "\",\"status\":" + estado + ","
                + "\"detail\":\"" + escapar(detalle) + "\",\"instance\":\"" + escapar(request.getRequestURI()) + "\","
                + "\"codigo\":\"" + codigo + "\",\"timestamp\":\"" + OffsetDateTime.now() + "\"}";
        response.getWriter().write(json);
    }

    private static String escapar(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ");
    }
}
