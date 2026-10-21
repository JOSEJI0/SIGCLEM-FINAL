package mx.edu.itch.sigclem.lenguasmaternas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LenguaMaternaRequest(@NotBlank @Size(max = 40) String nombre) {
}
