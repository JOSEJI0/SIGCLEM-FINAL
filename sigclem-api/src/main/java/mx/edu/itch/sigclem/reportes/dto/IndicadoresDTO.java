package mx.edu.itch.sigclem.reportes.dto;

import java.math.BigDecimal;
import java.util.List;

/** Indicadores generales para los paneles de coordinación y de la jefa del DGTV. */
public record IndicadoresDTO(
        String periodo,
        long alumnos,
        long facilitadoresActivos,
        long gruposPeriodo,
        long inscritosPeriodo,
        long acreditadosPeriodo,
        long noAcreditadosPeriodo,
        List<Conteo> alumnosPorNivel,
        List<Conteo> alumnosPorCarrera,
        long cargosPendientes,
        long cargosParciales,
        long cargosLiquidados,
        BigDecimal montoPorCobrar,
        BigDecimal montoCobrado,
        long comprobantesPorValidar,
        long examenesPendientes,
        long diagnosticosPorProgramar,
        long constanciasSolicitadas,
        long constanciasEmitidas,
        long lenguasPorRevisar) {

    public record Conteo(String etiqueta, long valor) {
    }
}
