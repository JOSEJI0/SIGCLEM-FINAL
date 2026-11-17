package mx.edu.itch.sigclem.constancias.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.constancias.dto.ConstanciaDTO;
import mx.edu.itch.sigclem.constancias.dto.MembreteConstancia;

/** Guarda las plantillas Word y sustituye los datos del alumno conservando el formato del documento. */
@Service
public class ConstanciaWordService {

    public static final String INGLES = "ingles";
    public static final String LENGUA_MATERNA = "lengua-materna";
    private static final long TAMANO_MAXIMO = 8L * 1024 * 1024;
    private static final DateTimeFormatter FECHA_NUMERICA = DateTimeFormatter.ofPattern("d/MMMM/yyyy", PdfConstancia.ES);
    private final Path directorio;

    public ConstanciaWordService(@Value("${app.archivos.directorio:./archivos}") String directorio) {
        this.directorio = Path.of(directorio, "constancias", "plantillas").toAbsolutePath().normalize();
    }

    @Transactional
    public void guardarPlantilla(String tipo, MultipartFile archivo) {
        validarTipo(tipo);
        if (archivo == null || archivo.isEmpty() || archivo.getSize() > TAMANO_MAXIMO) {
            throw new DatosInvalidosException("CON-020", "Adjunta un archivo Word .docx de hasta 8 MB.");
        }
        byte[] contenido;
        Path temporal = directorio.resolve(tipo + ".tmp");
        try {
            contenido = archivo.getBytes();
            validarDocx(contenido);
            Files.createDirectories(directorio);
            Path destino = ruta(tipo);
            Files.write(temporal, contenido);
            try {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(temporal);
            } catch (IOException limpieza) {
                e.addSuppressed(limpieza);
            }
            throw new UncheckedIOException("No se pudo guardar la plantilla Word.", e);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> estadoPlantillas() {
        return Map.of(
                INGLES, Files.isRegularFile(ruta(INGLES)),
                LENGUA_MATERNA, Files.isRegularFile(ruta(LENGUA_MATERNA)));
    }

    public byte[] generarIngles(ConstanciaDTO constancia, MembreteConstancia membrete) {
        LocalDate fecha = constancia.fechaExpedicion() == null ? LocalDate.now() : constancia.fechaExpedicion();
        return generar(INGLES, new DatosDocumento(
                constancia.nombreAlumno(), constancia.numeroControl(), constancia.carrera(), constancia.clavePlan(),
                constancia.folio(), fecha, constancia.idioma(), constancia.nivelesAcreditados(),
                constancia.totalHoras(), constancia.nivelMcer(), membrete.registroCoordinacion(),
                membrete.jefeDgtv(), membrete.subdirector(), constancia.sexo()));
    }

    public byte[] generarLenguaMaterna(
            String nombreAlumno, String numeroControl, String carrera, String clavePlan, String lengua,
            String sexo, String folio, LocalDate fecha, MembreteConstancia membrete) {
        return generar(LENGUA_MATERNA, new DatosDocumento(
                nombreAlumno, numeroControl, carrera, clavePlan, folio, fecha, lengua,
                null, null, null, membrete.registroCoordinacion(), membrete.jefeDgtv(),
                membrete.subdirector(), sexo));
    }

    private byte[] generar(String tipo, DatosDocumento datos) {
        Path archivo = ruta(tipo);
        if (!Files.isRegularFile(archivo)) {
            throw new DatosInvalidosException("CON-021",
                    "Coordinación debe cargar la plantilla Word de " + (INGLES.equals(tipo) ? "inglés" : "lengua materna")
                            + " en Firmas y membrete antes de emitir constancias.");
        }
        try (XWPFDocument documento = new XWPFDocument(Files.newInputStream(archivo));
                ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            List<XWPFParagraph> parrafos = todosLosParrafos(documento);
            boolean nombre = false;
            boolean control = false;
            boolean carrera = false;
            boolean plan = false;
            boolean folio = false;
            boolean fecha = false;
            boolean idioma = false;
            for (XWPFParagraph parrafo : parrafos) {
                String texto = texto(parrafo);
                if (texto.contains("estudiante C. ")) {
                    nombre |= reemplazarEntre(parrafo, "estudiante C. ", " con número de control ",
                            datos.nombreAlumno());
                }
                if (texto.contains(" con número de control ")) {
                    control |= reemplazarEntre(parrafo, " con número de control ", " de la carrera ",
                            datos.numeroControl());
                }
                if (texto.contains(" de la carrera ") && texto.contains(" con clave del plan de estudios ")) {
                    carrera |= reemplazarEntre(parrafo, " de la carrera ", " con clave del plan de estudios ",
                            datos.carrera());
                }
                if (texto.contains(" con clave del plan de estudios ")) {
                    plan |= reemplazarEntre(parrafo, " con clave del plan de estudios ", " acreditó",
                            datos.clavePlan());
                }
                if (texto.contains("DGTV/")) {
                    folio |= reemplazarEntre(parrafo, "DGTV/", "/", datos.folio());
                }
                if (texto.contains("Lengua Extranjera: ")) {
                    idioma |= reemplazarEntre(parrafo, "Lengua Extranjera: ", " en la Coordinación", datos.idioma());
                } else if (texto.contains("lengua materna: ")) {
                    idioma |= reemplazarEntre(parrafo, "lengua materna: ", " en la Coordinación", datos.idioma());
                }
                if (texto.contains("Chilpancingo") && reemplazarFechaNumerica(parrafo, datos.fecha())) {
                    fecha = true;
                }
                reemplazarFechaEnLetras(parrafo, datos.fecha());
                reemplazarFirmanteEnTexto(parrafo, datos.jefeDgtv());
                reemplazarGeneroEstudiante(parrafo, datos.sexo());
                if (datos.niveles() != null) {
                    reemplazarEntre(parrafo, "acreditando ", " niveles", datos.niveles().toString());
                    reemplazarEntre(parrafo, "total de ", " horas", datos.horas().toString());
                    reemplazarEntre(parrafo, "obteniendo el nivel ", " basado en el Marco Común",
                            datos.nivelMcer());
                    reemplazarEntre(parrafo, "con registro ", " DEL INSTITUTO", datos.registroCoordinacion());
                }
            }
            reemplazarFirmantesEnTablas(documento, datos.jefeDgtv(), datos.subdirector());
            reemplazarFirmantesEnParrafos(parrafos, datos.jefeDgtv(), datos.subdirector());
            if (!(nombre && control && carrera && plan && folio && fecha && idioma)) {
                throw new DatosInvalidosException("CON-022",
                        "La plantilla Word no coincide con el formato de referencia: verifica nombre, número de control, "
                                + "carrera, plan, idioma, fecha y folio del oficio.");
            }
            documento.write(salida);
            return salida.toByteArray();
        } catch (DatosInvalidosException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la constancia Word.", e);
        }
    }

    private void validarDocx(byte[] contenido) throws IOException {
        if (contenido.length < 4 || contenido[0] != 'P' || contenido[1] != 'K') {
            throw new DatosInvalidosException("CON-020", "El archivo debe ser un documento Word .docx válido.");
        }
        boolean documento = false;
        boolean tipos = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(contenido))) {
            java.util.zip.ZipEntry entrada;
            while ((entrada = zip.getNextEntry()) != null) {
                if ("word/document.xml".equals(entrada.getName())) documento = true;
                if ("[Content_Types].xml".equals(entrada.getName())) tipos = true;
            }
        }
        if (!documento || !tipos) {
            throw new DatosInvalidosException("CON-020", "El archivo debe ser un documento Word .docx válido.");
        }
        try (XWPFDocument ignored = new XWPFDocument(new ByteArrayInputStream(contenido))) {
            // Apache POI confirma que el paquete contiene una estructura Word legible.
        }
    }

