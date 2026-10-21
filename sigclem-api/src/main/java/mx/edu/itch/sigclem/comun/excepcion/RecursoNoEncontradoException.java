package mx.edu.itch.sigclem.comun.excepcion;

import org.springframework.http.HttpStatus;

public class RecursoNoEncontradoException extends SigclemException {

    public RecursoNoEncontradoException(String codigo, String mensaje) {
        super(HttpStatus.NOT_FOUND, codigo, "Recurso no encontrado", mensaje);
    }
}
