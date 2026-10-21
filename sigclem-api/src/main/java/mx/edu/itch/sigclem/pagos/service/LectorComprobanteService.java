package mx.edu.itch.sigclem.pagos.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;

/**
 * Lee en el servidor el QR y el texto de un comprobante de pago (PDF, JPG o PNG).
 * El texto sale del PDF digital; si el archivo es un escaneo o una foto, se usa OCR (Tesseract) cuando está
 * configurado en {@code app.ocr.comando}.
 */
@Service
public class LectorComprobanteService {

    private static final Logger LOG = LoggerFactory.getLogger(LectorComprobanteService.class);
    private static final long PIXELES_MAXIMOS = 40_000_000L;
    private static final int LADO_MAXIMO_RENDER = 3400;

    /** Resultado de la lectura; cualquier campo puede ser null si no se pudo obtener. */
    public record Lectura(String contenidoQr, String texto, String origenTexto) {
    }

    private final String comandoOcr;
    private final String idiomaOcr;
    private final long tiempoMaximoOcr;

    public LectorComprobanteService(
            @Value("${app.ocr.comando:}") String comandoOcr,
            @Value("${app.ocr.idioma:spa}") String idiomaOcr,
            @Value("${app.ocr.tiempo-maximo-segundos:40}") long tiempoMaximoOcr) {
        this.comandoOcr = comandoOcr == null ? "" : comandoOcr.trim();
        this.idiomaOcr = idiomaOcr;
        this.tiempoMaximoOcr = tiempoMaximoOcr;
    }

    public boolean ocrDisponible() {
        return !comandoOcr.isEmpty();
    }

    public Lectura leer(byte[] contenido, ArchivoComprobanteService.Formato formato) {
        try {
            BufferedImage imagen;
            String texto = null;
            String origenTexto = null;
            if (formato == ArchivoComprobanteService.Formato.PDF) {
                try (PDDocument documento = PDDocument.load(contenido)) {
                    if (documento.getNumberOfPages() == 0) {
                        throw new DatosInvalidosException("PAG-003", "El PDF del comprobante no tiene páginas.");
                    }
                    texto = textoPdf(documento);
                    if (texto != null) origenTexto = "PDF";
                    imagen = renderizar(documento);
                }
            } else {
                imagen = leerImagen(contenido);
            }
            String qr = decodificarQr(imagen);
            if (texto == null && ocrDisponible()) {
                texto = ocr(imagen);
                if (texto != null) origenTexto = "OCR";
            }
            return new Lectura(qr, texto, origenTexto);
        } catch (DatosInvalidosException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // PDFBox e ImageIO lanzan excepciones variadas con archivos dañados o malformados.
            throw new DatosInvalidosException("PAG-003", "No se pudo abrir el comprobante; verifica que el archivo no esté dañado.");
        }
    }

    private String textoPdf(PDDocument documento) throws IOException {
        PDFTextStripper extractor = new PDFTextStripper();
        extractor.setSortByPosition(true);
        extractor.setStartPage(1);
        extractor.setEndPage(Math.min(2, documento.getNumberOfPages()));
        String texto = extractor.getText(documento);
        // Un escaneo convertido a PDF no tiene capa de texto (o solo trae basura de unos cuantos caracteres).
        return texto == null || texto.replaceAll("\\s+", "").length() < 40 ? null : texto;
    }

    private BufferedImage renderizar(PDDocument documento) throws IOException {
        PDPage pagina = documento.getPage(0);
        float ladoMayor = Math.max(pagina.getMediaBox().getWidth(), pagina.getMediaBox().getHeight());
        float dpi = Math.min(300f, LADO_MAXIMO_RENDER * 72f / Math.max(ladoMayor, 1f));
        return new PDFRenderer(documento).renderImageWithDPI(0, dpi, ImageType.GRAY);
    }

