package mx.edu.itch.sigclem.grupos.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record NivelAcreditadoRequest(@NotBlank String numeroControl, @NotNull Integer idNivel) {
}
