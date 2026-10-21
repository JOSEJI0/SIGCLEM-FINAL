package mx.edu.itch.sigclem.comun.excepcion;

import org.springframework.http.HttpStatus;

public class ReglaNegocioException extends SigclemException {

    public ReglaNegocioException(String codigo, String mensaje) {
        super(HttpStatus.valueOf(422), codigo, "Regla de negocio no cumplida", mensaje);
    }
}
