package mx.edu.itch.sigclem.constancias.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import mx.edu.itch.sigclem.constancias.dto.ConstanciaAvanceDTO;
import mx.edu.itch.sigclem.constancias.dto.MembreteConstancia;

class ConstanciaAvancePdfServiceTest {

    @Test
    void generaLaConstanciaConLosDatosDelNivelYLasFirmas() throws Exception {
        var servicio = new ConstanciaAvancePdfService(null);
        var datos = new ConstanciaAvanceDTO(7, "22520187", "José de Jesús Tepetitlán Mancilla",
                "Ingeniería en Sistemas Computacionales", 2, "Básico 2", "Octubre - Noviembre", "A",
                "Samuel Isaí Villaseñor Díaz", 100, LocalDate.of(2025, 11, 28));

        byte[] pdf = servicio.generar(datos, MembreteConstancia.porOmision());

        try (PDDocument documento = PDDocument.load(pdf)) {
            String texto = new PDFTextStripper().getText(documento);
            assertEquals(1, documento.getNumberOfPages());
            assertTrue(texto.contains("FOREIGN LANGUAGES DEPARTMENT"));
            assertTrue(texto.contains("JOSÉ DE JESÚS TEPETITLÁN MANCILLA"));
            assertTrue(texto.contains("22520187"));
            assertTrue(texto.contains("BÁSICO 2"));
            assertTrue(texto.contains("SAMUEL ISAÍ VILLASEÑOR DÍAZ"));
            assertTrue(texto.contains("GRADE: 100 (CIEN)."));
            assertTrue(texto.contains("MARÍA ESTHER DURÁN FIGUEROA"));
            assertTrue(texto.contains("YANET EVANGELISTA ALCOCER"));
            assertTrue(texto.contains("JOSÉ ANTONIO CHÁVEZ ROMÁN"));
            assertTrue(texto.contains("COORDINADOR DE LENGUAS"));
            assertTrue(texto.contains("SUBDIRECTORA DE PLANEACIÓN"));
        }
    }

    @Test
    void escribeLaCalificacionConLetra() {
        assertEquals("CIEN", ConstanciaAvancePdfService.numeroConLetra(100));
        assertEquals("NOVENTA Y CINCO", ConstanciaAvancePdfService.numeroConLetra(95));
        assertEquals("SETENTA", ConstanciaAvancePdfService.numeroConLetra(70));
        assertEquals("VEINTIDÓS", ConstanciaAvancePdfService.numeroConLetra(22));
    }
}
