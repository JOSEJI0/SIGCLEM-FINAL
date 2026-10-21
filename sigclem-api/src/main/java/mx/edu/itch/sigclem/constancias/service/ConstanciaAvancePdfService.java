package mx.edu.itch.sigclem.constancias.service;

import static mx.edu.itch.sigclem.constancias.service.PdfConstancia.NEGRITA;
import static mx.edu.itch.sigclem.constancias.service.PdfConstancia.NORMAL;
import static mx.edu.itch.sigclem.constancias.service.PdfConstancia.mayusculas;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

import mx.edu.itch.sigclem.constancias.dto.ConstanciaAvanceDTO;
import mx.edu.itch.sigclem.constancias.dto.MembreteConstancia;

/**
 * Formato de la constancia de avance por nivel de inglés (Foreign Languages Department), con los datos del
 * alumno, el nivel, el periodo, el facilitador, la calificación con letra y las tres firmas vigentes.
 */
@Service
public class ConstanciaAvancePdfService {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float ANCHO = PDRectangle.LETTER.getWidth();
    private static final float MARGEN = 40;

    private final ConfiguracionConstanciaService configuracion;

    public ConstanciaAvancePdfService(ConfiguracionConstanciaService configuracion) {
        this.configuracion = configuracion;
    }

    /** Genera la constancia con las firmas y el logo vigentes. */
    public byte[] generar(ConstanciaAvanceDTO datos) throws IOException {
        return generar(datos, configuracion.membreteVigente());
    }

