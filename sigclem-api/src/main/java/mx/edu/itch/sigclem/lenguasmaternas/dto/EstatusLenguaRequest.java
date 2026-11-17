package mx.edu.itch.sigclem.lenguasmaternas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EstatusLenguaRequest(
        @NotBlank @Pattern(regexp = "Validado|Rechazado") String estatus,
        String documentoRechazado,
        @Size(max = 500) String motivoRechazo,
        @Size(max = 20) @Pattern(regexp = "[A-Za-z0-9]*") String folio) {
}
