package mx.edu.itch.sigclem.grupos.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record CalificacionesRequest(@NotEmpty @Valid List<CalificacionRequest> calificaciones) {
}