    private Path ruta(String tipo) {
        validarTipo(tipo);
        return directorio.resolve(tipo + ".docx").normalize();
    }

    private static void validarTipo(String tipo) {
        if (!INGLES.equals(tipo) && !LENGUA_MATERNA.equals(tipo)) {
            throw new RecursoNoEncontradoException("CON-023", "Tipo de plantilla no válido.");
        }
    }

    private static List<XWPFParagraph> todosLosParrafos(XWPFDocument documento) {
        List<XWPFParagraph> parrafos = new ArrayList<>(documento.getParagraphs());
        agregarParrafosTablas(documento.getTables(), parrafos);
        return parrafos;
    }

    private static void agregarParrafosTablas(List<XWPFTable> tablas, List<XWPFParagraph> destino) {
        for (XWPFTable tabla : tablas) {
            for (XWPFTableRow fila : tabla.getRows()) {
                for (XWPFTableCell celda : fila.getTableCells()) {
                    destino.addAll(celda.getParagraphs());
                    agregarParrafosTablas(celda.getTables(), destino);
                }
            }
        }
    }

    private static String texto(XWPFParagraph parrafo) {
        StringBuilder texto = new StringBuilder();
        parrafo.getRuns().forEach(run -> {
            String valor = run.getText(0);
            if (valor != null) texto.append(valor);
        });
        return texto.toString();
    }

