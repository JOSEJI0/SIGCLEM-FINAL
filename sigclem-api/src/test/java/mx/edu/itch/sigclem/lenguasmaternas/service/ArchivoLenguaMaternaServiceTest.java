package mx.edu.itch.sigclem.lenguasmaternas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;

class ArchivoLenguaMaternaServiceTest {

    @TempDir
    Path directorioTemporal;

    @Test
    void almacenaPdfConNombreAleatorioYLoPuedeCargar() {
        ArchivoLenguaMaternaService servicio = new ArchivoLenguaMaternaService(directorioTemporal.toString());
        MockMultipartFile archivo = new MockMultipartFile(
                "documento", "comprobante.pdf", "application/pdf", "%PDF-1.7\ncontenido".getBytes());

        String nombre = servicio.guardar(archivo);

        assertThat(nombre).matches("[0-9a-f-]{36}\\.pdf");
        assertThat(servicio.tipoContenido(nombre)).isEqualTo("application/pdf");
        assertThat(servicio.cargar(nombre).exists()).isTrue();
    }

    @Test
    void rechazaArchivoQueNoTieneUnaFirmaPermitida() {
        ArchivoLenguaMaternaService servicio = new ArchivoLenguaMaternaService(directorioTemporal.toString());
        MockMultipartFile archivo = new MockMultipartFile(
                "documento", "documento.pdf", "application/pdf", "<html>".getBytes());

        assertThatThrownBy(() -> servicio.guardar(archivo))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("PDF, PNG o JPEG");
    }
}
