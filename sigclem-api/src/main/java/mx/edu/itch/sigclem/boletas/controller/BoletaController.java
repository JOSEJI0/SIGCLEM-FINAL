package mx.edu.itch.sigclem.boletas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.boletas.dto.BoletaDTO;
import mx.edu.itch.sigclem.boletas.service.BoletaService;

@Tag(name = "09. Boletas", description = "CU-20: historial de calificaciones por periodo.")
@RestController
@RequestMapping("/api/v1/boletas")
@RequiredArgsConstructor
public class BoletaController {

    private final BoletaService boletaService;

    @GetMapping("/mias")
    @PreAuthorize("hasRole('ALUMNO')")
    @Operation(summary = "Mis boletas", description = "Rol: [Alumno]")
    public List<BoletaDTO> misBoletas(Authentication authentication) {
        return boletaService.obtenerBoletas(authentication.getName());
    }
}
