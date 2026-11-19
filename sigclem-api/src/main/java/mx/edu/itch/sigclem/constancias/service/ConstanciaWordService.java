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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import org.apache.poi.xwpf.usermodel.IBody;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlObject;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.constancias.dto.ConstanciaDTO;
import mx.edu.itch.sigclem.constancias.dto.MembreteConstancia;

/**
 * Plantillas Word de las constancias de acreditación (inglés y lengua materna).
 * <p>
 * La coordinación sube un .docx con el texto, el diseño y los membretes que necesite, y escribe <b>marcadores</b>
 * como {@code {{NOMBRE}}} donde deben ir los datos. Al emitir, el sistema copia el documento completo (encabezados,
 * pies, imágenes y cuadros de texto incluidos) y sustituye cada marcador conservando su formato, así que la redacción
 * puede cambiar sin tocar el código. Si no hay plantilla cargada se usa la de ejemplo incluida en el sistema.
 * Las plantillas anteriores sin marcadores se siguen procesando buscando las frases del formato de referencia.
 */
@Service
public class ConstanciaWordService {

    public static final String INGLES = "ingles";
    public static final String LENGUA_MATERNA = "lengua-materna";

    /** Marcadores admitidos y qué escribe el sistema en su lugar (se muestran en la pantalla de plantillas). */
    public static final Map<String, String> MARCADORES = marcadores();
    /** Marcadores que solo tienen dato en la constancia de inglés. */
    private static final Set<String> SOLO_INGLES = Set.of("NIVELES", "HORAS", "NIVEL_MCER");
    /** Sin estos datos la constancia no identifica al alumno ni al oficio. */
    private static final List<String> OBLIGATORIOS = List.of("NOMBRE", "NUMERO_CONTROL", "FOLIO");
    private static final Pattern MARCADOR = Pattern.compile("\\{\\{\\s*([A-Za-z_]+)\\s*\\}\\}");
    private static final Pattern MARCADOR_ABIERTO = Pattern.compile("\\{\\{");
    private static final long TAMANO_MAXIMO = 8L * 1024 * 1024;
    private static final DateTimeFormatter FECHA_NUMERICA = DateTimeFormatter.ofPattern("d/MMMM/yyyy", PdfConstancia.ES);
    private final Path directorio;

    public ConstanciaWordService(@Value("${app.archivos.directorio:./archivos}") String directorio) {
        this.directorio = Path.of(directorio, "constancias", "plantillas").toAbsolutePath().normalize();
    }

    /** Estado de una plantilla: si la coordinación cargó una propia y qué marcadores contiene la vigente. */
    public record EstadoPlantilla(boolean cargada, String modo, List<String> marcadores) {
    }

    /** Marcador admitido y lo que el sistema escribe en su lugar. */
    public record Marcador(String clave, String descripcion, boolean soloIngles) {
    }

