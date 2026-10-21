package mx.edu.itch.sigclem.pagos.dto;

import java.math.BigDecimal;

import mx.edu.itch.sigclem.pagos.domain.ConceptoPago;
import mx.edu.itch.sigclem.pagos.domain.TarifaPago;

/** Concepto con su monto general vigente ({@code idTarifa} es la tarifa con la que se emiten los cargos). */
public record ConceptoDTO(Integer id, String nombre, boolean requiereNivel, boolean activo, BigDecimal monto,
        Integer idTarifa) {

    public static ConceptoDTO desde(ConceptoPago concepto) {
        return desde(concepto, null);
    }

    public static ConceptoDTO desde(ConceptoPago concepto, TarifaPago tarifa) {
        return new ConceptoDTO(concepto.getId(), concepto.getNombre(), concepto.isRequiereNivel(), concepto.isActivo(),
                tarifa == null ? null : tarifa.getMonto(), tarifa == null ? null : tarifa.getId());
    }
}
