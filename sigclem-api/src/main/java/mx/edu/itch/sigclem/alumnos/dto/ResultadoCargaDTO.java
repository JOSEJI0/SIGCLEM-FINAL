package mx.edu.itch.sigclem.alumnos.dto;

import java.util.List;

/**
 * Resultado de la carga masiva de alumnos desde un archivo Excel: cuántas filas se procesaron, cuántas cuentas se
 * crearon y el detalle de las filas con error (para que el coordinador las corrija y vuelva a subirlas).
 * <p>
 * Los últimos campos solo los llena la importación del concentrado histórico: alumnos existentes actualizados,
 * niveles que quedaron acreditados en el avance, alumnos con el idioma liberado y alumnos completados con la
 * matrícula.
 */
public record ResultadoCargaDTO(int filas, int creados, int nivelesImportados, int gruposImportados,
        int inscripcionesImportadas, int horariosImportados, List<FilaConError> errores,
        int actualizados, int nivelesAcreditados, int liberados, int conMatricula) {

    public ResultadoCargaDTO(int filas, int creados, int nivelesImportados, int gruposImportados,
            int inscripcionesImportadas, int horariosImportados, List<FilaConError> errores) {
        this(filas, creados, nivelesImportados, gruposImportados, inscripcionesImportadas, horariosImportados, errores,
                0, 0, 0, 0);
    }

    public ResultadoCargaDTO(int filas, int creados, int nivelesImportados, int gruposImportados,
            int inscripcionesImportadas, List<FilaConError> errores) {
        this(filas, creados, nivelesImportados, gruposImportados, inscripcionesImportadas, 0, errores);
    }

    public ResultadoCargaDTO(int filas, int creados, int nivelesImportados, List<FilaConError> errores) {
        this(filas, creados, nivelesImportados, 0, 0, 0, errores);
    }

    /** {@code fila} es el número de fila del Excel (empezando en 2, porque la 1 es el encabezado). */
    public record FilaConError(int fila, String numeroControl, String mensaje) {
    }
}
