package mx.edu.itch.sigclem.constancias.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.constancias.domain.ImagenConstancia;
import mx.edu.itch.sigclem.constancias.dto.ConfiguracionConstanciaDTO;
import mx.edu.itch.sigclem.constancias.dto.ConstanciaAvanceDTO;
import mx.edu.itch.sigclem.constancias.dto.ConstanciaDTO;
import mx.edu.itch.sigclem.constancias.service.ConfiguracionConstanciaService;
import mx.edu.itch.sigclem.constancias.service.ConstanciaAvancePdfService;
import mx.edu.itch.sigclem.constancias.service.ConstanciaPdfService;

@Tag(name = "12. Firmas y membrete de constancias",
        description = "Firmantes, textos del oficio e imágenes del membrete del año que usan las constancias.")
@RestController
@RequestMapping("/api/v1/constancias/configuracion")
@RequiredArgsConstructor
public class ConfiguracionConstanciaController {

    private final ConfiguracionConstanciaService service;
    private final ConstanciaPdfService acreditacionPdf;
    private final ConstanciaAvancePdfService avancePdf;

    @GetMapping
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Consultar firmantes, textos e imágenes cargadas", description = "Rol: [Coordinador][Jefa DGTV]")
    public ConfiguracionConstanciaDTO obtener() {
        return service.obtener();
    }

    @PutMapping
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Actualizar firmantes y textos del oficio", description = "Rol: [Coordinador]")
    public ConfiguracionConstanciaDTO actualizar(
            @Valid @RequestBody ConfiguracionConstanciaDTO.Request request, Authentication authentication) {
        return service.actualizar(request, authentication.getName());
    }

    @PutMapping(value = "/imagenes/{tipo}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Reemplazar imagen del membrete (encabezado, pie o logo-coordinacion)",
            description = "Rol: [Coordinador]. PNG o JPG de hasta 2 MB.")
    public ConfiguracionConstanciaDTO guardarImagen(
            @PathVariable String tipo, @RequestParam("archivo") MultipartFile archivo) {
        service.guardarImagen(ConfiguracionConstanciaService.tipoDesdeRuta(tipo), archivo);
        return service.obtener();
    }

    @GetMapping("/imagenes/{tipo}")
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Ver imagen del membrete", description = "Rol: [Coordinador][Jefa DGTV]")
    public ResponseEntity<byte[]> imagen(@PathVariable String tipo) {
        ImagenConstancia imagen = service.obtenerImagen(ConfiguracionConstanciaService.tipoDesdeRuta(tipo));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(imagen.getTipoContenido()))
                .body(imagen.getContenido());
    }

    @DeleteMapping("/imagenes/{tipo}")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Quitar imagen del membrete (vuelve a la predeterminada)", description = "Rol: [Coordinador]")
    public ConfiguracionConstanciaDTO eliminarImagen(@PathVariable String tipo) {
        service.eliminarImagen(ConfiguracionConstanciaService.tipoDesdeRuta(tipo));
        return service.obtener();
    }

    @GetMapping("/vista-previa/{formato}")
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Vista previa en PDF con datos de ejemplo (acreditacion o avance)",
            description = "Rol: [Coordinador][Jefa DGTV]")
    public ResponseEntity<byte[]> vistaPrevia(@PathVariable String formato) {
        LocalDate hoy = LocalDate.now();
        byte[] pdf;
        try {
            pdf = switch (formato) {
                case "acreditacion" -> acreditacionPdf.generar(new ConstanciaDTO(0, "00000000",
                        "Nombre del Alumno de Ejemplo", "Ingeniería en Sistemas Computacionales", "ISIC-2010-224",
                        "0000", hoy, hoy, "Inglés", 10, 450, "B1", "Emitida", null));
                case "avance" -> avancePdf.generar(new ConstanciaAvanceDTO(0, "00000000",
                        "Nombre del Alumno de Ejemplo", "Ingeniería en Sistemas Computacionales", 1, "Básico 1",
                        "Periodo de ejemplo", "A", "Nombre del Facilitador", 100, hoy));
                default -> throw new RecursoNoEncontradoException("CFG-005",
                        "Formato no válido: usa acreditacion o avance.");
            };
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la vista previa.", e);
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"vista-previa-" + formato + ".pdf\"")
                .body(pdf);
    }
}
