package mx.edu.itch.sigclem.auditoria.controller;

import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.auditoria.domain.AccionAuditoria;
import mx.edu.itch.sigclem.auditoria.dto.RegistroAuditoriaDTO;
import mx.edu.itch.sigclem.auditoria.repository.RegistroAuditoriaRepository;

/** CU-30 Auditoría: solo el administrador consulta la bitácora de acciones sensibles. */
@Tag(name = "30. Auditoría", description = "Bitácora de acciones sensibles. Rol: [Administrador].")
@RestController
@RequestMapping("/api/v1/auditoria")
@RequiredArgsConstructor
public class AuditoriaController {

    private final RegistroAuditoriaRepository repository;

    @Operation(summary = "Consultar la bitácora de auditoría (paginada)",
            description = "Rol: [Administrador]. Filtros opcionales: usuario y accion.")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<RegistroAuditoriaDTO> listar(
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String accion,
            @PageableDefault(size = 50) Pageable pageable) {
        return repository.buscar(usuario == null ? "" : usuario.trim(), accion == null ? "" : accion.trim(), pageable)
                .map(RegistroAuditoriaDTO::desde);
    }

    @Operation(summary = "Catálogo de acciones auditadas", description = "Rol: [Administrador]. Para el filtro de la pantalla.")
    @GetMapping("/acciones")
    @PreAuthorize("hasRole('ADMIN')")
    public List<Accion> acciones() {
        return Arrays.stream(AccionAuditoria.values()).map(a -> new Accion(a.name(), a.descripcion())).toList();
    }

    public record Accion(String codigo, String descripcion) {
    }
}