    public byte[] generar(ConstanciaAvanceDTO datos, MembreteConstancia membrete) throws IOException {
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage(PDRectangle.LETTER);
            documento.addPage(pagina);
            PDImageXObject logo = cargarLogo(documento, membrete);
            try (PDPageContentStream c = new PDPageContentStream(documento, pagina)) {
                float arriba = 760;
                float abajo = 400;
                c.setLineWidth(0.8f);
                c.addRect(MARGEN, abajo, ANCHO - 2 * MARGEN, arriba - abajo);
                c.stroke();

                float limiteTexto = ANCHO - MARGEN;
                if (logo != null) {
                    float alto = Math.min(56, 150f * logo.getHeight() / logo.getWidth());
                    float ancho = alto * logo.getWidth() / logo.getHeight();
                    c.drawImage(logo, ANCHO - MARGEN - ancho - 10, arriba - alto - 14, ancho, alto);
                    limiteTexto = ANCHO - MARGEN - ancho - 16;
                }
                float centro = (MARGEN + limiteTexto) / 2;
                PdfConstancia.centrado(c, "INSTITUTO TECNOLÓGICO DE CHILPANCINGO", NORMAL, 11, centro, 735);
                PdfConstancia.centrado(c, "SUBDIRECCIÓN DE PLANEACIÓN Y VINCULACIÓN", NORMAL, 11, centro, 717);
                PdfConstancia.centrado(c, "DEPARTAMENTO DE GESTIÓN TECNOLÓGICA Y VINCULACIÓN", NORMAL, 11, centro, 699);
                PdfConstancia.centrado(c, "FOREIGN LANGUAGES DEPARTMENT", NEGRITA, 12.5f, centro, 677);

                float y = 640;
                y = renglon(c, y, List.of(
                        new String[] { "NAME:", mayusculas(datos.nombreAlumno()) },
                        new String[] { "CONTROL NUMBER:", datos.numeroControl() },
                        new String[] { "BACHELOR:", mayusculas(datos.carrera()) }));
                y -= 16;
                renglon(c, y, List.of(
                        new String[] { "LEVEL:", mayusculas(datos.nivel()) },
                        new String[] { "PERIOD:", mayusculas(datos.periodo()) },
                        new String[] { "NAME OF FACILITATOR:", mayusculas(datos.facilitador()) }));

                String calificacion = "GRADE: " + datos.calificacion() + " ("
                        + PdfConstancia.numeroConLetra(datos.calificacion()) + ").";
                float anchoCalif = PdfConstancia.ancho(calificacion, NEGRITA, 11);
                float xCalif = (ANCHO - anchoCalif) / 2;
                PdfConstancia.texto(c, calificacion, NEGRITA, 11, xCalif, 545);
                subrayar(c, xCalif, 543, anchoCalif);

                float[] columnas = { MARGEN + 95, ANCHO / 2, ANCHO - MARGEN - 95 };
                List<List<String>> cargos = List.of(membrete.cargoSubdirector(), membrete.cargoJefeDgtv(),
                        membrete.cargoCoordinador());
                List<String> nombres = List.of(membrete.subdirector().nombre(), membrete.jefeDgtv().nombre(),
                        membrete.coordinador().nombre());
                for (int i = 0; i < columnas.length; i++) {
                    c.moveTo(columnas[i] - 76, 470);
                    c.lineTo(columnas[i] + 76, 470);
                    c.stroke();
                    PdfConstancia.centrado(c, cargos.get(i).get(0), NEGRITA, 7.5f, columnas[i], 458);
                    PdfConstancia.centrado(c, cargos.get(i).get(1), NEGRITA, 7.5f, columnas[i], 448);
                    PdfConstancia.centrado(c, ajustarAncho(mayusculas(nombres.get(i)), NORMAL, 7.5f, 170), NORMAL, 7.5f,
                            columnas[i], 430);
                }
                PdfConstancia.texto(c, "Fecha de emisión: " + LocalDate.now().format(FECHA)
                        + (datos.fechaCierreActa() == null ? "" : "   ·   Cierre de acta: " + datos.fechaCierreActa().format(FECHA))
                        + "   ·   Grupo " + datos.grupo(), NORMAL, 6.5f, MARGEN + 8, abajo + 8);
            }
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    /**
     * Escribe pares «ETIQUETA: valor» uno tras otro (etiqueta en negritas y subrayada), pasando al siguiente
     * renglón cuando no caben. Devuelve la coordenada y del último renglón escrito.
     */
    private float renglon(PDPageContentStream c, float y, List<String[]> pares) throws IOException {
        float izquierda = MARGEN + 12;
        float derecha = ANCHO - MARGEN - 12;
        float x = izquierda;
        for (String[] par : pares) {
            float anchoEtiqueta = PdfConstancia.ancho(par[0], NEGRITA, 9.5f);
            String valor = ajustarAncho(par[1] == null ? "" : par[1], NORMAL, 8.5f, derecha - izquierda - anchoEtiqueta - 4);
            float anchoValor = PdfConstancia.ancho(valor, NORMAL, 8.5f);
            if (x > izquierda && x + anchoEtiqueta + 4 + anchoValor > derecha) {
                x = izquierda;
                y -= 16;
            }
            PdfConstancia.texto(c, par[0], NEGRITA, 9.5f, x, y);
            subrayar(c, x, y - 2, anchoEtiqueta);
            PdfConstancia.texto(c, valor, NORMAL, 8.5f, x + anchoEtiqueta + 4, y);
            x += anchoEtiqueta + 4 + anchoValor + 10;
        }
        return y;
    }

    private void subrayar(PDPageContentStream c, float x, float y, float ancho) throws IOException {
        c.setLineWidth(0.6f);
        c.moveTo(x, y);
        c.lineTo(x + ancho, y);
        c.stroke();
        c.setLineWidth(0.8f);
    }

    private String ajustarAncho(String texto, PDType1Font fuente, float tamano, float anchoMaximo) throws IOException {
        if (PdfConstancia.ancho(texto, fuente, tamano) <= anchoMaximo) {
            return texto;
        }
        String recortado = texto;
        while (recortado.length() > 4 && PdfConstancia.ancho(recortado + ".", fuente, tamano) > anchoMaximo) {
            recortado = recortado.substring(0, recortado.length() - 1);
        }
        return recortado.trim() + ".";
    }

    /** Logo de la coordinación cargado por coordinación; si no hay, el incluido en el sistema. */
    private PDImageXObject cargarLogo(PDDocument documento, MembreteConstancia membrete) {
        for (byte[] contenido : new byte[][] { membrete.logoCoordinacion(),
                PdfConstancia.recurso("constancias/clem-color.png") }) {
            if (contenido == null) continue;
            try {
                return PDImageXObject.createFromByteArray(documento, contenido, "logo");
            } catch (IOException | RuntimeException e) {
                // Imagen inválida: se intenta con la predeterminada.
            }
        }
        return null;
    }

    /** Calificación con letra, como en el formato impreso: 100 → «CIEN». */
    static String numeroConLetra(Integer numero) {
        return PdfConstancia.numeroConLetra(numero);
    }
}
