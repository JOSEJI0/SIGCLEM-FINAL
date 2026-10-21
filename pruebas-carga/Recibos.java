import com.google.zxing.*; import com.google.zxing.qrcode.QRCodeWriter; import com.google.zxing.client.j2se.MatrixToImageWriter;
import org.apache.pdfbox.pdmodel.*; import org.apache.pdfbox.pdmodel.common.PDRectangle; import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import java.nio.file.*; import java.nio.charset.StandardCharsets;

/** Genera recibos de prueba en lote. Cada línea del CSV: archivo|uuid|total|linea1|linea2 (UUID inventado). */
public class Recibos { public static void main(String[] a) throws Exception {
  Path salida = Path.of(a[0]); Files.createDirectories(salida);
  for (String fila : Files.readAllLines(Path.of(a[1]), StandardCharsets.UTF_8)) {
    if (fila.isBlank()) continue;
    String[] c = fila.split("\\|", -1);
    String qr = "https://verificacfdi.facturaelectronica.sat.gob.mx/default.aspx?&id=" + c[1]
        + "&re=TNM140723GFA&rr=XAXX010101000&tt=" + c[2] + "&fe=AbCd12==";
    var matriz = new QRCodeWriter().encode(qr, BarcodeFormat.QR_CODE, 300, 300);
    try (var doc = new PDDocument()) {
      var pag = new PDPage(PDRectangle.LETTER); doc.addPage(pag);
      try (var cs = new PDPageContentStream(doc, pag)) {
        String[] lineas = { "TECNOLOGICO NACIONAL DE MEXICO   RFC: TNM140723GFA   Recibo Oficial de Cobro",
            "Cantidad Unidad Clave de Unidad Clave Producto/Servicio Descripción Valor Unitario",
            "1 SERVICIO E48 86121700 " + c[3] + " " + c[2], c[4],
            "Importe con letra: PESOS 00/100 M.N.   Total: " + c[2], "Folio fiscal: " + c[1] };
        int y = 740;
        for (String l : lineas) { cs.beginText(); cs.setFont(PDType1Font.HELVETICA, 9); cs.newLineAtOffset(40, y); cs.showText(l); cs.endText(); y -= 18; }
        cs.drawImage(LosslessFactory.createFromImage(doc, MatrixToImageWriter.toBufferedImage(matriz)), 60, 250, 150, 150);
      }
      doc.save(salida.resolve(c[0]).toFile());
    }
  }
}}
