package mx.edu.itch.sigclem.pagos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RechazoRequest(@NotBlank @Size(max = 200) String motivo) {
}
