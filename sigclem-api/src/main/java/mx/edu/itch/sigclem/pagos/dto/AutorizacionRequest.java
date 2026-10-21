package mx.edu.itch.sigclem.pagos.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AutorizacionRequest(
        @NotBlank @Size(max = 10) String numeroControl,
        @NotNull @Positive Integer idTarifa,
        @NotNull @Positive Integer idPeriodo) {
}
