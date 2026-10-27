package mx.edu.itch.sigclem.seguridad.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.auditoria.domain.AccionAuditoria;
import mx.edu.itch.sigclem.auditoria.domain.RegistroAuditoria;
import mx.edu.itch.sigclem.auditoria.service.AuditoriaService;
import mx.edu.itch.sigclem.seguridad.service.CuentaService;

@Tag(name = "01. Autenticación", description = "Inicio de sesión, perfil y contraseñas administradas por coordinación.")
@RestController
@RequestMapping("/api/v1/cuentas")
@RequiredArgsConstructor
public class CuentaController {

    private final CuentaService cuentaService;
    private final AuditoriaService auditoria;

    public record CambioPropioRequest(@NotBlank String contrasenaActual, @NotBlank String contrasenaNueva) {
    }

    public record AsignacionRequest(@NotBlank String contrasena) {
    }

    @PutMapping("/mi-contrasena")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cambiar mi contraseña", description = "Rol: [Cualquier usuario autenticado]. Requiere la contraseña actual.")
    public ResponseEntity<Void> cambiarPropia(@Valid @RequestBody CambioPropioRequest req, Authentication authentication) {
        cuentaService.cambiarPropia(authentication.getName(), req.contrasenaActual(), req.contrasenaNueva());
        auditoria.registrar(AccionAuditoria.CONTRASENA_PROPIA, RegistroAuditoria.OK, null);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/alumnos/{numeroControl}/contrasena")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Asignar una nueva contraseña a un alumno",
            description = "Rol: [Coordinador]. Admite cualquier contraseña no vacía de hasta 72 bytes UTF-8.")
    public ResponseEntity<Void> asignarAlumno(@PathVariable String numeroControl, @Valid @RequestBody AsignacionRequest req) {
        cuentaService.asignarAlumno(numeroControl, req.contrasena());
        auditoria.registrar(AccionAuditoria.CONTRASENA_ASIGNADA, RegistroAuditoria.OK,
                "Contraseña asignada al alumno " + numeroControl.trim().toUpperCase() + ".");
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/jefa/contrasena")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Asignar una nueva contraseña a la jefa del DGTV", description = "Rol: [Coordinador]")
    public ResponseEntity<Void> asignarJefa(@Valid @RequestBody AsignacionRequest req) {
        cuentaService.asignarJefa(req.contrasena());
        auditoria.registrar(AccionAuditoria.CONTRASENA_ASIGNADA, RegistroAuditoria.OK,
                "Contraseña asignada a la jefa del DGTV.");
        return ResponseEntity.noContent().build();
    }
}
