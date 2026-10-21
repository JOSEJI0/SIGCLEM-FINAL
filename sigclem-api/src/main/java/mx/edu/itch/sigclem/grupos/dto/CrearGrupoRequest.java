package mx.edu.itch.sigclem.grupos.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearGrupoRequest(
        @NotBlank @Size(max = 2) @Pattern(regexp = "[A-Z]{1,2}", message = "La clave debe contener una o dos letras mayúsculas")
        String clave,
        @NotNull Integer idNivel,
        @NotNull Integer idPeriodo,
        @NotNull Integer idModalidad,
        @NotNull Integer idFacilitador,
        @NotNull Integer idAula,
        @NotNull @Min(1) @Max(255) Integer cupo,
        @NotEmpty @Valid List<HorarioRequest> horarios) {
}
