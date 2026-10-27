package mx.edu.itch.sigclem.lenguasmaternas.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.lenguasmaternas.dto.EstatusLenguaRequest;
import mx.edu.itch.sigclem.lenguasmaternas.dto.LenguaMaternaDTO;
import mx.edu.itch.sigclem.lenguasmaternas.dto.LenguaMaternaRequest;
import mx.edu.itch.sigclem.lenguasmaternas.dto.RegistroLenguaDTO;
import mx.edu.itch.sigclem.lenguasmaternas.dto.RegistroLenguaRequest;
import mx.edu.itch.sigclem.lenguasmaternas.service.LenguaMaternaService;

@Tag(name = "11. Lenguas maternas", description = "CU-18 y CU-19: catálogo, registro con documentos (CURP, Domicilio, INE) y revisión.")
@RestController
@RequestMapping("/api/v1/lenguas-maternas")
@RequiredArgsConstructor
public class LenguaMaternaController {

    private final LenguaMaternaService lenguaService;

    @GetMapping("/catalogo")
    @PreAuthorize("hasAnyRole('ALUMNO','COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Catálogo de lenguas maternas", description = "Rol: [Alumno][Coordinador][Jefa DGTV: solo consulta]")
    public List<LenguaMaternaDTO> listarLenguas() {
        return lenguaService.listarLenguas();
    }

    @PostMapping("/catalogo")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Agregar lengua al catálogo", description = "Rol: [Coordinador]")
    public ResponseEntity<LenguaMaternaDTO> crearLengua(@Valid @RequestBody LenguaMaternaRequest solicitud) {
        LenguaMaternaDTO creada = lenguaService.crearLengua(solicitud);
        return ResponseEntity.created(URI.create("/api/v1/lenguas-maternas/catalogo/" + creada.id())).body(creada);
    }

    @GetMapping("/registros/mios")
    @PreAuthorize("hasRole('ALUMNO')")
    @Operation(summary = "Mis registros de lengua materna", description = "Rol: [Alumno]")
    public List<RegistroLenguaDTO> listarPropios(Authentication authentication) {
        return lenguaService.listarPropios(authentication.getName());
    }

    @PostMapping(value = "/registros", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ALUMNO')")
    @Operation(summary = "Registrar lengua con CURP, comprobante de domicilio e INE", description = "Rol: [Alumno]")
    public ResponseEntity<RegistroLenguaDTO> registrar(
            @RequestParam Integer idLengua,
            @RequestParam(required = false) String nivelDominio,
            @RequestPart(value = "documento", required = false) MultipartFile documento,
            @RequestPart(value = "curp", required = false) MultipartFile curp,
            @RequestPart(value = "domicilio", required = false) MultipartFile domicilio,
            @RequestPart(value = "ine", required = false) MultipartFile ine,
            Authentication authentication) {
        if (nivelDominio != null && nivelDominio.trim().length() > 20) {
            throw new DatosInvalidosException("LENG-013", "El nivel de dominio no puede exceder 20 caracteres.");
        }
        RegistroLenguaRequest solicitud = new RegistroLenguaRequest(idLengua, nivelDominio);
        LenguaMaternaService.ResultadoRegistro resultado =
                lenguaService.registrar(solicitud, documento, curp, domicilio, ine, authentication.getName());
        URI ubicacion = URI.create("/api/v1/lenguas-maternas/registros/" + resultado.registro().id());
        return ResponseEntity.status(resultado.actualizado() ? 200 : 201).location(ubicacion)
                .body(resultado.registro());
    }

    @PostMapping(value = "/registros/{id}/documentos-coordinacion", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Subir documento intercultural y/o constancia de acreditación", description = "Rol: [Coordinador]")
    public RegistroLenguaDTO subirDocumentosCoordinacion(
            @PathVariable Integer id,
            @RequestPart(value = "intercultural", required = false) MultipartFile intercultural,
            @RequestPart(value = "constancia", required = false) MultipartFile constancia,
            Authentication authentication) {
        return lenguaService.subirDocumentosCoordinacion(id, intercultural, constancia, authentication.getName());
    }

    @GetMapping("/registros")
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Listar registros", description = "Rol: [Coordinador][Jefa DGTV: solo consulta]")
    public List<RegistroLenguaDTO> listarRegistros() {
        return lenguaService.listarRegistros();
    }

    @PatchMapping("/registros/{id}/estatus")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Validar o rechazar registro", description = "Rol: [Coordinador]")
    public RegistroLenguaDTO cambiarEstatus(
            @PathVariable Integer id,
            @Valid @RequestBody EstatusLenguaRequest solicitud,
            Authentication authentication) {
        return lenguaService.cambiarEstatus(id, solicitud, authentication.getName());
    }

    @GetMapping({ "/registros/{id}/documento", "/registros/{id}/documento/{tipo}" })
    @PreAuthorize("hasAnyRole('ALUMNO','COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Ver documento del registro (curp, domicilio, ine, intercultural, constancia)",
            description = "Rol: [Alumno][Coordinador][Jefa DGTV: solo consulta]")
    public ResponseEntity<Resource> obtenerDocumento(
            @PathVariable Integer id,
            @PathVariable(required = false) String tipo,
            Authentication authentication) {
        boolean coordinacion = authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_COORDINADOR")
                        || authority.getAuthority().equals("ROLE_ADMIN")
                        || authority.getAuthority().equals("ROLE_JEFA_DGTV"));
        LenguaMaternaService.DocumentoLengua documento = lenguaService.obtenerDocumento(
                id, tipo, authentication.getName(), coordinacion);
        String extension = documento.tipoContenido().contains("pdf") ? ".pdf" : documento.tipoContenido().contains("png") ? ".png" : ".jpg";
        String filename = "lengua-materna-" + id + (tipo != null ? "-" + tipo : "") + extension;
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(documento.tipoContenido()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(documento.recurso());
    }
}
