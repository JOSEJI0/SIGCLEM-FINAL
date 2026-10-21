package mx.edu.itch.sigclem.catalogos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.catalogos.dto.CarreraDTO;
import mx.edu.itch.sigclem.catalogos.dto.NivelDTO;
import mx.edu.itch.sigclem.catalogos.dto.ModalidadDTO;
import mx.edu.itch.sigclem.catalogos.repository.ModalidadRepository;
import mx.edu.itch.sigclem.catalogos.repository.CarreraRepository;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;

/** Catálogos de solo lectura (Tabla 66: GET /catalogos/{tipo}). */
@Tag(name = "02. Salud y catálogos")
@RestController
@RequestMapping("/api/v1/catalogos")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogoController {

    private final CarreraRepository carreraRepository;
    private final NivelRepository nivelRepository;
    private final ModalidadRepository modalidadRepository;

    @Operation(summary = "Listar carreras", description = "Rol: [Público]")
    @GetMapping("/carreras")
    public List<CarreraDTO> carreras() {
        return carreraRepository.findAll(Sort.by("nombre")).stream().map(CarreraDTO::desde).toList();
    }

    @Operation(summary = "Listar los diez niveles de inglés", description = "Rol: [Público]")
    @GetMapping("/niveles")
    public List<NivelDTO> niveles() {
        return nivelRepository.findAllByOrderByNumeroAsc().stream().map(NivelDTO::desde).toList();
    }

    @Operation(summary = "Listar modalidades", description = "Rol: [Público]")
    @GetMapping("/modalidades")
    public List<ModalidadDTO> modalidades() {
        return modalidadRepository.findAllByOrderByNombreAsc().stream().map(ModalidadDTO::desde).toList();
    }
}