    /**
     * Valida y guarda la plantilla. Antes de aceptarla se genera una constancia de prueba con datos de ejemplo, de
     * modo que un marcador mal escrito o faltante se detecta al subirla y no al emitir.
     */
    @Transactional
    public EstadoPlantilla guardarPlantilla(String tipo, MultipartFile archivo) {
        validarTipo(tipo);
        if (archivo == null || archivo.isEmpty() || archivo.getSize() > TAMANO_MAXIMO) {
            throw new DatosInvalidosException("CON-020", "Adjunta un archivo Word .docx de hasta 8 MB.");
        }
        byte[] contenido;
        Path temporal = directorio.resolve(tipo + ".tmp");
        try {
            contenido = archivo.getBytes();
            validarDocx(contenido);
            Set<String> encontrados = marcadoresEn(contenido);
            if (!encontrados.isEmpty()) {
                validarMarcadores(tipo, encontrados);
            }
            generarDesde(contenido, tipo, datosEjemplo(tipo, MembreteConstancia.porOmision()));
            Files.createDirectories(directorio);
            Path destino = ruta(tipo);
            Files.write(temporal, contenido);
            try {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, destino, StandardCopyOption.REPLACE_EXISTING);
            }
            return estado(tipo);
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
    public Map<String, EstadoPlantilla> estadoPlantillas() {
        Map<String, EstadoPlantilla> estados = new LinkedHashMap<>();
        estados.put(INGLES, estado(INGLES));
        estados.put(LENGUA_MATERNA, estado(LENGUA_MATERNA));
        return estados;
    }

    public List<Marcador> listaMarcadores() {
        return MARCADORES.entrySet().stream()
                .map(e -> new Marcador(e.getKey(), e.getValue(), SOLO_INGLES.contains(e.getKey())))
                .toList();
    }

    /** La plantilla vigente (la cargada o, si no hay, la de ejemplo) para editarla en Word. */
    public byte[] plantillaVigente(String tipo) {
        validarTipo(tipo);
        return contenido(tipo);
    }

    /** Plantilla de ejemplo incluida en el sistema, con todos los marcadores ya colocados. */
    public byte[] plantillaEjemplo(String tipo) {
        validarTipo(tipo);
        try {
            return new ClassPathResource("constancias/plantillas/" + tipo + ".docx").getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se encontró la plantilla de ejemplo.", e);
        }
    }

    /** Constancia de prueba con datos ficticios y los firmantes configurados, para revisar la plantilla. */
    public byte[] vistaPrevia(String tipo, MembreteConstancia membrete) {
        validarTipo(tipo);
        return generarDesde(contenido(tipo), tipo, datosEjemplo(tipo, membrete));
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
        return generarDesde(contenido(tipo), tipo, datos);
    }

    private byte[] contenido(String tipo) {
        Path archivo = ruta(tipo);
        if (!Files.isRegularFile(archivo)) {
            return plantillaEjemplo(tipo);
        }
        try {
            return Files.readAllBytes(archivo);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer la plantilla Word.", e);
        }
    }

    private EstadoPlantilla estado(String tipo) {
        boolean cargada = Files.isRegularFile(ruta(tipo));
        Set<String> marcadores;
        try {
            marcadores = marcadoresEn(contenido(tipo));
        } catch (IOException | RuntimeException e) {
            marcadores = Set.of();
        }
        return new EstadoPlantilla(cargada, marcadores.isEmpty() ? "Texto de referencia" : "Marcadores",
                List.copyOf(marcadores));
    }

    private byte[] generarDesde(byte[] plantilla, String tipo, DatosDocumento datos) {
        try (XWPFDocument documento = new XWPFDocument(new ByteArrayInputStream(plantilla));
                ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            List<XWPFParagraph> parrafos = parrafosCompletos(documento);
            boolean conMarcadores = parrafos.stream().anyMatch(p -> MARCADOR.matcher(texto(p)).find());
            if (conMarcadores) {
                Map<String, String> valores = valores(tipo, datos);
                for (XWPFParagraph parrafo : parrafos) {
                    reemplazarMarcadores(parrafo, valores);
                }
            } else {
                reemplazarPorReferencia(documento, datos);
            }
            documento.write(salida);
            return salida.toByteArray();
        } catch (DatosInvalidosException e) {
            throw e;
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la constancia Word.", e);
        }
    }

    // ---------- Marcadores ----------

    private static Map<String, String> marcadores() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("NOMBRE", "Nombre completo del alumno.");
        m.put("NOMBRE_MAYUSCULAS", "Nombre completo en mayúsculas.");
        m.put("NUMERO_CONTROL", "Número de control.");
        m.put("CARRERA", "Carrera del alumno.");
        m.put("CLAVE_PLAN", "Clave del plan de estudios de la carrera.");
        m.put("FOLIO", "Folio de la constancia (número del oficio).");
        m.put("ANIO", "Año de la fecha de expedición, por ejemplo 2026.");
        m.put("FECHA", "Fecha de expedición: 25/septiembre/2026.");
        m.put("FECHA_LETRA", "«a los veinticinco días del mes de septiembre del año dos mil veintiséis».");
        m.put("IDIOMA", "Idioma acreditado (Inglés) o lengua materna (Me’phaa, Náhuatl…).");
        m.put("LENGUA", "Igual que IDIOMA; útil en la constancia de lengua materna.");
        m.put("NIVELES", "Niveles acreditados.");
        m.put("HORAS", "Total de horas cursadas.");
        m.put("NIVEL_MCER", "Nivel del Marco Común Europeo, por ejemplo B1.");
        m.put("EL_LA_ESTUDIANTE", "«el estudiante» o «la estudiante»; «el (la) estudiante» si no se conoce el sexo.");
        m.put("AL_LA_INTERESADO", "«al interesado» o «a la interesada»; «al (a la) interesado(a)» si no se conoce.");
        m.put("JEFE_DGTV", "Nombre de quien encabeza el Departamento de Gestión Tecnológica y Vinculación.");
        m.put("JEFE_DGTV_MAYUSCULAS", "El mismo nombre en mayúsculas, para la firma.");
        m.put("CARGO_JEFE_DGTV", "«Jefa» o «Jefe».");
        m.put("CARGO_JEFE_DGTV_MAYUSCULAS", "«JEFA» o «JEFE».");
        m.put("SUBDIRECTOR", "Nombre de quien encabeza la Subdirección de Planeación y Vinculación.");
        m.put("SUBDIRECTOR_MAYUSCULAS", "El mismo nombre en mayúsculas, para la firma.");
        m.put("CARGO_SUBDIRECTOR", "«Subdirectora» o «Subdirector».");
        m.put("CARGO_SUBDIRECTOR_MAYUSCULAS", "«SUBDIRECTORA» o «SUBDIRECTOR».");
        m.put("REGISTRO_COORDINACION", "Registro de la coordinación ante el TecNM (configurado en Firmas y membrete).");
        return java.util.Collections.unmodifiableMap(m);
    }

