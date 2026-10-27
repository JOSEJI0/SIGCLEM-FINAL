package mx.edu.itch.sigclem.alumnos.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
import mx.edu.itch.sigclem.alumnos.dto.AlumnoResumenDTO;
import mx.edu.itch.sigclem.alumnos.dto.AvanceDTO;
import mx.edu.itch.sigclem.alumnos.dto.ExpedienteDTO;
import mx.edu.itch.sigclem.alumnos.dto.RegistroAlumnoRequest;
import mx.edu.itch.sigclem.alumnos.dto.SemestreRequest;
import mx.edu.itch.sigclem.alumnos.service.AlumnoService;

@Tag(name = "03. Alumnos", description = "CU-02, CU-03, CU-16 y CU-21: registro, expediente, avance y búsqueda.")
@RestController
@RequestMapping("/api/v1/alumnos")
@RequiredArgsConstructor
public class AlumnoController {

    private final AlumnoService alumnoService;
    private final mx.edu.itch.sigclem.alumnos.service.ExpedienteIntegralService expedienteIntegralService;
    private final mx.edu.itch.sigclem.alumnos.service.ImportacionAlumnosService importacionService;
    private final mx.edu.itch.sigclem.auditoria.service.AuditoriaService auditoria;

    @Operation(summary = "Cargar alumnos desde un archivo Excel",
            description = "Rol: [Coordinador]. Crea las cuentas (contraseña inicial = número de control) e informa qué "
                    + "filas fallaron. Usa la plantilla de /alumnos/plantilla.")
    @PostMapping(value = "/carga", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('COORDINADOR')")
    public mx.edu.itch.sigclem.alumnos.dto.ResultadoCargaDTO cargar(
            @org.springframework.web.bind.annotation.RequestParam("archivo")
            org.springframework.web.multipart.MultipartFile archivo) {
        var resultado = importacionService.importar(archivo);
        auditoria.registrar(mx.edu.itch.sigclem.auditoria.domain.AccionAuditoria.ALUMNOS_IMPORTADOS,
                mx.edu.itch.sigclem.auditoria.domain.RegistroAuditoria.OK,
                resultado.creados() + " de " + resultado.filas() + " filas creadas desde Excel.");
        return resultado;
    }

    @Operation(summary = "Descargar la plantilla de Excel para la carga de alumnos", description = "Rol: [Coordinador]")
    @GetMapping("/plantilla")
    @PreAuthorize("hasRole('COORDINADOR')")
    public ResponseEntity<byte[]> plantilla() {
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"plantilla-alumnos.xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(importacionService.plantilla());
    }

    /** Ficha única: avance, historial, pagos, exámenes, diagnósticos, constancias y lengua materna. */
    @GetMapping("/{numeroControl}/integral")
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Expediente integral del alumno", description = "Rol: [Coordinador][Jefa DGTV]")
    public mx.edu.itch.sigclem.alumnos.dto.ExpedienteIntegralDTO integral(@PathVariable String numeroControl) {
        return expedienteIntegralService.obtener(numeroControl);
    }

    /** CU-16/CU-21: buscar alumnos con filtros por partes (carrera, número de control, nombre y nivel actual). */
    @GetMapping
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Buscar alumnos (paginado)", description = "Rol: [Coordinador][Jefa DGTV]. Filtros opcionales y "
            + "combinables: idCarrera, numeroControl, nombre, semestre y nivel (1 a 10 = nivel que cursa o tiene registrado; "
            + "0 = idioma acreditado). 'busqueda' busca en todos los campos.")
    public Page<AlumnoResumenDTO> buscar(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) Integer idCarrera,
            @RequestParam(required = false) String numeroControl,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) Integer nivel,
            @RequestParam(required = false) Integer semestre,
            @RequestParam(required = false) String estatus,
            @RequestParam(required = false) String formaLiberacion,
            @PageableDefault(size = 20) Pageable pageable) {
        return alumnoService.buscar(busqueda, idCarrera, numeroControl, nombre, nivel, semestre, estatus, formaLiberacion, pageable);
    }

    /** CU-02 El coordinador crea la cuenta del alumno (ya no hay registro público; cambio 15). */
    @Operation(summary = "Crear la cuenta de un alumno",
            description = "Rol: [Coordinador]. La contraseña inicial es el número de control.")
    @PostMapping("/registro")
    @PreAuthorize("hasRole('COORDINADOR')")
    public ResponseEntity<ExpedienteDTO> registrar(@Valid @RequestBody RegistroAlumnoRequest req) {
        ExpedienteDTO creado = alumnoService.registrar(req);
        auditoria.registrar(mx.edu.itch.sigclem.auditoria.domain.AccionAuditoria.ALUMNO_CREADO,
                mx.edu.itch.sigclem.auditoria.domain.RegistroAuditoria.OK, "Alumno " + creado.numeroControl() + ".");
        return ResponseEntity.created(URI.create("/api/v1/alumnos/" + creado.numeroControl())).body(creado);
    }

    @Operation(summary = "Dar de baja a un alumno", description = "Rol: [Coordinador]. Reversible; conserva el historial.")
    @PostMapping("/{numeroControl}/baja")
    @PreAuthorize("hasRole('COORDINADOR')")
    public ExpedienteDTO darDeBaja(@PathVariable String numeroControl,
            @Valid @RequestBody mx.edu.itch.sigclem.alumnos.dto.BajaAlumnoRequest req) {
        ExpedienteDTO dto = alumnoService.darDeBaja(numeroControl, req.motivo());
        auditoria.registrar(mx.edu.itch.sigclem.auditoria.domain.AccionAuditoria.ALUMNO_BAJA,
                mx.edu.itch.sigclem.auditoria.domain.RegistroAuditoria.OK,
                "Alumno " + numeroControl + ". Motivo: " + req.motivo());
        return dto;
    }

    @Operation(summary = "Reactivar a un alumno dado de baja", description = "Rol: [Coordinador]")
    @PostMapping("/{numeroControl}/reactivar")
    @PreAuthorize("hasRole('COORDINADOR')")
    public ExpedienteDTO reactivar(@PathVariable String numeroControl) {
        ExpedienteDTO dto = alumnoService.reactivar(numeroControl);
        auditoria.registrar(mx.edu.itch.sigclem.auditoria.domain.AccionAuditoria.ALUMNO_REACTIVADO,
                mx.edu.itch.sigclem.auditoria.domain.RegistroAuditoria.OK, "Alumno " + numeroControl + ".");
        return dto;
    }

    @Operation(summary = "Liberar la lengua extranjera por otra forma",
            description = "Rol: [Coordinador]. Formas: examen global, lengua materna u otra. Deja el idioma acreditado.")
    @PostMapping("/{numeroControl}/liberacion")
    @PreAuthorize("hasRole('COORDINADOR')")
    public ExpedienteDTO liberar(@PathVariable String numeroControl,
            @Valid @RequestBody mx.edu.itch.sigclem.alumnos.dto.LiberacionRequest req) {
        ExpedienteDTO dto = alumnoService.liberarIdioma(numeroControl, req.forma(), req.detalle());
        auditoria.registrar(mx.edu.itch.sigclem.auditoria.domain.AccionAuditoria.IDIOMA_LIBERADO,
                mx.edu.itch.sigclem.auditoria.domain.RegistroAuditoria.OK,
                "Alumno " + numeroControl + ". Forma: " + req.forma()
                        + (req.detalle() == null || req.detalle().isBlank() ? "" : ". " + req.detalle()));
        return dto;
    }

    /**
     * CU-03 Consultar expediente.
     * En producción: @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV') or (hasRole('ALUMNO') and #numeroControl == authentication.name)")
     */
    @GetMapping("/{numeroControl}")
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV') or (hasRole('ALUMNO') and #numeroControl == authentication.name)")
    @Operation(summary = "Consultar expediente", description = "Rol: [Coordinador][Jefa DGTV][Alumno]")
    public ExpedienteDTO expediente(@PathVariable String numeroControl) {
        return alumnoService.expediente(numeroControl);
    }

    /**
     * CU-03 Consultar avance reticular de los diez niveles (Tabla 81).
     */
    /** El alumno confirma o actualiza el semestre que cursa. */
    @PutMapping("/yo/semestre")
    @PreAuthorize("hasRole('ALUMNO')")
    @Operation(summary = "Actualizar mi semestre", description = "Rol: [Alumno]. Registra la fecha de la actualización.")
    public ExpedienteDTO actualizarSemestre(@Valid @RequestBody SemestreRequest req, Authentication authentication) {
        return alumnoService.actualizarSemestre(authentication.getName(), req.semestre());
    }

    @GetMapping("/{numeroControl}/avance")
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV') or (hasRole('ALUMNO') and #numeroControl == authentication.name)")
    @Operation(summary = "Consultar avance reticular de los 10 niveles", description = "Rol: [Coordinador][Jefa DGTV][Alumno]")
    public List<AvanceDTO> avance(@PathVariable String numeroControl) {
        return alumnoService.avance(numeroControl);
    }
}
