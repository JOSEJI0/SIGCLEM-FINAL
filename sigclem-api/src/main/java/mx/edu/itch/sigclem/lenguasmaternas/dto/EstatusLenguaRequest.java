package mx.edu.itch.sigclem.lenguasmaternas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record EstatusLenguaRequest(@NotBlank @Pattern(regexp = "Validado|Rechazado") String estatus) {
}
