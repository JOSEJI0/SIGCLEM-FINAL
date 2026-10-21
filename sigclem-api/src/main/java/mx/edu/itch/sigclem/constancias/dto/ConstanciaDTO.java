package mx.edu.itch.sigclem.constancias.dto;

import java.time.LocalDate;

import mx.edu.itch.sigclem.constancias.domain.Constancia;

public record ConstanciaDTO(
        Integer id,
        String numeroControl,
        String nombreAlumno,
        String carrera,
        String clavePlan,
        String folio,
        LocalDate fechaSolicitud,
        LocalDate fechaExpedicion,
        String idioma,
        Integer nivelesAcreditados,
        Integer totalHoras,
        String nivelMcer,
        String estatus,
        String motivoRechazo) {

    public static ConstanciaDTO desde(Constancia constancia) {
        var alumno = constancia.getAlumno();
        return new ConstanciaDTO(
                constancia.getId(),
                alumno.getNumeroControl(),
                alumno.nombreCompleto(),
                alumno.getCarrera().getNombre(),
                alumno.getCarrera().getClavePlan(),
                constancia.getFolio(),
                constancia.getFechaSolicitud(),
                constancia.getFechaExpedicion(),
                constancia.getIdioma(),
                constancia.getNivelesAcreditados(),
                constancia.getTotalHoras(),
                constancia.getNivelMcer(),
                constancia.getEstatus(),
                constancia.getMotivoRechazo());
    }
}
