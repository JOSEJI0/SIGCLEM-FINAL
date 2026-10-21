package mx.edu.itch.sigclem.pagos.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DatosComprobanteRequest(
        @NotBlank @Pattern(regexp = "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        String uuidCfdi,
        @NotBlank @Size(max = 13) String rfcEmisor,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal total,
        /** Últimos 8 caracteres del «Sello digital del CFDI» impreso; opcional, permite consultar al SAT. */
        @Pattern(regexp = "[A-Za-z0-9+/=]{8}", message = "Escribe los últimos 8 caracteres del sello digital del CFDI.")
        String selloFinal) {
}
