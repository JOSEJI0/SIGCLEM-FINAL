package mx.edu.itch.sigclem.lenguasmaternas.dto;

import jakarta.validation.constraints.Size;

public record RegistroLenguaRequest(
        Integer idLengua,
        @Size(max = 20) String nivelDominio) {
}
