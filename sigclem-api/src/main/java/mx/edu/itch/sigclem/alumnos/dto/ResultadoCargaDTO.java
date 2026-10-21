package mx.edu.itch.sigclem.alumnos.dto;

import java.util.List;

/**
 * Resultado de la carga masiva de alumnos desde un archivo Excel: cuántas filas se procesaron, cuántas cuentas se
 * crearon y el detalle de las filas con error (para que el coordinador las corrija y vuelva a subirlas).
 */
public record ResultadoCargaDTO(int filas, int creados, List<FilaConError> errores) {

    /** {@code fila} es el número de fila del Excel (empezando en 2, porque la 1 es el encabezado). */
    public record FilaConError(int fila, String numeroControl, String motivo) {
    }
}
