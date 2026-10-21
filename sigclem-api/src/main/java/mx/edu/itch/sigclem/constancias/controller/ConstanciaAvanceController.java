package mx.edu.itch.sigclem.constancias.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.constancias.dto.ConstanciaAvanceDTO;
import mx.edu.itch.sigclem.constancias.service.ConstanciaAvanceService;

@Tag(name = "11. Constancias de avance por nivel", description = "Constancia de cada nivel de inglés acreditado.")
@RestController
@RequestMapping("/api/v1/constancias/avance")
@RequiredArgsConstructor
public class ConstanciaAvanceController {

    private final ConstanciaAvanceService service;

    @GetMapping("/mias")
    @PreAuthorize("hasRole('ALUMNO')")
    @Operation(summary = "Mis niveles acreditados con constancia disponible", description = "Rol: [Alumno]")
    public List<ConstanciaAvanceDTO> mias(Authentication authentication) {
        return service.listarPropias(authentication.getName());
    }

    @GetMapping("/alumnos/{numeroControl}")
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Niveles acreditados de un alumno", description = "Rol: [Coordinador][Jefa DGTV]")
    public List<ConstanciaAvanceDTO> deAlumno(@PathVariable String numeroControl) {
        return service.listar(numeroControl);
    }

    @GetMapping("/{idInscripcion}/pdf")
    @PreAuthorize("hasAnyRole('ALUMNO','COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Descargar la constancia de avance de un nivel (PDF)",
            description = "Rol: [Alumno] (solo propias)[Coordinador][Jefa DGTV]")
    public ResponseEntity<byte[]> pdf(@PathVariable Integer idInscripcion, Authentication authentication) {
        boolean personal = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_COORDINADOR")
                        || a.getAuthority().equals("ROLE_JEFA_DGTV"));
        byte[] pdf = service.generarPdf(idInscripcion, authentication.getName(), personal);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"constancia-avance-" + idInscripcion + ".pdf\"")
                .body(pdf);
    }
}