    private BufferedImage leerImagen(byte[] contenido) throws IOException {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                throw new DatosInvalidosException("PAG-003", "La imagen del comprobante no tiene un formato legible.");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                if ((long) lector.getWidth(0) * lector.getHeight(0) > PIXELES_MAXIMOS) {
                    throw new DatosInvalidosException("PAG-003", "La imagen del comprobante es demasiado grande.");
                }
                return lector.read(0);
            } finally {
                lector.dispose();
            }
        }
    }

    /**
     * Busca el QR probando varias versiones de la imagen. En escaneos con grano, reducir la resolución o suavizar
     * la imagen elimina el ruido que impide la lectura; en fotos nítidas basta la imagen original.
     */
    String decodificarQr(BufferedImage imagen) {
        if (imagen == null) {
            return null;
        }
        int ancho = imagen.getWidth();
        int alto = imagen.getHeight();
        // Recibo oficial de cobro: el QR está a la izquierda, en la mitad inferior de la hoja.
        BufferedImage recorte = alto > ancho ? imagen.getSubimage(0, alto / 3, ancho / 2, alto / 2) : imagen;
        List<java.util.function.Supplier<BufferedImage>> candidatas = List.of(
                () -> escalar(imagen, 1100),
                () -> recorte,
                () -> suavizar(recorte, 3),
                () -> escalar(imagen, 1600),
                () -> escalar(imagen, 800),
                () -> imagen,
                () -> suavizar(recorte, 5),
                () -> escalar(suavizar(recorte, 3), 900));
        Map<DecodeHintType, Object> pistas = new EnumMap<>(DecodeHintType.class);
        pistas.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        pistas.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        pistas.put(DecodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
        QRCodeReader lector = new QRCodeReader();
        for (var candidata : candidatas) {
            LuminanceSource fuente = new BufferedImageLuminanceSource(candidata.get());
            for (BinaryBitmap mapa : List.of(new BinaryBitmap(new HybridBinarizer(fuente)),
                    new BinaryBitmap(new GlobalHistogramBinarizer(fuente)))) {
                try {
                    String texto = lector.decode(mapa, pistas).getText();
                    if (texto != null && !texto.isBlank()) {
                        return texto.trim();
                    }
                } catch (ReaderException e) {
                    // Se intenta con el siguiente binarizador o versión de la imagen.
                } finally {
                    lector.reset();
                }
            }
        }
        return null;
    }

    /** Reduce la imagen por mitades sucesivas (promedia píxeles y atenúa el grano del escaneo). */
    private BufferedImage escalar(BufferedImage imagen, int ladoMaximo) {
        BufferedImage actual = imagen;
        while (Math.max(actual.getWidth(), actual.getHeight()) > ladoMaximo) {
            int lado = Math.max(actual.getWidth(), actual.getHeight());
            double factor = Math.max(0.5, (double) ladoMaximo / lado);
            int ancho = Math.max(1, (int) Math.round(actual.getWidth() * factor));
            int alto = Math.max(1, (int) Math.round(actual.getHeight() * factor));
            BufferedImage destino = new BufferedImage(ancho, alto, BufferedImage.TYPE_BYTE_GRAY);
            Graphics2D g = destino.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(actual, 0, 0, ancho, alto, null);
            g.dispose();
            actual = destino;
        }
        return actual;
    }

    private BufferedImage suavizar(BufferedImage imagen, int tamano) {
        float[] matriz = new float[tamano * tamano];
        java.util.Arrays.fill(matriz, 1f / matriz.length);
        BufferedImage gris = new BufferedImage(imagen.getWidth(), imagen.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gris.createGraphics();
        g.drawImage(imagen, 0, 0, null);
        g.dispose();
        return new java.awt.image.ConvolveOp(new java.awt.image.Kernel(tamano, tamano, matriz),
                java.awt.image.ConvolveOp.EDGE_NO_OP, null).filter(gris, null);
    }

    /** OCR con el ejecutable de Tesseract; si falla o tarda demasiado, se devuelve null y coordinación revisa a ojo. */
    private String ocr(BufferedImage imagen) {
        Path temporal = null;
        Process proceso = null;
        try {
            temporal = Files.createTempFile("sigclem-ocr-", ".png");
            ImageIO.write(escalar(imagen, LADO_MAXIMO_RENDER), "png", temporal.toFile());
            proceso = new ProcessBuilder(comandoOcr, temporal.toString(), "stdout", "-l", idiomaOcr, "--psm", "4")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            Process enEjecucion = proceso;
            CompletableFuture<byte[]> salida = CompletableFuture.supplyAsync(() -> {
                try {
                    return enEjecucion.getInputStream().readAllBytes();
                } catch (IOException e) {
                    return new byte[0];
                }
            });
            if (!proceso.waitFor(tiempoMaximoOcr, TimeUnit.SECONDS)) {
                LOG.warn("El OCR del comprobante superó {} s y se canceló.", tiempoMaximoOcr);
                return null;
            }
            String texto = new String(salida.get(5, TimeUnit.SECONDS), StandardCharsets.UTF_8);
            return texto.replaceAll("\\s+", "").length() < 40 ? null : texto;
        } catch (Exception e) {
            LOG.warn("No se pudo ejecutar el OCR ({}): {}", comandoOcr, e.getMessage());
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return null;
        } finally {
            if (proceso != null && proceso.isAlive()) proceso.destroyForcibly();
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException e) {
                    // Archivo temporal del sistema; se limpia al reiniciar.
                }
            }
        }
    }
}
