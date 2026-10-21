package mx.edu.itch.sigclem.pagos.dto;

import jakarta.validation.constraints.Positive;

public record ValidarComprobanteRequest(@Positive Integer idGrupo) {
}
