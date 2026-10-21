package mx.edu.itch.sigclem.constancias.dto;

import java.time.LocalDateTime;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Configuración de las constancias para la pantalla de coordinación.
 *
 * @param imagenes fecha de la última carga de cada imagen del membrete (ENCABEZADO, PIE, LOGO_COORDINACION);
 *                 null si no se ha cargado y se usa la predeterminada
 */
public record ConfiguracionConstanciaDTO(
        String subdirectorNombre,
        String subdirectorGenero,
        String jefeDgtvNombre,
        String jefeDgtvGenero,
        String coordinadorNombre,
        String coordinadorGenero,
        String lema,
        String registroCoordinacion,
        String prefijoOficio,
        String copias,
        LocalDateTime fechaActualizacion,
        String actualizadoPor,
        Map<String, LocalDateTime> imagenes) {

    public record Request(
            @NotBlank @Size(max = 100) String subdirectorNombre,
            @NotBlank @Pattern(regexp = "[FM]", message = "Indica F (femenino) o M (masculino).") String subdirectorGenero,
            @NotBlank @Size(max = 100) String jefeDgtvNombre,
            @NotBlank @Pattern(regexp = "[FM]", message = "Indica F (femenino) o M (masculino).") String jefeDgtvGenero,
            @NotBlank @Size(max = 100) String coordinadorNombre,
            @NotBlank @Pattern(regexp = "[FM]", message = "Indica F (femenino) o M (masculino).") String coordinadorGenero,
            @NotBlank @Size(max = 200) String lema,
            @NotBlank @Size(max = 100) String registroCoordinacion,
            @NotBlank @Size(max = 60) String prefijoOficio,
            @NotBlank @Size(max = 200) String copias) {
    }
}
