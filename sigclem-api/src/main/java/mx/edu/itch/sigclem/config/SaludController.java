package mx.edu.itch.sigclem.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint público para verificar que la API está en ejecución. */
@Tag(name = "02. Salud y catálogos", description = "Estado de la API y catálogos institucionales.")
@RestController
public class SaludController {

    @Operation(summary = "Verificar que la API está en ejecución", description = "Rol: [Público]")
    @GetMapping("/api/v1/salud")
    public Map<String, String> salud() {
        return Map.of("estado", "ok");
    }
}
