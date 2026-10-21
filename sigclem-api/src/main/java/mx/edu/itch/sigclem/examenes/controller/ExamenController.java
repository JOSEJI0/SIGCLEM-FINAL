package mx.edu.itch.sigclem.examenes.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.security.Principal;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.examenes.domain.CriterioEvaluacion;
import mx.edu.itch.sigclem.examenes.dto.CapturaResultadoRequest;
import mx.edu.itch.sigclem.examenes.dto.EdicionExamenRequest;
import mx.edu.itch.sigclem.examenes.dto.RegistroExamenRequest;
import mx.edu.itch.sigclem.examenes.dto.ExamenDTO;
import mx.edu.itch.sigclem.examenes.dto.ProgramacionExamenRequest;
import mx.edu.itch.sigclem.examenes.dto.SolicitudExamenRequest;
import mx.edu.itch.sigclem.examenes.service.ExamenService;

@Tag(name = "04. Exámenes", description = "CU-04 a CU-06: solicitud, programación, registro, edición, ocultamiento "
        + "y resultado con asignación directa de nivel.")
@RestController
@RequestMapping("/api/v1/examenes")
@RequiredArgsConstructor
public class ExamenController {

    private final ExamenService examenService;

    /** CU-04: Solicitar examen de colocación o de cuatro habilidades (Tabla 81). */
    @PostMapping
    @PreAuthorize("hasRole('ALUMNO')")
    @Operation(summary = "Solicitar examen de colocación o cuatro habilidades", description = "Rol: [Alumno]")
    public ResponseEntity<ExamenDTO> solicitar(
            @Valid @RequestBody SolicitudExamenRequest req,
            Principal principal) {
        ExamenDTO dto = examenService.solicitar(req, principal.getName());
        return ResponseEntity.created(URI.create("/api/v1/examenes/" + dto.id())).body(dto);
    }

    /** CU-05: Programar fecha y horario del examen (Tabla 81). */
    @PutMapping("/{id}/programacion")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Programar fecha y evaluador", description = "Rol: [Coordinador]")
    public ExamenDTO programar(
            @PathVariable Integer id,
            @Valid @RequestBody ProgramacionExamenRequest req) {
        return examenService.programar(id, req);
    }

    /** Registro directo de un examen ya aplicado, con su resultado (sin solicitud del alumno). */
    @PostMapping("/registro")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Registrar examen aplicado con su resultado", description = "Rol: [Coordinador]")
    public ResponseEntity<ExamenDTO> registrar(@Valid @RequestBody RegistroExamenRequest req, Principal principal) {
        ExamenDTO dto = examenService.registrarDirecto(req, principal != null ? principal.getName() : null);
        return ResponseEntity.created(URI.create("/api/v1/examenes/" + dto.id())).body(dto);
    }

    /** Edita el tipo y la fecha de aplicación del examen. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Editar datos del examen", description = "Rol: [Coordinador]")
    public ExamenDTO editar(@PathVariable Integer id, @Valid @RequestBody EdicionExamenRequest req) {
        return examenService.editar(id, req);
    }

    /** Borrado lógico: el examen se oculta, no se elimina de la base de datos. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Ocultar examen (borrado lógico)", description = "Rol: [Coordinador]")
    public ResponseEntity<Void> ocultar(@PathVariable Integer id) {
        examenService.ocultar(id);
        return ResponseEntity.noContent().build();
    }

    /** CU-06: Capturar o corregir el resultado asignando directamente el nivel. */
    @PutMapping("/{id}/resultado")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Capturar o corregir resultado (nivel asignado)", description = "Rol: [Coordinador]")
    public ExamenDTO capturarResultado(
            @PathVariable Integer id,
            @Valid @RequestBody CapturaResultadoRequest req,
            Principal principal) {
        String evaluador = principal != null ? principal.getName() : null;
        return examenService.capturarResultado(id, req, evaluador);
    }

    /** Listado de exámenes con filtros opcionales. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ALUMNO','COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Listar exámenes", description = "Rol: [Alumno][Coordinador][Jefa DGTV: solo consulta]")
    public List<ExamenDTO> listar(
            @RequestParam(required = false) String numeroControl,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) Boolean pendientes,
            Authentication authentication) {
        boolean coordinador = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_COORDINADOR")
                        || authority.getAuthority().equals("ROLE_JEFA_DGTV"));
        if (!coordinador && numeroControl != null && !numeroControl.isBlank()
                && !numeroControl.equals(authentication.getName())) {
            throw new AccessDeniedException("Solo puedes consultar tus propios exámenes.");
        }
        String numeroControlVisible = coordinador ? numeroControl : authentication.getName();
        return examenService.listar(numeroControlVisible, tipo, pendientes);
    }

    /** Detalle de un examen por ID. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ALUMNO','COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Detalle de un examen", description = "Rol: [Alumno][Coordinador][Jefa DGTV: solo consulta]")
    public ExamenDTO obtenerPorId(@PathVariable Integer id, Authentication authentication) {
        ExamenDTO examen = examenService.obtenerPorId(id);
        boolean coordinador = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_COORDINADOR")
                        || authority.getAuthority().equals("ROLE_JEFA_DGTV"));
        if (!coordinador && !examen.numeroControl().equals(authentication.getName())) {
            throw new AccessDeniedException("Solo puedes consultar tus propios exámenes.");
        }
        return examen;
    }

    /** Catálogo de criterios de evaluación de la rúbrica. */
    @GetMapping("/criterios")
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Criterios de la rúbrica", description = "Rol: [Coordinador][Jefa DGTV: solo consulta]")
    public List<CriterioEvaluacion> listarCriterios() {
        return examenService.listarCriterios();
    }
}
