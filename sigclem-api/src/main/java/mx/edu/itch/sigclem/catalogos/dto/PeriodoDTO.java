package mx.edu.itch.sigclem.catalogos.dto;

import java.time.LocalDate;

import mx.edu.itch.sigclem.catalogos.domain.Periodo;

public record PeriodoDTO(Integer id, String nombre, LocalDate fechaInicio, LocalDate fechaFin) {

    public static PeriodoDTO desde(Periodo periodo) {
        return new PeriodoDTO(periodo.getId(), periodo.getNombre(), periodo.getFechaInicio(), periodo.getFechaFin());
    }
}
