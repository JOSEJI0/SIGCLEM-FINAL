package mx.edu.itch.sigclem.grupos.dto;

import java.time.LocalDate;
import java.util.List;

import mx.edu.itch.sigclem.grupos.domain.Grupo;

/**
 * {@code interesados}: alumnos que eligieron este grupo en la encuesta y aún no están asignados.
 * {@code cerradaPor} y {@code observacionCierre}: quién cerró el acta y, si fue coordinación, por qué.
 */
public record GrupoDTO(
        Integer id,
        String clave,
        Integer idNivel,
        Integer nivelNumero,
        String nivel,
        String periodo,
        Integer idPeriodo,
        LocalDate fechaInicioPeriodo,
        LocalDate fechaFinPeriodo,
        Integer idModalidad,
        String modalidad,
        Integer idAula,
        String aula,
        Integer idFacilitador,
        String facilitador,
        Integer cupo,
        long alumnosInscritos,
        long interesados,
        String estatusActa,
        LocalDate fechaCierreActa,
        String cerradaPor,
        String observacionCierre,
        List<HorarioDTO> horarios) {

    public static GrupoDTO desde(Grupo grupo, long alumnosInscritos) {
        return desde(grupo, alumnosInscritos, 0);
    }

    public static GrupoDTO desde(Grupo grupo, long alumnosInscritos, long interesados) {
        return new GrupoDTO(grupo.getId(), grupo.getClave(), grupo.getNivel().getId(), grupo.getNivel().getNumero(),
                grupo.getNivel().getNombre(), grupo.getPeriodo().getNombre(), grupo.getPeriodo().getId(),
                grupo.getPeriodo().getFechaInicio(), grupo.getPeriodo().getFechaFin(),
                grupo.getModalidad().getId(), grupo.getModalidad().getNombre(),
                grupo.getAula().getId(), grupo.getAula().getNombre(),
                grupo.getFacilitador().getId(), grupo.getFacilitador().nombreCompleto(),
                grupo.getCupo(), alumnosInscritos, interesados, grupo.getEstatusActa(),
                grupo.getFechaCierreActa(), grupo.getCerradaPor(), grupo.getObservacionCierre(),
                grupo.getHorarios().stream()
                        .sorted(java.util.Comparator.comparing(h -> h.getDiaSemana() * 10000 + h.getHoraInicio().toSecondOfDay() / 60))
                        .map(HorarioDTO::desde).toList());
    }
}
