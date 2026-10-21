package mx.edu.itch.sigclem.facilitadores.dto;

public record FacilitadorCreadoDTO(
        FacilitadorDTO facilitador,
        String usuario,
        String contrasenaTemporal) {
}
