package mx.edu.itch.sigclem.grupos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import mx.edu.itch.sigclem.grupos.dto.AsignarAlumnoRequest;
import mx.edu.itch.sigclem.grupos.dto.DetalleGrupoDTO;
import mx.edu.itch.sigclem.grupos.dto.ObservacionRequest;
import mx.edu.itch.sigclem.auditoria.domain.AccionAuditoria;
import mx.edu.itch.sigclem.auditoria.domain.RegistroAuditoria;
import mx.edu.itch.sigclem.grupos.dto.OfertaDTO;
import mx.edu.itch.sigclem.grupos.service.InscripcionGrupoService;
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
import mx.edu.itch.sigclem.grupos.dto.ActaDTO;
import mx.edu.itch.sigclem.grupos.dto.CalificacionesRequest;
import mx.edu.itch.sigclem.grupos.dto.CrearGrupoRequest;
import mx.edu.itch.sigclem.grupos.dto.GrupoDTO;
import mx.edu.itch.sigclem.grupos.service.GrupoService;

@Tag(name = "07. Grupos y actas", description = "CU-08 a CU-10: apertura, edición y borrado de grupos, encuesta de "
        + "inicio de semestre (el alumno elige curso y horario), asignación de alumnos con cargo automático y actas.")
@RestController
@RequestMapping("/api/v1/grupos")
@RequiredArgsConstructor
public class GrupoController {

    private final GrupoService grupoService;
    private final InscripcionGrupoService inscripcionService;
    private final mx.edu.itch.sigclem.grupos.service.ActaPdfService actaPdfService;
    private final mx.edu.itch.sigclem.constancias.service.ConfiguracionConstanciaService configuracionConstancias;
    private final mx.edu.itch.sigclem.auditoria.service.AuditoriaService auditoria;

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Editar grupo y horarios", description = "Rol: [Coordinador]")
    public GrupoDTO editar(@PathVariable Integer id, @Valid @RequestBody CrearGrupoRequest req) {
        return grupoService.editar(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Eliminar grupo sin alumnos", description = "Rol: [Coordinador]")
    public ResponseEntity<Void> eliminar(@PathVariable Integer id) {
        grupoService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/alumnos")
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Alumnos asignados (con estatus de pago) e interesados del grupo",
            description = "Rol: [Coordinador][Jefa DGTV]")
    public DetalleGrupoDTO alumnos(@PathVariable Integer id) {
        return inscripcionService.detalle(id);
    }

    @PostMapping("/{id}/alumnos")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Asignar alumno al grupo (genera su cargo de pago)", description = "Rol: [Coordinador]")
    public DetalleGrupoDTO asignar(@PathVariable Integer id, @Valid @RequestBody AsignarAlumnoRequest req,
            Authentication authentication) {
        return inscripcionService.asignar(id, req.numeroControl(), authentication.getName());
    }

    @PostMapping("/{id}/acta/cierre-coordinacion")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Cerrar el acta desde coordinación con observación",
            description = "Rol: [Coordinador]. Se puede cerrar aunque haya cursos sin liquidar: a esos alumnos se les "
                    + "registra la calificación y su nivel queda pendiente hasta asignarlo de forma individual.")
    public DetalleGrupoDTO cerrarPorCoordinacion(@PathVariable Integer id, @Valid @RequestBody ObservacionRequest req,
            Authentication authentication) {
        grupoService.cerrarActaPorCoordinacion(id, req.observacion(), authentication);
        auditoria.registrar(AccionAuditoria.ACTA_CERRADA_COORDINACION, RegistroAuditoria.OK,
                "Grupo " + id + ". " + req.observacion());
        return inscripcionService.detalle(id);
    }

    @PostMapping("/{id}/alumnos/{numeroControl}/nivel")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Asignar el nivel pendiente de un alumno",
            description = "Rol: [Coordinador]. Para alumnos cuyo curso no estaba liquidado al cerrar el acta: aplica el "
                    + "resultado de su calificación y registra la observación.")
    public DetalleGrupoDTO asignarNivel(@PathVariable Integer id, @PathVariable String numeroControl,
            @Valid @RequestBody ObservacionRequest req, Authentication authentication) {
        grupoService.asignarNivelPendiente(id, numeroControl, req.observacion(), authentication);
        auditoria.registrar(AccionAuditoria.NIVEL_ASIGNADO, RegistroAuditoria.OK,
                "Grupo " + id + ", alumno " + numeroControl + ". " + req.observacion());
        return inscripcionService.detalle(id);
    }

