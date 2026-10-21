package mx.edu.itch.sigclem.seguridad;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import mx.edu.itch.sigclem.comun.excepcion.DemasiadosIntentosException;
import mx.edu.itch.sigclem.seguridad.service.IntentosLoginService;

class IntentosLoginServiceTest {

    private final IntentosLoginService servicio = new IntentosLoginService();

    @Test
    void bloqueaTrasCincoFallos() {
        for (int i = 0; i < 5; i++) {
            servicio.registrarFallo("22520187");
        }
        assertThatThrownBy(() -> servicio.verificarNoBloqueado("22520187"))
                .isInstanceOf(DemasiadosIntentosException.class);
    }

    @Test
    void noBloqueaConMenosDeCincoFallosYSeReiniciaConExito() {
        for (int i = 0; i < 4; i++) {
            servicio.registrarFallo("coordinador");
        }
        assertThatCode(() -> servicio.verificarNoBloqueado("coordinador")).doesNotThrowAnyException();
        servicio.registrarExito("coordinador");
        servicio.registrarFallo("coordinador");
        assertThatCode(() -> servicio.verificarNoBloqueado("coordinador")).doesNotThrowAnyException();
    }
}
