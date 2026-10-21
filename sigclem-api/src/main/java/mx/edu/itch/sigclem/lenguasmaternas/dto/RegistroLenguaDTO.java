package mx.edu.itch.sigclem.lenguasmaternas.dto;

import java.time.LocalDate;

import mx.edu.itch.sigclem.lenguasmaternas.domain.RegistroLenguaMaterna;

public record RegistroLenguaDTO(
        Integer id,
        String numeroControl,
        String nombreAlumno,
        Integer idLengua,
        String lengua,
        String nivelDominio,
        String estatus,
        LocalDate fechaRegistro,
        String usuarioValidador,
        String nombreDocumento) {

    public static RegistroLenguaDTO desde(RegistroLenguaMaterna registro) {
        return new RegistroLenguaDTO(
                registro.getId(),
                registro.getAlumno().getNumeroControl(),
                registro.getAlumno().nombreCompleto(),
                registro.getLengua().getId(),
                registro.getLengua().getNombre(),
                registro.getNivelDominio(),
                registro.getEstatus(),
                registro.getFechaRegistro(),
                registro.getUsuarioValida() == null ? null : registro.getUsuarioValida().getNombreUsuario(),
                "Documento adjunto");
    }
}
