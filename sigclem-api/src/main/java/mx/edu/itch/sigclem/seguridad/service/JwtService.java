package mx.edu.itch.sigclem.seguridad.service;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

/** Emisión y verificación de tokens JWT firmados con HMAC-SHA256 (decisión DA-03). */
@Slf4j
@Service
public class JwtService {

    private final SecretKey clave;
    private final long expiracionMs;

    public JwtService(@Value("${app.jwt.secreto}") String secreto,
            @Value("${app.jwt.expiracion-ms}") long expiracionMs) {
        byte[] bytes = secreto == null ? new byte[0] : secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        }
        this.clave = Keys.hmacShaKeyFor(bytes);
        this.expiracionMs = expiracionMs;
    }

    public String generarToken(UsuarioAutenticado usuario) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(usuario.getUsername())
                .claim("rol", usuario.rol().name())
                .claim("uid", usuario.id())
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expiracionMs))
                .signWith(clave)
                .compact();
    }

    /** Devuelve el nombre de usuario si el token es válido y no ha vencido. */
    public Optional<String> extraerUsuario(String token) {
        try {
            String sujeto = Jwts.parser().verifyWith(clave).build()
                    .parseSignedClaims(token).getPayload().getSubject();
            return Optional.ofNullable(sujeto);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Token JWT rechazado: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public long getExpiracionSegundos() {
        return expiracionMs / 1000;
    }
}
