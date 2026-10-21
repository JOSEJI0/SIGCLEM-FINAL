package mx.edu.itch.sigclem.comun.excepcion;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Excepción base del sistema. Cada subclase define el código HTTP; el campo {@code codigo}
 * identifica el error para la SPA (Tabla 61 del documento de diseño).
 */
@Getter
public abstract class SigclemException extends RuntimeException {

    private final HttpStatus estado;
    private final String codigo;
    private final String titulo;

    protected SigclemException(HttpStatus estado, String codigo, String titulo, String mensaje) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
        this.titulo = titulo;
    }
}
