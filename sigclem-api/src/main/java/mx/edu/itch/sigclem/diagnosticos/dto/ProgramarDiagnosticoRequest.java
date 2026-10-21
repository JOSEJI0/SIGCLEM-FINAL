package mx.edu.itch.sigclem.diagnosticos.dto;

import java.time.LocalDateTime;
import java.net.URI;
import java.net.URISyntaxException;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

public record ProgramarDiagnosticoRequest(
        @NotNull @Future LocalDateTime fechaHoraAplicacion,
        @NotBlank @Size(max = 255) String enlace) {

    @AssertTrue(message = "El enlace debe ser una URL HTTP o HTTPS válida.")
    public boolean isEnlaceValido() {
        if (enlace == null || enlace.isBlank()) return true;
        try {
            URI uri = new URI(enlace.trim());
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank()
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException ex) {
            return false;
        }
    }
}
