package mx.edu.itch.sigclem.grupos.dto;

import java.time.LocalTime;

import mx.edu.itch.sigclem.grupos.domain.Horario;

public record HorarioDTO(Integer diaSemana, LocalTime horaInicio, LocalTime horaFin) {

    public static HorarioDTO desde(Horario horario) {
        return new HorarioDTO(horario.getDiaSemana(), horario.getHoraInicio(), horario.getHoraFin());
    }
}
