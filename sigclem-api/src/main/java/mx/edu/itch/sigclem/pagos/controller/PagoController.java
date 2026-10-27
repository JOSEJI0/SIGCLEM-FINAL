package mx.edu.itch.sigclem.pagos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.pagos.dto.AutorizacionDTO;
import mx.edu.itch.sigclem.pagos.dto.AutorizacionRequest;
import mx.edu.itch.sigclem.pagos.dto.ComprobanteDTO;
import mx.edu.itch.sigclem.pagos.dto.ConceptoDTO;
import mx.edu.itch.sigclem.pagos.dto.ConceptoRequest;
import mx.edu.itch.sigclem.pagos.dto.DatosComprobanteRequest;
import mx.edu.itch.sigclem.pagos.dto.RechazoRequest;
import mx.edu.itch.sigclem.pagos.dto.TarifaDTO;
import mx.edu.itch.sigclem.pagos.dto.TarifaRequest;
import mx.edu.itch.sigclem.pagos.dto.ValidarComprobanteRequest;
import mx.edu.itch.sigclem.pagos.service.PagoService;

@Tag(name = "08. Pagos e inscripciones", description = "CU-11 a CU-13: conceptos, tarifas, cargos, comprobantes e inscripción.")
@RestController
@RequestMapping("/api/v1/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagoService;

    @GetMapping("/conceptos")
    @PreAuthorize("hasAnyRole('ALUMNO','COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Listar conceptos de pago", description = "Rol: [Alumno][Coordinador][Jefa DGTV]")
    public List<ConceptoDTO> conceptos(
            @RequestParam(defaultValue = "false") boolean incluirInactivos,
            Authentication authentication) {
        boolean coordinacion = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_COORDINADOR")
                        || a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_JEFA_DGTV"));
        return pagoService.listarConceptos(incluirInactivos && coordinacion);
    }

    @PostMapping("/conceptos")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Crear concepto de pago con su monto", description = "Rol: [Coordinador]")
    public ResponseEntity<ConceptoDTO> crearConcepto(@Valid @RequestBody ConceptoRequest request) {
        return ResponseEntity.status(201).body(pagoService.crearConcepto(request));
    }

    @PutMapping("/conceptos/{id}")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Editar nombre y monto del concepto", description = "Rol: [Coordinador]")
    public ConceptoDTO editarConcepto(@PathVariable Integer id, @Valid @RequestBody ConceptoRequest request) {
        return pagoService.editarConcepto(id, request);
    }

    @DeleteMapping("/conceptos/{id}")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Eliminar concepto sin cargos emitidos", description = "Rol: [Coordinador]")
    public ResponseEntity<Void> eliminarConcepto(@PathVariable Integer id) {
        pagoService.eliminarConcepto(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/conceptos/{id}/activar")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Reactivar concepto", description = "Rol: [Coordinador]")
    public ConceptoDTO activarConcepto(@PathVariable Integer id) {
        return pagoService.activarConcepto(id);
    }

    @PatchMapping("/conceptos/{id}/desactivar")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Desactivar concepto", description = "Rol: [Coordinador]")
    public ConceptoDTO desactivarConcepto(@PathVariable Integer id) {
        return pagoService.desactivarConcepto(id);
    }

    @GetMapping("/tarifas")
    @PreAuthorize("hasAnyRole('ALUMNO','COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Listar tarifas", description = "Rol: [Alumno][Coordinador]")
    public List<TarifaDTO> tarifas(
            @RequestParam(defaultValue = "false") boolean incluirInactivas,
            Authentication authentication) {
        boolean coordinacion = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_COORDINADOR")
                        || a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_JEFA_DGTV"));
        return pagoService.listarTarifas(incluirInactivas && coordinacion);
    }

    @PostMapping("/tarifas")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Crear tarifa", description = "Rol: [Coordinador]")
    public ResponseEntity<TarifaDTO> crearTarifa(@Valid @RequestBody TarifaRequest request) {
        return ResponseEntity.status(201).body(pagoService.crearTarifa(request));
    }

    @PatchMapping("/tarifas/{id}/desactivar")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Desactivar tarifa", description = "Rol: [Coordinador]")
    public TarifaDTO desactivarTarifa(@PathVariable Integer id) {
        return pagoService.desactivarTarifa(id);
    }

    @GetMapping("/autorizaciones")
    @PreAuthorize("hasRole('ALUMNO')")
    @Operation(summary = "Mis cargos / autorizaciones de pago", description = "Rol: [Alumno]")
    public List<AutorizacionDTO> autorizaciones(Authentication authentication) {
        return pagoService.listarAutorizaciones(authentication);
    }

    @GetMapping("/cargos")
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Listado general de cargos", description = "Rol: [Coordinador][Jefa DGTV]. Filtro opcional: estatus "
            + "(Pendiente, Parcial o Liquidada).")
    public List<mx.edu.itch.sigclem.pagos.dto.CargoResumenDTO> todosLosCargos(@RequestParam(required = false) String estatus) {
        return pagoService.listarTodosLosCargos(estatus);
    }

    @GetMapping("/alumnos/{numeroControl}/cargos")
    @PreAuthorize("hasAnyRole('ALUMNO','COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Cargos de un alumno", description = "Rol: [Alumno][Coordinador][Jefa DGTV]")
    public List<AutorizacionDTO> cargos(
            @PathVariable String numeroControl, Authentication authentication) {
        boolean personalAutorizado = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_COORDINADOR")
                        || a.getAuthority().equals("ROLE_JEFA_DGTV"));
        return pagoService.listarCargos(numeroControl, authentication, personalAutorizado);
    }

    @PostMapping({ "/autorizaciones", "/autorizaciones-pago" })
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Emitir cargo (autorización de pago) a un alumno", description = "Rol: [Coordinador]")
    public ResponseEntity<AutorizacionDTO> crearAutorizacion(
            @Valid @RequestBody AutorizacionRequest request, Authentication authentication) {
        return ResponseEntity.status(201).body(pagoService.crearAutorizacion(request, authentication));
    }

    @PostMapping(value = "/autorizaciones/{id}/comprobante", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ALUMNO')")
    @Operation(summary = "Subir comprobante (PDF, JPG o PNG) de un cargo", description = "Rol: [Alumno]. El servidor lee el QR, "
            + "consulta el CFDI en el SAT y coteja la descripción del recibo con el alumno y el concepto.")
    public ResponseEntity<ComprobanteDTO> cargarComprobante(
            @PathVariable Integer id,
            @RequestParam(required = false) String uuidCfdi,
            @RequestParam(required = false) String rfcEmisor,
            @RequestParam(required = false) String total,
            @RequestParam(required = false) String contenidoQr,
            @RequestParam("archivo") MultipartFile archivo,
            Authentication authentication) {
        ComprobanteDTO creado = pagoService.cargarComprobante(
                id, uuidCfdi, rfcEmisor, total, contenidoQr, archivo, authentication);
        return ResponseEntity.status(201).body(creado);
    }

    @PostMapping(value = "/comprobantes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ALUMNO')")
    @Operation(summary = "Subir comprobante (ruta alterna)", description = "Rol: [Alumno]")
    public ResponseEntity<ComprobanteDTO> registrarComprobante(
            @RequestParam Integer idAutorizacion,
            @RequestParam(required = false) String uuidCfdi,
            @RequestParam(required = false) String rfcEmisor,
            @RequestParam(required = false) String total,
            @RequestParam(required = false) String contenidoQr,
            @RequestParam("archivo") MultipartFile archivo,
            Authentication authentication) {
        ComprobanteDTO creado = pagoService.cargarComprobante(
                idAutorizacion, uuidCfdi, rfcEmisor, total, contenidoQr, archivo, authentication);
        return ResponseEntity.status(201).body(creado);
    }

    @GetMapping({ "/comprobantes", "/comprobantes/pendientes" })
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Listar comprobantes por estatus", description = "Rol: [Coordinador][Jefa DGTV]")
    public List<ComprobanteDTO> comprobantesPendientes(
            @RequestParam(required = false) String estatus) {
        return pagoService.listarPendientes(estatus);
    }

    @PostMapping("/comprobantes/{id}/captura-manual")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Capturar UUID, RFC y total del CFDI", description = "Rol: [Coordinador]")
    public ComprobanteDTO capturarDatosManuales(
            @PathVariable Integer id,
            @Valid @RequestBody DatosComprobanteRequest request,
            Authentication authentication) {
        return pagoService.capturarDatosManuales(id, request, authentication);
    }

    @PostMapping("/comprobantes/{id}/reverificar")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Repetir lectura del QR, consulta al SAT y cotejo", description = "Rol: [Coordinador]")
    public ComprobanteDTO reverificarComprobante(@PathVariable Integer id) {
        return pagoService.reverificarComprobante(id);
    }

    @PostMapping({ "/comprobantes/{id}/validar", "/comprobantes/{id}/validacion" })
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Validar comprobante (e inscribir si liquida el curso)", description = "Rol: [Coordinador]")
    public ComprobanteDTO validarComprobante(
            @PathVariable Integer id,
            @Valid @RequestBody(required = false) ValidarComprobanteRequest request,
            Authentication authentication) {
        return pagoService.validarComprobante(id, request, authentication);
    }

    @PostMapping({ "/comprobantes/{id}/rechazar", "/comprobantes/{id}/rechazo" })
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Rechazar comprobante", description = "Rol: [Coordinador]")
    public ComprobanteDTO rechazarComprobante(
            @PathVariable Integer id,
            @Valid @RequestBody RechazoRequest request,
            Authentication authentication) {
        return pagoService.rechazarComprobante(id, request, authentication);
    }

    @GetMapping("/comprobantes/{id}/archivo")
    @PreAuthorize("hasAnyRole('ALUMNO','COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Descargar archivo del comprobante", description = "Rol: [Alumno][Coordinador][Jefa DGTV]")
    public ResponseEntity<Resource> archivo(
            @PathVariable Integer id, Authentication authentication) {
        boolean coordinacion = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_COORDINADOR")
                        || a.getAuthority().equals("ROLE_JEFA_DGTV"));
        Resource archivo = pagoService.obtenerArchivo(id, authentication, coordinacion);
        String nombre = archivo.getFilename() == null ? "" : archivo.getFilename();
        String extension = nombre.endsWith(".pdf") ? "pdf" : nombre.endsWith(".png") ? "png" : "jpg";
        MediaType tipo = extension.equals("pdf") ? MediaType.APPLICATION_PDF
                : extension.equals("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(tipo)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"comprobante-" + id + "." + extension + "\"")
                .body(archivo);
    }
}
