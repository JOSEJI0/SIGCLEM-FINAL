package mx.edu.itch.sigclem.comun.excepcion;

import org.springframework.http.HttpStatus;

public class ConflictoException extends SigclemException {

    public ConflictoException(String codigo, String mensaje) {
        super(HttpStatus.CONFLICT, codigo, "Conflicto con datos existentes", mensaje);
    }
}
