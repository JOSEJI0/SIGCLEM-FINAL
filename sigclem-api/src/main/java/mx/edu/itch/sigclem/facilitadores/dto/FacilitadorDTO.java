package mx.edu.itch.sigclem.facilitadores.dto;

import mx.edu.itch.sigclem.facilitadores.domain.Facilitador;

public record FacilitadorDTO(
        Integer id,
        String nombreCompleto,
        String nombre,
        String apellidoPaterno,
        String apellidoMaterno,
        String correo,
        String telefono,
        boolean activo) {

    public static FacilitadorDTO desde(Facilitador facilitador) {
        return new FacilitadorDTO(facilitador.getId(), facilitador.nombreCompleto(), facilitador.getNombre(),
                facilitador.getApellidoPaterno(), facilitador.getApellidoMaterno(), facilitador.getCorreo(),
                facilitador.getTelefono(), facilitador.getUsuario().isActivo());
    }
}
