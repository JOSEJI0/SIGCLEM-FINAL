package mx.edu.itch.sigclem.comun.excepcion;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Traduce las excepciones a respuestas con el formato de detalle de problemas
 * del RFC 7807
 * (application/problem+json), agregando los campos {@code codigo} y
 * {@code timestamp}.
 */
@Slf4j
@RestControllerAdvice
public class ManejadorGlobalExcepciones extends ResponseEntityExceptionHandler {

    public static final String BASE_TIPOS = "https://sigclem.itch.edu.mx/errores/";

    public static ProblemDetail problema(HttpStatusCode estado, String codigo, String titulo, String detalle,
            String instancia) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(estado, detalle);
        pd.setType(URI.create(BASE_TIPOS + codigo.toLowerCase()));
        pd.setTitle(titulo);
        if (instancia != null) {
            pd.setInstance(URI.create(instancia));
        }
        pd.setProperty("codigo", codigo);
        pd.setProperty("timestamp", OffsetDateTime.now().toString());
        return pd;
    }

    @ExceptionHandler(SigclemException.class)
    public ResponseEntity<ProblemDetail> manejarSigclem(SigclemException ex, HttpServletRequest req) {
        return ResponseEntity.status(ex.getEstado())
                .body(problema(ex.getEstado(), ex.getCodigo(), ex.getTitulo(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler({ BadCredentialsException.class, DisabledException.class })
    public ResponseEntity<ProblemDetail> manejarCredenciales(RuntimeException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problema(HttpStatus.UNAUTHORIZED, "AUTH-001",
                "Credenciales inválidas", "Usuario o contraseña incorrectos, o cuenta inactiva.", req.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> manejarAccesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problema(HttpStatus.FORBIDDEN, "AUTH-003",
                "Acceso denegado", "Tu rol no tiene permiso para realizar esta operación.", req.getRequestURI()));
    }

    @Override
    protected ResponseEntity<Object>
    handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
    HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        HttpStatusCode estado = HttpStatusCode.valueOf(413);
        ProblemDetail pd = problema(estado, "ARCH-001", "Archivo demasiado grande",
        "El archivo supera el tamaño máximo permitido de 5 MB.", null);
        return ResponseEntity.status(estado).body(pd);
        }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ProblemDetail pd = problema(HttpStatus.BAD_REQUEST, "VAL-001", "Datos inválidos",
                "Uno o más campos no cumplen el formato requerido.", null);
        pd.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(pd);
    }

    /** Violaciones de llaves únicas o restricciones CHECK de la BD: antes terminaban como error 500. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> manejarIntegridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violación de integridad en {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problema(HttpStatus.CONFLICT, "GEN-409",
                "Conflicto de datos",
                "La operación entra en conflicto con información existente o no cumple las reglas de la base de datos.",
                req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> manejarGeneral(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problema(HttpStatus.INTERNAL_SERVER_ERROR,
                "GEN-500", "Error interno", "Ocurrió un error inesperado. Intenta de nuevo más tarde.",
                req.getRequestURI()));
    }
}
