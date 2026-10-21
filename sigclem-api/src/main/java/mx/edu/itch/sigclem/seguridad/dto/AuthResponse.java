package mx.edu.itch.sigclem.seguridad.dto;

public record AuthResponse(String token, String tipo, long expiraEnSegundos, PerfilResponse perfil) {
}