    private static Map<String, String> valores(String tipo, DatosDocumento d) {
        boolean conocido = d.sexo() != null && !d.sexo().isBlank();
        boolean mujer = conocido && "M".equalsIgnoreCase(d.sexo());
        Map<String, String> v = new LinkedHashMap<>();
        v.put("NOMBRE", d.nombreAlumno());
        v.put("NOMBRE_MAYUSCULAS", PdfConstancia.mayusculas(d.nombreAlumno()));
        v.put("NUMERO_CONTROL", d.numeroControl());
        v.put("CARRERA", d.carrera());
        v.put("CLAVE_PLAN", d.clavePlan());
        v.put("FOLIO", d.folio());
        v.put("ANIO", String.valueOf(d.fecha().getYear()));
        v.put("FECHA", FECHA_NUMERICA.format(d.fecha()));
        v.put("FECHA_LETRA", fechaEnLetra(d.fecha()));
        v.put("IDIOMA", d.idioma());
        v.put("LENGUA", d.idioma());
        v.put("EL_LA_ESTUDIANTE", !conocido ? "el (la) estudiante" : mujer ? "la estudiante" : "el estudiante");
        v.put("AL_LA_INTERESADO", !conocido ? "al (a la) interesado(a)" : mujer ? "a la interesada" : "al interesado");
        v.put("JEFE_DGTV", d.jefeDgtv().nombre());
        v.put("JEFE_DGTV_MAYUSCULAS", PdfConstancia.mayusculas(d.jefeDgtv().nombre()));
        v.put("CARGO_JEFE_DGTV", d.jefeDgtv().mujer() ? "Jefa" : "Jefe");
        v.put("CARGO_JEFE_DGTV_MAYUSCULAS", d.jefeDgtv().mujer() ? "JEFA" : "JEFE");
        v.put("SUBDIRECTOR", d.subdirector().nombre());
        v.put("SUBDIRECTOR_MAYUSCULAS", PdfConstancia.mayusculas(d.subdirector().nombre()));
        v.put("CARGO_SUBDIRECTOR", d.subdirector().mujer() ? "Subdirectora" : "Subdirector");
        v.put("CARGO_SUBDIRECTOR_MAYUSCULAS", d.subdirector().mujer() ? "SUBDIRECTORA" : "SUBDIRECTOR");
        v.put("REGISTRO_COORDINACION", d.registroCoordinacion());
        if (INGLES.equals(tipo)) {
            v.put("NIVELES", d.niveles() == null ? "" : d.niveles().toString());
            v.put("HORAS", d.horas() == null ? "" : d.horas().toString());
            v.put("NIVEL_MCER", d.nivelMcer());
        }
        v.replaceAll((clave, valor) -> valor == null ? "" : valor);
        return v;
    }

    /** «a los veinticinco días del mes de septiembre del año dos mil veintiséis» (o «al primer día…»). */
    static String fechaEnLetra(LocalDate fecha) {
        String mesAnio = " del mes de " + PdfConstancia.mes(fecha) + " del año "
                + PdfConstancia.anioConLetra(fecha.getYear());
        return fecha.getDayOfMonth() == 1 ? "al primer día" + mesAnio
                : "a los " + PdfConstancia.numeroConLetra(fecha.getDayOfMonth()).toLowerCase(PdfConstancia.ES)
                        + " días" + mesAnio;
    }

    private static void validarMarcadores(String tipo, Set<String> encontrados) {
        List<String> desconocidos = new ArrayList<>();
        List<String> noAplican = new ArrayList<>();
        for (String marcador : encontrados) {
            if (!MARCADORES.containsKey(marcador)) {
                desconocidos.add("{{" + marcador + "}}");
            } else if (LENGUA_MATERNA.equals(tipo) && SOLO_INGLES.contains(marcador)) {
                noAplican.add("{{" + marcador + "}}");
            }
        }
        List<String> faltan = OBLIGATORIOS.stream().filter(m -> !encontrados.contains(m))
                .map(m -> "{{" + m + "}}").toList();
        List<String> problemas = new ArrayList<>();
        if (!desconocidos.isEmpty()) problemas.add("marcadores que no existen: " + String.join(", ", desconocidos));
        if (!noAplican.isEmpty()) problemas.add("marcadores que solo aplican a inglés: " + String.join(", ", noAplican));
        if (!faltan.isEmpty()) problemas.add("faltan los marcadores obligatorios " + String.join(", ", faltan));
        if (!encontrados.contains("FECHA") && !encontrados.contains("FECHA_LETRA")) {
            problemas.add("falta la fecha ({{FECHA}} o {{FECHA_LETRA}})");
        }
        if (!problemas.isEmpty()) {
            throw new DatosInvalidosException("CON-024", "Revisa la plantilla: " + String.join("; ", problemas) + ".");
        }
    }

