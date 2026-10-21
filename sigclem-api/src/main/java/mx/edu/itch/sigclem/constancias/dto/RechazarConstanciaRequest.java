package mx.edu.itch.sigclem.constancias.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RechazarConstanciaRequest(
        @NotBlank @Size(max = 200) String motivo) {
}
