package mx.edu.itch.sigclem.pruebas.dto;

import java.util.List;

/** Resultado de la carga de datos de demostración. */
public record DatosDemoDTO(
        List<String> acciones,
        Integer idPeriodo,
        Integer idGrupo,
        List<CuentaDemo> cuentas) {

    /** Cuenta creada (o existente) con su contraseña para probar también el login real. */
    public record CuentaDemo(String rol, String usuario, String contrasena) {
    }
}
