package mx.edu.itch.sigclem.comun.excepcion;

import org.springframework.http.HttpStatus;

public class DemasiadosIntentosException extends SigclemException {

    public DemasiadosIntentosException(String mensaje) {
        super(HttpStatus.TOO_MANY_REQUESTS, "AUTH-002", "Cuenta bloqueada temporalmente", mensaje);
    }
}
