package mx.edu.itch.sigclem.grupos.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CerrarActaCoordinacionRequest(
        @NotBlank(message = "Escribe la observación")
        @Size(min = 10, max = 500, message = "La observación debe tener de 10 a 500 caracteres")
        String observacion,
        @Valid List<NivelAcreditadoRequest> nivelesAcreditados) {
}
