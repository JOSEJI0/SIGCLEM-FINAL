package mx.edu.itch.sigclem.diagnosticos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.security.Principal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.diagnosticos.dto.EvaluacionDiagnosticaDTO;
import mx.edu.itch.sigclem.diagnosticos.dto.ProgramarDiagnosticoRequest;
import mx.edu.itch.sigclem.diagnosticos.dto.RegistroDiagnosticoRequest;
import mx.edu.itch.sigclem.diagnosticos.dto.ResultadoDiagnosticoRequest;
import mx.edu.itch.sigclem.diagnosticos.service.EvaluacionDiagnosticaService;

@Tag(name = "05. Evaluación diagnóstica", description = "CU-22 a CU-24.")
@RestController
@RequestMapping("/api/v1/diagnosticos")
@RequiredArgsConstructor
public class EvaluacionDiagnosticaController {

    private final EvaluacionDiagnosticaService service;

    @PostMapping
    @PreAuthorize("hasRole('ALUMNO')")
    @Operation(summary = "Solicitar evaluación diagnóstica", description = "Rol: [Alumno]")
    public ResponseEntity<EvaluacionDiagnosticaDTO> solicitar(Principal principal) {
        EvaluacionDiagnosticaDTO diagnostico = service.solicitar(principal.getName());
        return ResponseEntity.created(URI.create("/api/v1/diagnosticos/" + diagnostico.id())).body(diagnostico);
    }

    @GetMapping("/mias")
    @PreAuthorize("hasRole('ALUMNO')")
    @Operation(summary = "Mis evaluaciones diagnósticas", description = "Rol: [Alumno]")
    public List<EvaluacionDiagnosticaDTO> listarPropias(Principal principal) {
        return service.listarPropias(principal.getName());
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Listar todas las evaluaciones", description = "Rol: [Coordinador][Jefa DGTV: solo consulta]")
    public List<EvaluacionDiagnosticaDTO> listarTodas() {
        return service.listarTodas();
    }

    @PutMapping("/{id}/programacion")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Programar evaluación (fecha y enlace)", description = "Rol: [Coordinador]")
    public EvaluacionDiagnosticaDTO programar(
            @PathVariable Integer id,
            @Valid @RequestBody ProgramarDiagnosticoRequest solicitud,
            Principal principal) {
        return service.programar(id, solicitud, principal.getName());
    }

    @PostMapping("/registro")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Registrar evaluación aplicada con su resultado", description = "Rol: [Coordinador]")
    public ResponseEntity<EvaluacionDiagnosticaDTO> registrarDirecto(
            @Valid @RequestBody RegistroDiagnosticoRequest solicitud, Principal principal) {
        EvaluacionDiagnosticaDTO creada = service.registrarDirecto(solicitud, principal.getName());
        return ResponseEntity.created(URI.create("/api/v1/diagnosticos/" + creada.id())).body(creada);
    }

    @PutMapping("/{id}/resultado")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Registrar o corregir puntaje y recomendación", description = "Rol: [Coordinador]")
    public EvaluacionDiagnosticaDTO registrarResultado(
            @PathVariable Integer id,
            @Valid @RequestBody ResultadoDiagnosticoRequest solicitud) {
        return service.registrarResultado(id, solicitud);
    }
}
