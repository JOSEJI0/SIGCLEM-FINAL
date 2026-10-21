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
import mx.edu.itch.sigclem.catalogos.domain.Periodo;
import mx.edu.itch.sigclem.catalogos.dto.PeriodoDTO;
import mx.edu.itch.sigclem.catalogos.dto.PeriodoRequest;
import mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository;
import mx.edu.itch.sigclem.comun.excepcion.ConflictoException;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;

@Tag(name = "02. Salud y catálogos")
@RestController
@RequestMapping("/api/v1/catalogos/periodos")
@RequiredArgsConstructor
public class PeriodoController {

    private final PeriodoRepository periodoRepository;

    @Operation(summary = "Listar periodos", description = "Rol: [Público]")
    @GetMapping
    public List<PeriodoDTO> listar() {
        return periodoRepository.findAllByOrderByFechaInicioDesc().stream().map(PeriodoDTO::desde).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Registrar periodo", description = "Rol: [Coordinador]")
    public ResponseEntity<PeriodoDTO> crear(@Valid @RequestBody PeriodoRequest req) {
        String nombre = req.nombre().trim();
        if (req.fechaFin().isBefore(req.fechaInicio())) {
            throw new DatosInvalidosException("PER-001", "La fecha de fin debe ser igual o posterior al inicio.");
        }
        if (periodoRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ConflictoException("PER-002", "Ya existe un periodo con ese nombre.");
        }
        Periodo periodo = new Periodo();
        periodo.setNombre(nombre);
        periodo.setFechaInicio(req.fechaInicio());
        periodo.setFechaFin(req.fechaFin());
        Periodo creado = periodoRepository.save(periodo);
        return ResponseEntity.created(URI.create("/api/v1/catalogos/periodos/" + creado.getId()))
                .body(PeriodoDTO.desde(creado));
    }
}
