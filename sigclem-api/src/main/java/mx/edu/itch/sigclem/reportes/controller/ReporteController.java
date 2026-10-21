package mx.edu.itch.sigclem.reportes.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.reportes.dto.ReporteAcademicoDTO;
import mx.edu.itch.sigclem.reportes.service.ReporteAcademicoService;

@Tag(name = "12. Reportes", description = "CU-17 / RF-18: resumen académico del periodo por nivel y por carrera y nivel.")
@RestController
@RequestMapping("/api/v1/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteAcademicoService reporteService;
    private final mx.edu.itch.sigclem.reportes.service.IndicadoresService indicadoresService;

    @GetMapping("/indicadores")
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Indicadores generales para el panel", description = "Rol: [Coordinador][Jefa DGTV]")
    public mx.edu.itch.sigclem.reportes.dto.IndicadoresDTO indicadores() {
        return indicadoresService.obtener();
    }

    @GetMapping("/academico")
    @PreAuthorize("hasAnyRole('COORDINADOR','JEFA_DGTV')")
    @Operation(summary = "Reporte académico del periodo", description = "Rol: [Coordinador][Jefa DGTV]")
    public ReporteAcademicoDTO reporteAcademico(@RequestParam(required = false) Integer periodoId) {
        return reporteService.obtenerReporte(periodoId);
    }
}