    private static boolean reemplazarEntre(XWPFParagraph parrafo, String prefijo, String sufijo, String valor) {
        String texto = texto(parrafo);
        int inicioPrefijo = texto.indexOf(prefijo);
        if (inicioPrefijo < 0) return false;
        int inicio = inicioPrefijo + prefijo.length();
        int fin = texto.indexOf(sufijo, inicio);
        if (fin < inicio) return false;
        reemplazarRango(parrafo, inicio, fin, valor);
        return true;
    }

    private static void reemplazarRango(XWPFParagraph parrafo, int inicio, int fin, String reemplazo) {
        int posicion = 0;
        boolean primero = true;
        List<org.apache.poi.xwpf.usermodel.XWPFRun> runs = parrafo.getRuns();
        for (int indice = 0; indice < runs.size(); indice++) {
            var run = runs.get(indice);
            String textoRun = run.getText(0);
            if (textoRun == null) continue;
            int finRun = posicion + textoRun.length();
            if (finRun > inicio && posicion < fin) {
                int localInicio = Math.max(0, inicio - posicion);
                int localFin = Math.min(textoRun.length(), fin - posicion);
                String antes = textoRun.substring(0, localInicio);
                String despues = textoRun.substring(localFin);
                String nuevo = primero ? antes + reemplazo + despues : despues;
                run.setText(nuevo, 0);
                primero = false;
            }
            posicion = finRun;
        }
    }

    private static boolean reemplazarFechaNumerica(XWPFParagraph parrafo, LocalDate fecha) {
        String texto = texto(parrafo);
        Matcher matcher = Pattern.compile("\\d{1,2}/[\\p{L}]+/\\d{4}").matcher(texto);
        if (!matcher.find()) return false;
        reemplazarRango(parrafo, matcher.start(), matcher.end(), FECHA_NUMERICA.format(fecha));
        return true;
    }

    private static void reemplazarFechaEnLetras(XWPFParagraph parrafo, LocalDate fecha) {
        String texto = texto(parrafo);
        Matcher matcher = Pattern.compile("(?:a los |al ).*?días? del mes de .*? del año .*?\\.")
                .matcher(texto);
        if (!matcher.find()) return;
        String reemplazo = fecha.getDayOfMonth() == 1
                ? "al primer día del mes de " + PdfConstancia.mes(fecha) + " del año "
                        + PdfConstancia.anioConLetra(fecha.getYear()) + "."
                : "a los " + PdfConstancia.numeroConLetra(fecha.getDayOfMonth()).toLowerCase(PdfConstancia.ES)
                        + " días del mes de " + PdfConstancia.mes(fecha) + " del año "
                        + PdfConstancia.anioConLetra(fecha.getYear()) + ".";
        reemplazarRango(parrafo, matcher.start(), matcher.end(), reemplazo);
    }

