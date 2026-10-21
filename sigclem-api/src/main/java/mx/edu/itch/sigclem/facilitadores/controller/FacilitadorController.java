package mx.edu.itch.sigclem.facilitadores.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
import mx.edu.itch.sigclem.facilitadores.dto.FacilitadorCreadoDTO;
import mx.edu.itch.sigclem.facilitadores.dto.FacilitadorDTO;
import mx.edu.itch.sigclem.facilitadores.dto.TelefonoRequest;
import mx.edu.itch.sigclem.grupos.dto.GrupoDTO;
import mx.edu.itch.sigclem.facilitadores.dto.FacilitadorRequest;
import mx.edu.itch.sigclem.facilitadores.service.FacilitadorService;

@Tag(name = "06. Facilitadores", description = "CU-07: alta, consulta, edición y desactivación.")
@RestController
@RequestMapping("/api/v1/facilitadores")
@RequiredArgsConstructor
public class FacilitadorController {

    private final FacilitadorService facilitadorService;
    private final mx.edu.itch.sigclem.grupos.service.GrupoService grupoService;

    @GetMapping("/yo")
    @PreAuthorize("hasRole('FACILITADOR')")
    @Operation(summary = "Mis datos de facilitador", description = "Rol: [Facilitador]")
    public FacilitadorDTO propio(Authentication authentication) {
        return facilitadorService.propio(authentication.getName());
    }

    @PutMapping("/yo/telefono")
    @PreAuthorize("hasRole('FACILITADOR')")
    @Operation(summary = "Actualizar mi teléfono", description = "Rol: [Facilitador]")
    public FacilitadorDTO actualizarTelefono(Authentication authentication,
            @Valid @RequestBody TelefonoRequest req) {
        return facilitadorService.actualizarTelefono(authentication.getName(), req.telefono());
    }

    @GetMapping("/yo/grupos")
    @PreAuthorize("hasRole('FACILITADOR')")
    @Operation(summary = "Mis grupos de todos los periodos", description = "Rol: [Facilitador]")
    public List<GrupoDTO> misGrupos(Authentication authentication) {
        return grupoService.listarDeFacilitador(facilitadorService.propioEntidad(authentication.getName()).getId());
    }

    @GetMapping("/{id}/grupos")
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Grupos y horarios del facilitador", description = "Rol: [Coordinador][Jefa DGTV]")
    public List<mx.edu.itch.sigclem.grupos.dto.GrupoDTO> grupos(@PathVariable Integer id) {
        return grupoService.listarDeFacilitador(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Registrar facilitador (con contraseña asignada o temporal)", description = "Rol: [Coordinador]")
    public ResponseEntity<FacilitadorCreadoDTO> registrar(@Valid @RequestBody FacilitadorRequest req) {
        FacilitadorCreadoDTO creado = facilitadorService.registrar(req);
        return ResponseEntity.created(URI.create("/api/v1/facilitadores/" + creado.facilitador().id()))
                .body(creado);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Listar facilitadores", description = "Rol: [Coordinador][Jefa DGTV]")
    public List<FacilitadorDTO> listar() {
        return facilitadorService.listar();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Editar facilitador", description = "Rol: [Coordinador]")
    public FacilitadorDTO actualizar(@PathVariable Integer id, @Valid @RequestBody FacilitadorRequest req) {
        return facilitadorService.actualizar(id, req);
    }

    @PutMapping("/{id}/contrasena")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Asignar nueva contraseña al facilitador", description = "Rol: [Coordinador]")
    public FacilitadorDTO cambiarContrasena(@PathVariable Integer id,
            @Valid @RequestBody mx.edu.itch.sigclem.facilitadores.dto.ContrasenaRequest req) {
        return facilitadorService.cambiarContrasena(id, req.contrasena());
    }

    @PutMapping("/{id}/estado")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Activar o desactivar facilitador", description = "Rol: [Coordinador]")
    public FacilitadorDTO cambiarEstado(@PathVariable Integer id, @RequestParam boolean activo) {
        return facilitadorService.cambiarEstado(id, activo);
    }
}
