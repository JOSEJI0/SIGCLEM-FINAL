package mx.edu.itch.sigclem.catalogos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.catalogos.domain.Aula;
import mx.edu.itch.sigclem.catalogos.dto.AulaDTO;
import mx.edu.itch.sigclem.catalogos.dto.AulaRequest;
import mx.edu.itch.sigclem.catalogos.repository.AulaRepository;
import mx.edu.itch.sigclem.comun.excepcion.ConflictoException;

@Tag(name = "02. Salud y catálogos")
@RestController
@RequestMapping("/api/v1/catalogos/aulas")
@RequiredArgsConstructor
public class AulaController {

    private final AulaRepository aulaRepository;

    @Operation(summary = "Listar aulas", description = "Rol: [Público]")
    @GetMapping
    public List<AulaDTO> listar() {
        return aulaRepository.findAllByOrderByNombreAsc().stream().map(AulaDTO::desde).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Registrar aula", description = "Rol: [Coordinador]")
    public ResponseEntity<AulaDTO> crear(@Valid @RequestBody AulaRequest req) {
        String nombre = req.nombre().trim();
        if (aulaRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ConflictoException("AULA-001", "Ya existe un aula con ese nombre.");
        }
        Aula aula = new Aula();
        aula.setNombre(nombre);
        Aula creada = aulaRepository.save(aula);
        return ResponseEntity.created(URI.create("/api/v1/catalogos/aulas/" + creada.getId()))
                .body(AulaDTO.desde(creada));
    }
}
