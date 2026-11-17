package mx.edu.itch.sigclem.alumnos.dto;

import java.util.List;

public record ResultadoImportacionOperativaDTO(
        int filas,
        int alumnosCreados,
        int alumnosExistentes,
        int facilitadoresCreados,
        int facilitadoresExistentes,
        int gruposCreados,
        int gruposExistentes,
        int horariosCreados,
        List<CredencialTemporal> credencialesTemporales,
        List<ErrorFila> errores) {

    public record CredencialTemporal(String nombre, String correo, String contrasenaTemporal) {
    }

    public record ErrorFila(String hoja, int fila, String identificador, String mensaje) {
    }
}