    private static void reemplazarFirmanteEnTexto(XWPFParagraph parrafo, MembreteConstancia.Firmante jefe) {
        String texto = texto(parrafo);
        String prefijo = "la que suscribe, ";
        int inicioPrefijo = texto.indexOf(prefijo);
        if (inicioPrefijo < 0) return;
        int inicio = inicioPrefijo + prefijo.length();
        int finJefa = texto.indexOf(" Jefa del Departamento", inicio);
        int finJefe = texto.indexOf(" Jefe del Departamento", inicio);
        int fin = finJefa < 0 ? finJefe : finJefe < 0 ? finJefa : Math.min(finJefa, finJefe);
        if (fin < inicio) return;
        if (fin > inicio && texto.charAt(fin - 1) == ',') fin--;
        reemplazarRango(parrafo, inicio, fin, jefe.nombre());
        texto = texto(parrafo);
        int cargoInicio = texto.indexOf("Jefa del Departamento");
        String cargoAnterior = "Jefa del Departamento";
        if (cargoInicio < 0) {
            cargoInicio = texto.indexOf("Jefe del Departamento");
            cargoAnterior = "Jefe del Departamento";
        }
        if (cargoInicio >= 0) {
            reemplazarRango(parrafo, cargoInicio, cargoInicio + cargoAnterior.length(),
                    (jefe.mujer() ? "Jefa" : "Jefe") + " del Departamento");
        }
    }

    private static void reemplazarGeneroEstudiante(XWPFParagraph parrafo, String sexo) {
        if (sexo == null || sexo.isBlank()) return;
        boolean mujer = "M".equalsIgnoreCase(sexo);
        String texto = texto(parrafo);
        String tratamiento = mujer ? "la estudiante" : "el estudiante";
        for (String anterior : List.of("el (la) estudiante", "la estudiante", "el estudiante")) {
            int inicio = texto.indexOf(anterior);
            if (inicio >= 0) {
                reemplazarRango(parrafo, inicio, inicio + anterior.length(), tratamiento);
                texto = texto(parrafo);
            }
        }
        texto = texto(parrafo);
        String interesada = mujer ? "interesada" : "interesado";
        for (String anterior : List.of("interesado", "interesada")) {
            int inicio = texto.indexOf(anterior);
            if (inicio >= 0) {
                reemplazarRango(parrafo, inicio, inicio + anterior.length(), interesada);
                break;
            }
        }
    }

    private static void reemplazarFirmantesEnTablas(
            XWPFDocument documento, MembreteConstancia.Firmante jefe, MembreteConstancia.Firmante subdirector) {
        for (XWPFTable tabla : documento.getTables()) {
            List<XWPFTableRow> filas = tabla.getRows();
            for (int fila = 1; fila < filas.size(); fila++) {
                List<XWPFTableCell> celdas = filas.get(fila).getTableCells();
                List<XWPFTableCell> nombres = filas.get(fila - 1).getTableCells();
                for (int columna = 0; columna < celdas.size() && columna < nombres.size(); columna++) {
                    String cargo = celdas.get(columna).getText().toUpperCase(PdfConstancia.ES);
                    if (cargo.contains("JEFA DEL DEPARTAMENTO DE GESTIÓN")
                            || cargo.contains("JEFE DEL DEPARTAMENTO DE GESTIÓN")) {
                        reemplazarCelda(nombres.get(columna), jefe.nombre().toUpperCase(PdfConstancia.ES));
                        reemplazarPrefijoCelda(celdas.get(columna),
                                cargo.contains("JEFA") ? "JEFA DEL DEPARTAMENTO DE GESTIÓN"
                                        : "JEFE DEL DEPARTAMENTO DE GESTIÓN",
                                (jefe.mujer() ? "JEFA" : "JEFE") + " DEL DEPARTAMENTO DE GESTIÓN");
                    } else if (cargo.contains("SUBDIRECTORA DE PLANEACIÓN")
                            || cargo.contains("SUBDIRECTOR DE PLANEACIÓN")) {
                        reemplazarCelda(nombres.get(columna), subdirector.nombre().toUpperCase(PdfConstancia.ES));
                        reemplazarPrefijoCelda(celdas.get(columna),
                                cargo.contains("SUBDIRECTORA") ? "SUBDIRECTORA DE PLANEACIÓN"
                                        : "SUBDIRECTOR DE PLANEACIÓN",
                                (subdirector.mujer() ? "SUBDIRECTORA" : "SUBDIRECTOR") + " DE PLANEACIÓN");
                    }
                }
            }
        }
    }

