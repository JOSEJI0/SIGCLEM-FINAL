package mx.edu.itch.sigclem.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Fila del listado general de cargos (consulta de coordinación y supervisión). */
public record CargoResumenDTO(
        Integer id,
        String numeroControl,
        String alumno,
        String concepto,
        String nivel,
        String periodo,
        BigDecimal montoEsperado,
        BigDecimal saldoPendiente,
        String estatus,
        LocalDate fechaEmision,
        int comprobantes,
        int comprobantesPendientes) {
}
