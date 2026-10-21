package mx.edu.itch.sigclem.constancias.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.core.io.ClassPathResource;

/** Utilidades de dibujo y redacción compartidas por los documentos PDF (constancias y actas). */
public final class PdfConstancia {

    public static final Locale ES = Locale.forLanguageTag("es-MX");
    public static final PDType1Font NORMAL = PDType1Font.HELVETICA;
    public static final PDType1Font NEGRITA = PDType1Font.HELVETICA_BOLD;
    public static final PDType1Font NEGRITA_CURSIVA = PDType1Font.HELVETICA_BOLD_OBLIQUE;

    private static final String[] MESES = { "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio",
            "agosto", "septiembre", "octubre", "noviembre", "diciembre" };
    private static final String[] UNIDADES = { "CERO", "UNO", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE",
            "OCHO", "NUEVE", "DIEZ", "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISÉIS", "DIECISIETE",
            "DIECIOCHO", "DIECINUEVE", "VEINTE", "VEINTIUNO", "VEINTIDÓS", "VEINTITRÉS", "VEINTICUATRO",
            "VEINTICINCO", "VEINTISÉIS", "VEINTISIETE", "VEINTIOCHO", "VEINTINUEVE" };
    private static final String[] DECENAS = { "", "", "", "TREINTA", "CUARENTA", "CINCUENTA", "SESENTA", "SETENTA",
            "OCHENTA", "NOVENTA" };

    /** Fragmento de texto con su fuente, para párrafos que mezclan normal y negritas. */
    public record Trozo(String texto, PDType1Font fuente) {
        static Trozo n(String texto) {
            return new Trozo(texto, NORMAL);
        }

        static Trozo b(String texto) {
            return new Trozo(texto, NEGRITA);
        }
    }

    private PdfConstancia() {
    }

    public static void texto(PDPageContentStream c, String texto, PDType1Font fuente, float tamano, float x, float y)
            throws IOException {
        c.beginText();
        c.setFont(fuente, tamano);
        c.newLineAtOffset(x, y);
        c.showText(codificable(texto, fuente));
        c.endText();
    }

    public static float ancho(String texto, PDType1Font fuente, float tamano) throws IOException {
        return fuente.getStringWidth(codificable(texto, fuente)) / 1000 * tamano;
    }

    /** Las fuentes estándar solo cubren WinAnsi: los caracteres fuera de ella (p. ej. de lenguas maternas) se omiten. */
    private static String codificable(String texto, PDType1Font fuente) {
        try {
            fuente.encode(texto);
            return texto;
        } catch (IOException | IllegalArgumentException e) {
            StringBuilder sb = new StringBuilder();
            for (char ch : texto.toCharArray()) {
                try {
                    fuente.encode(String.valueOf(ch));
                    sb.append(ch);
                } catch (IOException | IllegalArgumentException ignorado) {
                    // Carácter sin glifo en la fuente estándar.
                }
            }
            return sb.toString();
        }
    }

    public static void centrado(PDPageContentStream c, String texto, PDType1Font fuente, float tamano, float centro, float y)
            throws IOException {
        texto(c, texto, fuente, tamano, centro - ancho(texto, fuente, tamano) / 2, y);
    }

    public static void derecha(PDPageContentStream c, String texto, PDType1Font fuente, float tamano, float derecha, float y)
            throws IOException {
        texto(c, texto, fuente, tamano, derecha - ancho(texto, fuente, tamano), y);
    }