    private static void reemplazarFirmantesEnParrafos(
            List<XWPFParagraph> parrafos, MembreteConstancia.Firmante jefe,
            MembreteConstancia.Firmante subdirector) {
        for (int indice = 1; indice < parrafos.size(); indice++) {
            XWPFParagraph cargos = parrafos.get(indice);
            String textoCargos = texto(cargos).toUpperCase(PdfConstancia.ES);
            boolean lineaFirmas = textoCargos.contains("JEFA DEL DEPARTAMENTO DE GESTIÓN")
                    && textoCargos.contains("SUBDIRECTORA DE PLANEACIÓN");
            if (!lineaFirmas) continue;

            List<org.apache.poi.xwpf.usermodel.XWPFRun> nombres = parrafos.get(indice - 1).getRuns()
                    .stream().filter(run -> run.getText(0) != null && !run.getText(0).isBlank()).toList();
            if (nombres.size() >= 2) {
                reemplazarRun(nombres.get(0), jefe.nombre().toUpperCase(PdfConstancia.ES));
                reemplazarRun(nombres.get(1), subdirector.nombre().toUpperCase(PdfConstancia.ES));
            }
            for (var run : cargos.getRuns()) {
                String actual = run.getText(0);
                if (actual == null) continue;
                String normalizado = actual.toUpperCase(PdfConstancia.ES);
                if (normalizado.startsWith("JEFA DEL DEPARTAMENTO DE GESTIÓN")) {
                    reemplazarTextoRun(run, "JEFA DEL DEPARTAMENTO DE GESTIÓN",
                            (jefe.mujer() ? "JEFA" : "JEFE") + " DEL DEPARTAMENTO DE GESTIÓN");
                } else if (normalizado.startsWith("SUBDIRECTORA DE PLANEACIÓN")) {
                    reemplazarTextoRun(run, "SUBDIRECTORA DE PLANEACIÓN",
                            (subdirector.mujer() ? "SUBDIRECTORA" : "SUBDIRECTOR") + " DE PLANEACIÓN");
                }
            }
        }
    }

    private static void reemplazarRun(org.apache.poi.xwpf.usermodel.XWPFRun run, String reemplazo) {
        String actual = run.getText(0);
        if (actual == null) return;
        int inicio = 0;
        while (inicio < actual.length() && Character.isWhitespace(actual.charAt(inicio))) inicio++;
        run.setText(actual.substring(0, inicio) + reemplazo, 0);
    }

    private static void reemplazarTextoRun(
            org.apache.poi.xwpf.usermodel.XWPFRun run, String anterior, String reemplazo) {
        String actual = run.getText(0);
        if (actual == null) return;
        int inicio = actual.toUpperCase(PdfConstancia.ES).indexOf(anterior.toUpperCase(PdfConstancia.ES));
        if (inicio >= 0) {
            run.setText(actual.substring(0, inicio) + reemplazo + actual.substring(inicio + anterior.length()), 0);
        }
    }

    private static void reemplazarRangoCelda(XWPFTableCell celda, String textoNuevo) {
        if (celda.getParagraphs().isEmpty()) return;
        XWPFParagraph parrafo = celda.getParagraphs().get(0);
        String actual = texto(parrafo);
        if (!actual.isEmpty()) reemplazarRango(parrafo, 0, actual.length(), textoNuevo);
    }

    private static void reemplazarCelda(XWPFTableCell celda, String textoNuevo) {
        reemplazarRangoCelda(celda, textoNuevo);
    }

    private static void reemplazarPrefijoCelda(XWPFTableCell celda, String prefijo, String reemplazo) {
        if (celda.getParagraphs().isEmpty()) return;
        XWPFParagraph parrafo = celda.getParagraphs().get(0);
        String actual = texto(parrafo);
        if (actual.startsWith(prefijo)) reemplazarRango(parrafo, 0, prefijo.length(), reemplazo);
    }

    private record DatosDocumento(
            String nombreAlumno, String numeroControl, String carrera, String clavePlan, String folio, LocalDate fecha,
            String idioma, Integer niveles, Integer horas, String nivelMcer, String registroCoordinacion,
            MembreteConstancia.Firmante jefeDgtv, MembreteConstancia.Firmante subdirector, String sexo) {
    }
}
