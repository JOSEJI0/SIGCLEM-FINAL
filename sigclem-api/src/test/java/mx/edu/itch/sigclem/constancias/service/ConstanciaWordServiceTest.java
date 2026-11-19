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
                    // La fecha del oficio es la de expedición de la constancia, no la del día en que corre la prueba.
                    "10/noviembre/2026");
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

    // ---------- Plantillas con marcadores ----------

    private static final MembreteConstancia MEMBRETE = MembreteConstancia.porOmision();

    @Test
    void sinPlantillaCargadaUsaLaDeEjemploYSustituyeTodosLosMarcadores() throws Exception {
        ConstanciaWordService servicio = new ConstanciaWordService(directorio.toString());
        ConstanciaDTO constancia = new ConstanciaDTO(
                1, "21520227", "Silvia Edith Antaño Meléndez", "Ingeniería en Gestión Empresarial", "IGEM-2009-201",
                "1152", LocalDate.now(), LocalDate.of(2026, 9, 25), "Inglés", 10, 450, "B1", "Emitida", null, "M", true);

        String texto = textoCompleto(servicio.generarIngles(constancia, MEMBRETE));

        assertThat(texto).doesNotContain("{{");
        assertThat(texto).contains("Silvia Edith Antaño Meléndez", "21520227", "Ingeniería en Gestión Empresarial",
                "IGEM-2009-201", "DGTV/1152/2026", "25/septiembre/2026",
                "a los veinticinco días del mes de septiembre del año dos mil veintiséis",
                "acreditando 10 niveles", "450 horas", "nivel B1", "la estudiante ha cumplido", "a la interesada",
                "MARÍA ESTHER DURÁN FIGUEROA", "JEFA DEL DEPARTAMENTO DE GESTIÓN", "SUBDIRECTORA DE PLANEACIÓN");
        // El encabezado y el pie de la plantilla se conservan.
        assertThat(texto).contains("Departamento de Gestión Tecnológica y Vinculación", "dgtv@chilpancingo.tecnm.mx");
    }

    @Test
    void laConstanciaDeLenguaMaternaUsaElSexoYLaLengua() throws Exception {
        ConstanciaWordService servicio = new ConstanciaWordService(directorio.toString());

        String texto = textoCompleto(servicio.generarLenguaMaterna("Yesica Reyes Cruz", "17520291",
                "Ingeniería en Sistemas Computacionales", "ISIC-2010-224", "Me’phaa", "H", "1136",
                LocalDate.of(2026, 10, 1), MEMBRETE));

        assertThat(texto).doesNotContain("{{");
        assertThat(texto).contains("Yesica Reyes Cruz", "17520291", "lengua materna: Me’phaa", "DGTV/1136/2026",
                "el estudiante ha cumplido", "al interesado",
                "al primer día del mes de octubre del año dos mil veintiséis");
    }

    @Test
    void reemplazaMarcadoresPartidosEnVariosFragmentosYConservaElFormato() throws Exception {
        ConstanciaWordService servicio = new ConstanciaWordService(directorio.toString());
        byte[] plantilla = documento(d -> {
            var p = d.createParagraph();
            p.createRun().setText("Se hace constar que {{NOM");
            var negritas = p.createRun();
            negritas.setBold(true);
            negritas.setText("BRE}}, número {{NUMERO_");
            p.createRun().setText("CONTROL}}, folio {{FOLIO}}/{{ANIO}} del {{FECHA}}.");
        });
        servicio.guardarPlantilla("lengua-materna", docx(plantilla));

        byte[] generado = servicio.generarLenguaMaterna("Ana López", "22520001", "Contador Público", "COPU",
                "Náhuatl", null, "77", LocalDate.of(2026, 11, 18), MEMBRETE);

        try (XWPFDocument documento = new XWPFDocument(new ByteArrayInputStream(generado))) {
            assertThat(documento.getParagraphs().get(0).getText()).isEqualTo(
                    "Se hace constar que Ana López, número 22520001, folio 77/2026 del 18/noviembre/2026.");
        }
    }

    @Test
    void rechazaMarcadoresMalEscritosFaltantesOQueNoAplican() throws Exception {
        ConstanciaWordService servicio = new ConstanciaWordService(directorio.toString());

        assertThatThrownBy(() -> servicio.guardarPlantilla("ingles", docx(documento(d -> d.createParagraph()
                .createRun().setText("{{NOMRBE}} {{NUMERO_CONTROL}} {{FOLIO}} {{FECHA}}")))))
                .hasMessageContaining("{{NOMRBE}}");
        assertThatThrownBy(() -> servicio.guardarPlantilla("ingles", docx(documento(d -> d.createParagraph()
                .createRun().setText("{{NOMBRE}} {{FECHA}}")))))
                .hasMessageContaining("{{NUMERO_CONTROL}}").hasMessageContaining("{{FOLIO}}");
        assertThatThrownBy(() -> servicio.guardarPlantilla("lengua-materna", docx(documento(d -> d.createParagraph()
                .createRun().setText("{{NOMBRE}} {{NUMERO_CONTROL}} {{FOLIO}} {{FECHA}} {{NIVELES}}")))))
                .hasMessageContaining("solo aplican a inglés");
        assertThatThrownBy(() -> servicio.guardarPlantilla("ingles", docx(documento(d -> d.createParagraph()
                .createRun().setText("{{NOMBRE}} {{NUMERO_CONTROL}} {{FOLIO}} {{FECHA} ")))))
                .hasMessageContaining("incompleto");
        // Ninguna de esas plantillas reemplazó a la vigente (la de ejemplo).
        assertThat(servicio.estadoPlantillas().get("ingles").cargada()).isFalse();
    }

    @Test
    void lasPlantillasDeEjemploSonValidasYSeInformanSusMarcadores() throws Exception {
        ConstanciaWordService servicio = new ConstanciaWordService(directorio.toString());
        servicio.guardarPlantilla("ingles", docx(servicio.plantillaEjemplo("ingles")));
        servicio.guardarPlantilla("lengua-materna", docx(servicio.plantillaEjemplo("lengua-materna")));

        var estado = servicio.estadoPlantillas();
        assertThat(estado.get("ingles").cargada()).isTrue();
        assertThat(estado.get("ingles").modo()).isEqualTo("Marcadores");
        assertThat(estado.get("ingles").marcadores()).contains("NOMBRE", "NIVELES", "FECHA_LETRA");
        assertThat(estado.get("lengua-materna").marcadores()).contains("LENGUA").doesNotContain("NIVELES");
    }

    /** Texto de cuerpo, encabezados y pies (incluidos los cuadros de texto), un renglón por párrafo. */
    private static String textoCompleto(byte[] docx) throws Exception {
        StringBuilder texto = new StringBuilder();
        java.util.regex.Pattern t = java.util.regex.Pattern.compile("<w:t(?: [^>]*)?>([^<]*)</w:t>");
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new ByteArrayInputStream(docx))) {
            java.util.zip.ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                if (!entrada.getName().matches("word/(document|header\\d*|footer\\d*)\\.xml")) continue;
                String xml = new String(zip.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                for (String parrafo : xml.split("</w:p>")) {
                    java.util.regex.Matcher m = t.matcher(parrafo);
                    while (m.find()) texto.append(m.group(1));
                    texto.append('\n');
                }
            }
        }
        return texto.toString().replace("&amp;", "&");
    }

    private static byte[] documento(java.util.function.Consumer<XWPFDocument> contenido) throws Exception {
        try (XWPFDocument documento = new XWPFDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            contenido.accept(documento);
            documento.write(salida);
            return salida.toByteArray();
        }
    }

    private static MockMultipartFile docx(byte[] contenido) {
        return new MockMultipartFile("archivo", "plantilla.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", contenido);
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
