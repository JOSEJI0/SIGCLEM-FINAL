package mx.edu.itch.sigclem.constancias.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import mx.edu.itch.sigclem.constancias.dto.ConstanciaDTO;
import mx.edu.itch.sigclem.constancias.dto.MembreteConstancia;

class ConstanciaPdfServiceTest {

    private final ConstanciaDTO constancia = new ConstanciaDTO(
            1, "22520187", "José De Jesús Tepetitlán Mancilla", "Ingeniería en Sistemas Computacionales",
            "ISIC-2010-224", "1620", LocalDate.of(2025, 4, 10), LocalDate.of(2025, 4, 10), "Inglés",
            10, 450, "B1", "Emitida", null);

    @Test
    void generaElOficioDeAcreditacionConLasFirmasVigentes() throws Exception {
        byte[] pdf = new ConstanciaPdfService(null).generar(constancia, MembreteConstancia.porOmision());
        String contenido = texto(pdf);

        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(contenido.replaceAll("\\s+", " ")).contains(
                "Oficio No. 12DIT0002E/D/SPYV/DGTYV/1620/2025", "10/abril/2025", "A QUIEN CORRESPONDA",
                "la que suscribe, María Esther Durán Figueroa Jefa del Departamento",
                "José De Jesús Tepetitlán Mancilla", "22520187", "ISIC-2010-224",
                "TecNM-SeyV-DVIA-CNLE-07/24-ITChilpancingo-142", "10 niveles", "450 horas", "B1",
                "diez", "abril", "dos mil veinticinco",
                "MARÍA ESTHER DURÁN FIGUEROA", "JEFA DEL DEPARTAMENTO DE GESTIÓN",
                "YANET EVANGELISTA ALCOCER", "SUBDIRECTORA DE PLANEACIÓN", "Vo. Bo.",
                "Crear Tecnología es Forjar Libertad", "YEA/MEDF");
    }

    @Test
    void usaElCargoEnMasculinoCuandoCambiaLaPersona() throws Exception {
        MembreteConstancia base = MembreteConstancia.porOmision();
        MembreteConstancia cambio = new MembreteConstancia(new MembreteConstancia.Firmante("Pedro Ruiz Soto", false),
                new MembreteConstancia.Firmante("Luis Pérez Gómez", false), base.coordinador(), base.lema(),
                base.registroCoordinacion(), base.prefijoOficio(), base.copias(), null, null, null);

        String contenido = texto(new ConstanciaPdfService(null).generar(constancia, cambio)).replaceAll("\\s+", " ");

        assertThat(contenido).contains("el que suscribe, Luis Pérez Gómez Jefe del Departamento",
                "SUBDIRECTOR DE PLANEACIÓN", "PEDRO RUIZ SOTO");
        assertThat(contenido).doesNotContain("Durán Figueroa", "SUBDIRECTORA");
    }

    @Test
    void escribeElAnioConLetra() {
        assertThat(PdfConstancia.anioConLetra(2025)).isEqualTo("dos mil veinticinco");
        assertThat(PdfConstancia.anioConLetra(2000)).isEqualTo("dos mil");
        assertThat(PdfConstancia.anioConLetra(2031)).isEqualTo("dos mil treinta y uno");
    }

    private static String texto(byte[] pdf) throws Exception {
        try (PDDocument documento = PDDocument.load(new ByteArrayInputStream(pdf))) {
            return new PDFTextStripper().getText(documento);
        }
    }
}