    @DeleteMapping("/{id}/alumnos/{numeroControl}")
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Quitar alumno del grupo", description = "Rol: [Coordinador]")
    public DetalleGrupoDTO quitar(@PathVariable Integer id, @PathVariable String numeroControl) {
        return inscripcionService.quitar(id, numeroControl);
    }

    @GetMapping("/oferta")
    @PreAuthorize("hasRole('ALUMNO')")
    @Operation(summary = "Cursos disponibles para el alumno (solo su siguiente nivel)", description = "Rol: [Alumno]")
    public OfertaDTO oferta(Authentication authentication) {
        return inscripcionService.oferta(authentication.getName());
    }

    @PutMapping("/{id}/eleccion")
    @PreAuthorize("hasRole('ALUMNO')")
    @Operation(summary = "Elegir este curso y horario (encuesta de inicio de semestre)", description = "Rol: [Alumno]")
    public OfertaDTO elegir(@PathVariable Integer id, Authentication authentication) {
        return inscripcionService.elegir(id, authentication.getName());
    }

    @DeleteMapping("/{id}/eleccion")
    @PreAuthorize("hasRole('ALUMNO')")
    @Operation(summary = "Retirar mi elección de este grupo", description = "Rol: [Alumno]")
    public OfertaDTO cancelarEleccion(@PathVariable Integer id, Authentication authentication) {
        return inscripcionService.cancelarEleccion(id, authentication.getName());
    }

    @PostMapping
    @PreAuthorize("hasRole('COORDINADOR')")
    @Operation(summary = "Abrir grupo con horarios", description = "Rol: [Coordinador]")
    public ResponseEntity<GrupoDTO> abrir(@Valid @RequestBody CrearGrupoRequest req) {
        GrupoDTO creado = grupoService.abrir(req);
        return ResponseEntity.created(URI.create("/api/v1/grupos/" + creado.id())).body(creado);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('FACILITADOR','COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Listar grupos de un periodo (por omisión el vigente)", description = "Rol: [Facilitador][Coordinador][Jefa DGTV]")
    public List<GrupoDTO> listar(
            @RequestParam(required = false) Integer periodoId,
            Authentication authentication) {
        return grupoService.listar(periodoId, authentication);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('FACILITADOR','COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Consultar acta del grupo", description = "Rol: [Facilitador][Coordinador][Jefa DGTV]")
    public ActaDTO obtenerActa(@PathVariable Integer id, Authentication authentication) {
        return grupoService.obtenerActa(id, authentication);
    }

    @GetMapping("/{id}/acta/pdf")
    @PreAuthorize("hasAnyRole('FACILITADOR','COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Descargar acta de calificaciones en PDF",
            description = "Rol: [Facilitador] (solo sus grupos)[Coordinador][Jefa DGTV]. Si no está cerrada sale como preliminar.")
    public ResponseEntity<byte[]> actaPdf(@PathVariable Integer id, Authentication authentication) {
        ActaDTO acta = grupoService.obtenerActa(id, authentication);
        byte[] pdf;
        try {
            pdf = actaPdfService.generar(acta, configuracionConstancias.membreteVigente());
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("No se pudo generar el acta en PDF.", e);
        }
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"acta-"
                        + acta.grupo().nivel().replace(' ', '-') + "-" + acta.grupo().clave() + ".pdf\"")
                .body(pdf);
    }

    @PutMapping("/{id}/acta")
    @PreAuthorize("hasRole('FACILITADOR')")
    @Operation(summary = "Guardar calificaciones del acta", description = "Rol: [Facilitador]")
    public ActaDTO guardarCalificaciones(
            @PathVariable Integer id,
            @Valid @RequestBody CalificacionesRequest req,
            Authentication authentication) {
        return grupoService.guardarCalificaciones(id, req, authentication);
    }

    @PostMapping("/{id}/acta/cierre")
    @PreAuthorize("hasRole('FACILITADOR')")
    @Operation(summary = "Cerrar acta (actualiza el avance)", description = "Rol: [Facilitador]")
    public ActaDTO cerrarActa(@PathVariable Integer id, Authentication authentication) {
        return grupoService.cerrarActa(id, authentication);
    }
}
