package mx.edu.itch.sigclem.pagos.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import mx.edu.itch.sigclem.pagos.domain.AutorizacionPago;

public record AutorizacionDTO(
        Integer id,
        String concepto,
        Integer idNivel,
        String nivel,
        Integer idPeriodo,
        String periodo,
        BigDecimal montoEsperado,
        LocalDate fechaEmision,
        BigDecimal saldoPendiente,
        String estatusCargo,
        String estatusComprobante,
        String motivoRechazo,
        List<ComprobanteDTO> comprobantes) {

    public static AutorizacionDTO desde(
            AutorizacionPago autorizacion, String estatus, String motivo, List<ComprobanteDTO> comprobantes) {
        return new AutorizacionDTO(autorizacion.getId(), autorizacion.getConcepto(),
                autorizacion.getNivel() == null ? null : autorizacion.getNivel().getId(),
                autorizacion.getNivel() == null ? null : autorizacion.getNivel().getNombre(),
                autorizacion.getPeriodo().getId(), autorizacion.getPeriodo().getNombre(),
                autorizacion.getMontoEsperado(), autorizacion.getFechaEmision(),
                autorizacion.getSaldoPendiente(), autorizacion.getEstatus(), estatus, motivo,
                List.copyOf(comprobantes));
    }
}
