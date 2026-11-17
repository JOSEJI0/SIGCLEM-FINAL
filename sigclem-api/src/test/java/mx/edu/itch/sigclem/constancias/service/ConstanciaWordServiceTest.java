package mx.edu.itch.sigclem.constancias.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import mx.edu.itch.sigclem.constancias.dto.ConstanciaDTO;
import mx.edu.itch.sigclem.constancias.dto.MembreteConstancia;

class ConstanciaWordServiceTest {

    @TempDir
    Path directorio;

    @Test
    void reemplazaDatosDeLaPlantillaConservandoLosTextosDeReferencia() throws Exception {
        ConstanciaWordService servicio = new ConstanciaWordService(directorio.toString());
        byte[] plantilla = crearPlantilla();
        servicio.guardarPlantilla("ingles", new MockMultipartFile(
                "archivo", "referencia.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", plantilla));

        ConstanciaDTO constancia = new ConstanciaDTO(
                1, "22520187", "Nombre Nuevo Alumno", "Ingeniería Civil", "IC-2020",
                "A2026", LocalDate.now(), LocalDate.of(2026, 11, 10), "Inglés",
                10, 450, "B1", "Emitida", null, "H", false);
        byte[] generado = servicio.generarIngles(constancia, MembreteConstancia.porOmision());

        try (XWPFDocument documento = new XWPFDocument(new ByteArrayInputStream(generado))) {
            String texto = documento.getParagraphs().stream()
                    .map(XWPFParagraph -> XWPFParagraph.getText())
                    .collect(Collectors.joining(" "));
            assertThat(texto).contains(
                    "Nombre Nuevo Alumno", "22520187", "Ingeniería Civil", "IC-2020",
                    "DGTV/A2026/", "10 niveles", "450 horas", "B1",
                    LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d/MMMM/yyyy",
                            mx.edu.itch.sigclem.constancias.service.PdfConstancia.ES)));
            assertThat(texto).doesNotContain("Nombre Anterior", "00000000", "Carrera Anterior");
        }
    }

    @Test
    void rechazaArchivosQueNoSeanPlantillasWordDocx() {
        ConstanciaWordService servicio = new ConstanciaWordService(directorio.toString());
        MockMultipartFile archivo = new MockMultipartFile("archivo", "plantilla.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "no es docx".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> servicio.guardarPlantilla("ingles", archivo))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("documento Word");
    }

    @Test
    void sustituyeLosDatosEnLasPlantillasDeReferenciaSiSeProporcionan() throws Exception {
        String rutaIngles = System.getProperty("sigclem.plantilla.ingles");
        String rutaLenguaMaterna = System.getProperty("sigclem.plantilla.lengua-materna");
        Assumptions.assumeTrue(rutaIngles != null && rutaLenguaMaterna != null,
                "Las plantillas de referencia se proporcionan solo para la verificación local.");

        ConstanciaWordService servicio = new ConstanciaWordService(directorio.toString());
        servicio.guardarPlantilla("ingles", archivo("ingles.docx", Path.of(rutaIngles)));
        servicio.guardarPlantilla("lengua-materna", archivo("lengua-materna.docx", Path.of(rutaLenguaMaterna)));

        ConstanciaDTO constancia = new ConstanciaDTO(
                1, "TEST0001", "Estudiante de Prueba", "Carrera de Prueba", "PLANTEST",
                "FOLIO2026", LocalDate.now(), LocalDate.of(2026, 11, 10), "Inglés",
                10, 450, "B1", "Emitida", null, "M", false);
        byte[] ingles = servicio.generarIngles(constancia, MembreteConstancia.porOmision());
        byte[] lenguaMaterna = servicio.generarLenguaMaterna(
                "Estudiante de Prueba", "TEST0001", "Carrera de Prueba", "PLANTEST",
                "Náhuatl", "M", "FOLIO2026", LocalDate.of(2026, 11, 10),
                MembreteConstancia.porOmision());

        assertDatosSustituidos(ingles, "Inglés");
        assertDatosSustituidos(lenguaMaterna, "Náhuatl");
    }

    private MockMultipartFile archivo(String nombre, Path ruta) throws Exception {
        return new MockMultipartFile(
                "archivo", nombre,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                Files.readAllBytes(ruta));
    }

    private void assertDatosSustituidos(byte[] generado, String idioma) throws Exception {
        try (XWPFDocument documento = new XWPFDocument(new ByteArrayInputStream(generado))) {
            List<String> textos = new ArrayList<>();
            documento.getParagraphs().forEach(parrafo -> textos.add(parrafo.getText()));
            agregarTextosTablas(documento.getTables(), textos);
            assertThat(String.join(" ", textos)).contains(
                    "Estudiante de Prueba", "TEST0001", "Carrera de Prueba", "PLANTEST",
                    "FOLIO2026", idioma);
        }
    }

    private void agregarTextosTablas(List<XWPFTable> tablas, List<String> textos) {
        for (XWPFTable tabla : tablas) {
            for (XWPFTableRow fila : tabla.getRows()) {
                for (XWPFTableCell celda : fila.getTableCells()) {
                    textos.add(celda.getText());
                    agregarTextosTablas(celda.getTables(), textos);
                }
            }
        }
    }

    private byte[] crearPlantilla() throws Exception {
        try (XWPFDocument documento = new XWPFDocument();
                ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            documento.createParagraph().createRun().setText("Chilpancingo, Guerrero 25/septiembre/2026");
            documento.createParagraph().createRun().setText("Oficio No. 12DIT0002E/SPV/DGTV/1152/2026");
            documento.createParagraph().createRun().setText(
                    "Por este conducto, el (la) estudiante C. Nombre Anterior con número de control 00000000 "
                            + "de la carrera Carrera Anterior con clave del plan de estudios XX-0000 acreditó "
                            + "el programa de Lengua Extranjera: Inglés en la Coordinación, con registro CODIGO "
                            + "DEL INSTITUTO, acreditando 10 niveles y cubriendo un total de 450 horas, "
                            + "obteniendo el nivel B1 basado en el Marco Común.");
            documento.createParagraph().createRun().setText(
                    "Para los fines legales, a los veinticinco días del mes de septiembre del año dos mil veintiséis.");
            documento.write(salida);
            return salida.toByteArray();
        }
    }
}
