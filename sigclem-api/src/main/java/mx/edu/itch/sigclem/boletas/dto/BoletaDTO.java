package mx.edu.itch.sigclem.boletas.dto;

import java.time.LocalDate;

import mx.edu.itch.sigclem.grupos.domain.Inscripcion;

public record BoletaDTO(
        Integer idInscripcion,
        String periodo,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Integer nivel,
        String nombreNivel,
        String grupo,
        String oportunidad,
        Integer calificacion,
        String resultado,
        LocalDate fechaCierreActa,
        Integer nivelSecundario,
        String nombreNivelSecundario,
        Integer calificacionSecundaria,
        String resultadoSecundario) {

    public static BoletaDTO desde(Inscripcion inscripcion) {
        var grupo = inscripcion.getGrupo();
        boolean actaCerrada = "Cerrada".equals(grupo.getEstatusActa());
        Integer calificacion = actaCerrada ? inscripcion.getCalificacionFinal() : null;
        String resultado = !actaCerrada ? "Pendiente de cierre"
                : calificacion == null ? "Sin calificación"
                : inscripcion.isAvancePendiente() ? "Nivel pendiente de pago"
                : calificacion >= 70 ? "Acreditado" : "No acreditado";
        Integer calificacionSecundaria = actaCerrada ? inscripcion.getCalificacionSecundaria() : null;
        String resultadoSecundario = grupo.getNivelSecundario() == null ? null
                : !actaCerrada ? "Pendiente de cierre"
                        : calificacionSecundaria == null ? "Sin calificación"
                                : inscripcion.isAvancePendienteSecundario() ? "Nivel pendiente de pago"
                                        : calificacionSecundaria >= 70 ? "Acreditado" : "No acreditado";
        return new BoletaDTO(
                inscripcion.getId(),
                grupo.getPeriodo().getNombre(),
                grupo.getPeriodo().getFechaInicio(),
                grupo.getPeriodo().getFechaFin(),
                grupo.getNivel().getNumero(),
                grupo.getNivel().getNombre(),
                grupo.getClave(),
                inscripcion.getOportunidad(),
                calificacion,
                resultado,
                grupo.getFechaCierreActa(),
                grupo.getNivelSecundario() == null ? null : grupo.getNivelSecundario().getNumero(),
                grupo.getNivelSecundario() == null ? null : grupo.getNivelSecundario().getNombre(),
                calificacionSecundaria,
                resultadoSecundario);
    }
}