    /** Marcadores {{...}} presentes en cualquier parte del documento (cuerpo, tablas, encabezados, pies, cuadros). */
    static Set<String> marcadoresEn(byte[] contenido) throws IOException {
        Set<String> encontrados = new TreeSet<>();
        try (XWPFDocument documento = new XWPFDocument(new ByteArrayInputStream(contenido))) {
            for (XWPFParagraph parrafo : parrafosCompletos(documento)) {
                String texto = texto(parrafo);
                Matcher matcher = MARCADOR.matcher(texto);
                long completos = 0;
                while (matcher.find()) {
                    encontrados.add(matcher.group(1).toUpperCase(Locale.ROOT));
                    completos++;
                }
                if (MARCADOR_ABIERTO.matcher(texto).results().count() > completos) {
                    throw new DatosInvalidosException("CON-024", "Revisa la plantilla: hay un marcador incompleto o "
                            + "con espacios en «" + recortar(texto) + "». Cada marcador se escribe como {{NOMBRE}}.");
                }
            }
        }
        return encontrados;
    }

    private static void reemplazarMarcadores(XWPFParagraph parrafo, Map<String, String> valores) {
        String texto = texto(parrafo);
        Matcher matcher = MARCADOR.matcher(texto);
        List<int[]> rangos = new ArrayList<>();
        List<String> reemplazos = new ArrayList<>();
        while (matcher.find()) {
            String clave = matcher.group(1).toUpperCase(Locale.ROOT);
            String valor = valores.get(clave);
            if (valor == null) {
                throw new DatosInvalidosException("CON-024", "La plantilla usa el marcador {{" + clave
                        + "}}, que no existe o no aplica a esta constancia. Corrígela en Constancias → Plantillas Word.");
            }
            rangos.add(new int[] { matcher.start(), matcher.end() });
            reemplazos.add(valor);
        }
        // De atrás hacia adelante, para que las posiciones de los marcadores anteriores no cambien.
        for (int i = rangos.size() - 1; i >= 0; i--) {
            reemplazarRango(parrafo, rangos.get(i)[0], rangos.get(i)[1], reemplazos.get(i));
        }
    }

    /** Todos los párrafos del documento, incluidos tablas, encabezados, pies y cuadros de texto. */
    private static List<XWPFParagraph> parrafosCompletos(XWPFDocument documento) {
        List<XWPFParagraph> parrafos = new ArrayList<>();
        agregarParrafos(documento.getDocument().getBody(), documento, parrafos);
        for (XWPFHeader encabezado : documento.getHeaderList()) {
            agregarParrafos(encabezado._getHdrFtr(), encabezado, parrafos);
        }
        for (XWPFFooter pie : documento.getFooterList()) {
            agregarParrafos(pie._getHdrFtr(), pie, parrafos);
        }
        return parrafos;
    }

    private static void agregarParrafos(XmlObject xml, IBody cuerpo, List<XWPFParagraph> destino) {
        Set<XmlObject> vistos = new LinkedHashSet<>();
        for (XmlObject objeto : xml.selectPath(
                "declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' .//w:p")) {
            if (objeto instanceof CTP ctp && vistos.add(objeto)) {
                destino.add(new XWPFParagraph(ctp, cuerpo));
            }
        }
    }

    private static DatosDocumento datosEjemplo(String tipo, MembreteConstancia m) {
        boolean ingles = INGLES.equals(tipo);
        return new DatosDocumento("Nombre del Alumno de Ejemplo", "00000000", "Carrera de ejemplo", "CLAVE-PLAN",
                "0000", LocalDate.now(), ingles ? "Inglés" : "Náhuatl", ingles ? 10 : null, ingles ? 450 : null,
                ingles ? "B1" : null, m.registroCoordinacion(), m.jefeDgtv(), m.subdirector(), "M");
    }

    private static String recortar(String texto) {
        String limpio = texto.strip();
        return limpio.length() <= 80 ? limpio : limpio.substring(0, 80) + "…";
    }

    // ---------- Formato de referencia (plantillas anteriores, sin marcadores) ----------

    private static void reemplazarPorReferencia(XWPFDocument documento, DatosDocumento datos) {
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
                    "La plantilla Word no tiene marcadores y no coincide con el formato de referencia. Escribe "
                            + "marcadores como {{NOMBRE}}, {{NUMERO_CONTROL}}, {{FOLIO}} y {{FECHA}} donde van los "
                            + "datos (descarga la plantilla de ejemplo).");
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
