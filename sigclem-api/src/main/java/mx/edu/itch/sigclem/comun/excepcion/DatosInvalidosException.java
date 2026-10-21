package mx.edu.itch.sigclem.comun.excepcion;

import org.springframework.http.HttpStatus;

public class DatosInvalidosException extends SigclemException {

    public DatosInvalidosException(String codigo, String mensaje) {
        super(HttpStatus.BAD_REQUEST, codigo, "Datos inválidos", mensaje);
    }
}