    /**
     * Párrafo con fragmentos en distintas fuentes, ajustado al ancho y justificado (salvo el último renglón).
     * Devuelve la coordenada y debajo del último renglón.
     */
    public static float parrafo(PDPageContentStream c, List<Trozo> trozos, float x, float y, float anchoMaximo,
            float tamano, float interlineado) throws IOException {
        List<List<Trozo>> palabras = new ArrayList<>();
        List<Trozo> actual = new ArrayList<>();
        for (Trozo trozo : trozos) {
            StringBuilder pieza = new StringBuilder();
            for (char ch : trozo.texto().toCharArray()) {
                if (Character.isWhitespace(ch)) {
                    if (!pieza.isEmpty()) actual.add(new Trozo(pieza.toString(), trozo.fuente()));
                    pieza.setLength(0);
                    if (!actual.isEmpty()) palabras.add(actual);
                    actual = new ArrayList<>();
                } else {
                    pieza.append(ch);
                }
            }
            if (!pieza.isEmpty()) actual.add(new Trozo(pieza.toString(), trozo.fuente()));
        }
        if (!actual.isEmpty()) palabras.add(actual);

        float espacio = ancho(" ", NORMAL, tamano);
        List<List<List<Trozo>>> renglones = new ArrayList<>();
        List<List<Trozo>> renglon = new ArrayList<>();
        float anchoRenglon = 0;
        for (List<Trozo> palabra : palabras) {
            float anchoPalabra = anchoPalabra(palabra, tamano);
            if (!renglon.isEmpty() && anchoRenglon + espacio + anchoPalabra > anchoMaximo) {
                renglones.add(renglon);
                renglon = new ArrayList<>();
                anchoRenglon = 0;
            }
            anchoRenglon += (renglon.isEmpty() ? 0 : espacio) + anchoPalabra;
            renglon.add(palabra);
        }
        if (!renglon.isEmpty()) renglones.add(renglon);

        for (int i = 0; i < renglones.size(); i++) {
            List<List<Trozo>> linea = renglones.get(i);
            float soloPalabras = 0;
            for (List<Trozo> palabra : linea) soloPalabras += anchoPalabra(palabra, tamano);
            boolean ultimo = i == renglones.size() - 1;
            float separacion = ultimo || linea.size() == 1 ? espacio
                    : (anchoMaximo - soloPalabras) / (linea.size() - 1);
            float xActual = x;
            for (List<Trozo> palabra : linea) {
                for (Trozo pieza : palabra) {
                    texto(c, pieza.texto(), pieza.fuente(), tamano, xActual, y);
                    xActual += ancho(pieza.texto(), pieza.fuente(), tamano);
                }
                xActual += separacion;
            }
            y -= interlineado;
        }
        return y;
    }

    private static float anchoPalabra(List<Trozo> palabra, float tamano) throws IOException {
        float total = 0;
        for (Trozo pieza : palabra) total += ancho(pieza.texto(), pieza.fuente(), tamano);
        return total;
    }

    public enum Alineacion { IZQUIERDA, CENTRO, DERECHA }

    /** Dibuja la imagen dentro del recuadro conservando su proporción; devuelve false si no es válida. */
    public static boolean imagen(PDDocument documento, PDPageContentStream c, byte[] contenido, float x, float y,
            float anchoMaximo, float altoMaximo, Alineacion alineacion) {
        if (contenido == null) return false;
        try {
            PDImageXObject imagen = PDImageXObject.createFromByteArray(documento, contenido, "membrete");
            float escala = Math.min(anchoMaximo / imagen.getWidth(), altoMaximo / imagen.getHeight());
            float ancho = imagen.getWidth() * escala;
            float alto = imagen.getHeight() * escala;
            float xImagen = switch (alineacion) {
                case IZQUIERDA -> x;
                case CENTRO -> x + (anchoMaximo - ancho) / 2;
                case DERECHA -> x + anchoMaximo - ancho;
            };
            c.drawImage(imagen, xImagen, y + (altoMaximo - alto) / 2, ancho, alto);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** Imagen incluida en el sistema (logos predeterminados). */
    public static byte[] recurso(String ruta) {
        try (InputStream entrada = new ClassPathResource(ruta).getInputStream()) {
            return entrada.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** Número de 0 a 100 con letra en mayúsculas: 100 → «CIEN», 85 → «OCHENTA Y CINCO». */
    public static String numeroConLetra(Integer numero) {
        if (numero == null) return "";
        if (numero == 100) return "CIEN";
        if (numero < 0 || numero > 100) return String.valueOf(numero);
        if (numero < 30) return UNIDADES[numero];
        int unidad = numero % 10;
        return DECENAS[numero / 10] + (unidad == 0 ? "" : " Y " + UNIDADES[unidad]);
    }

    public static String mes(LocalDate fecha) {
        return MESES[fecha.getMonthValue() - 1];
    }

    /** Año con letra en minúsculas (2000 a 2099): 2025 → «dos mil veinticinco». */
    public static String anioConLetra(int anio) {
        if (anio < 2000 || anio > 2099) return String.valueOf(anio);
        int resto = anio - 2000;
        return "dos mil" + (resto == 0 ? "" : " " + numeroConLetra(resto).toLowerCase(ES));
    }

    public static String mayusculas(String valor) {
        return valor == null ? "" : valor.toUpperCase(ES);
    }
}
