package mx.edu.itch.sigclem.pagos.dto;

import java.math.BigDecimal;

import mx.edu.itch.sigclem.pagos.domain.TarifaPago;

public record TarifaDTO(Integer id, String concepto, Integer idNivel, String nivel, BigDecimal monto, boolean activa) {

    public static TarifaDTO desde(TarifaPago tarifa) {
        return new TarifaDTO(tarifa.getId(), tarifa.getConcepto(),
                tarifa.getNivel() == null ? null : tarifa.getNivel().getId(),
                tarifa.getNivel() == null ? null : tarifa.getNivel().getNombre(),
                tarifa.getMonto(), tarifa.isActiva());
    }
}
