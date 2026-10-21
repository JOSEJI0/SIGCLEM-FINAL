package mx.edu.itch.sigclem.pagos.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TarifaRequest(
        @NotBlank @Size(max = 30) String concepto,
        @Positive Integer idNivel,
        @DecimalMin(value = "0.01") @Digits(integer = 8, fraction = 2) BigDecimal monto) {
}
