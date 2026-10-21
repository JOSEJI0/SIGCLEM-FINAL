package mx.edu.itch.sigclem.constancias.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;

class ArchivoConstanciaServiceTest {

    @TempDir
    Path directorioTemporal;

    @Test
    void guardaYRecuperaPdfDeConstancia() throws Exception {
        ArchivoConstanciaService service = new ArchivoConstanciaService(directorioTemporal.toString());
        byte[] pdf = "%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

        String ruta = service.guardar(23, pdf);

        assertThat(ruta).isEqualTo("constancias/23.pdf");
        try (var contenido = service.cargar(ruta).getInputStream()) {
            assertThat(contenido.readAllBytes()).isEqualTo(pdf);
        }

        service.eliminar(ruta);
        assertThatThrownBy(() -> service.cargar(ruta))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void noPermiteNombresDeArchivoFueraDelFormatoInterno() {
        ArchivoConstanciaService service = new ArchivoConstanciaService(directorioTemporal.toString());

        assertThatThrownBy(() -> service.cargar("constancias/../../application.properties"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
