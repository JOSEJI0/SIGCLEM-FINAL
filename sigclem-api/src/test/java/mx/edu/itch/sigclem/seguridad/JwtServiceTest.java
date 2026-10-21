package mx.edu.itch.sigclem.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import mx.edu.itch.sigclem.seguridad.domain.RolSistema;
import mx.edu.itch.sigclem.seguridad.service.JwtService;
import mx.edu.itch.sigclem.seguridad.service.UsuarioAutenticado;

class JwtServiceTest {

    private static final String SECRETO = "secreto-de-pruebas-con-mas-de-32-caracteres";

    private final JwtService servicio = new JwtService(SECRETO, 60_000);
    private final UsuarioAutenticado coordinador =
            new UsuarioAutenticado(1, "coordinador", "hash", RolSistema.COORDINADOR, true);

    @Test
    void generaYValidaToken() {
        String token = servicio.generarToken(coordinador);
        assertThat(servicio.extraerUsuario(token)).contains("coordinador");
    }

    @Test
    void rechazaTokenAlterado() {
        String token = servicio.generarToken(coordinador);
        String alterado = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThat(servicio.extraerUsuario(alterado)).isEmpty();
    }

    @Test
    void rechazaTokenFirmadoConOtraClave() {
        JwtService otro = new JwtService("otra-clave-distinta-con-mas-de-32-caracteres", 60_000);
        assertThat(servicio.extraerUsuario(otro.generarToken(coordinador))).isEmpty();
    }

    @Test
    void rechazaTokenVencido() {
        JwtService vencido = new JwtService(SECRETO, -1_000);
        assertThat(servicio.extraerUsuario(vencido.generarToken(coordinador))).isEmpty();
    }

    @Test
    void exigeSecretoDeAlMenos32Caracteres() {
        assertThatThrownBy(() -> new JwtService("corto", 60_000)).isInstanceOf(IllegalStateException.class);
    }
}
