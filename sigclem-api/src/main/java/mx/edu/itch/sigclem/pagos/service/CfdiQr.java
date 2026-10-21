package mx.edu.itch.sigclem.pagos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Datos del QR de un CFDI 4.0 (verificador del SAT):
 * {@code https://verificacfdi.facturaelectronica.sat.gob.mx/default.aspx?id=UUID&re=RFC&rr=RFC&tt=TOTAL&fe=SELLO8}.
 *
 * @param totalTexto valor de {@code tt} tal como viene en el QR (el SAT lo espera así en la consulta)
 * @param selloFinal últimos 8 caracteres del sello digital del CFDI ({@code fe}); puede ser null
 */
public record CfdiQr(String uuid, String rfcEmisor, String rfcReceptor, String totalTexto, BigDecimal total,
        String selloFinal) {

    /** Interpreta el contenido del QR; devuelve null si no trae id, re y tt válidos. */
    public static CfdiQr interpretar(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            return null;
        }
        String uuid = parametro(contenido, "id");
        String re = parametro(contenido, "re");
        String tt = parametro(contenido, "tt");
        if (uuid == null || re == null || tt == null
                || !uuid.matches("[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}")) {
            return null;
        }
        BigDecimal total;
        try {
            total = new BigDecimal(tt.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return null;
        }
        String fe = parametro(contenido, "fe");
        return new CfdiQr(uuid.trim().toUpperCase(Locale.ROOT), re.trim().toUpperCase(Locale.ROOT),
                mayusculas(parametro(contenido, "rr")), tt.trim(), total,
                fe == null || fe.isBlank() || fe.length() > 8 ? null : fe.trim());
    }

    /**
     * Valor de un parámetro del QR. El sello ({@code fe}) es Base64 y puede traer «+»: se decodifican solo las
     * secuencias %XX para no convertir «+» en espacio.
     */
    static String parametro(String contenido, String nombre) {
        Matcher matcher = Pattern.compile("(?:^|[?&])" + nombre + "=([^&\\s]*)", Pattern.CASE_INSENSITIVE)
                .matcher(contenido.trim());
        if (!matcher.find()) {
            return null;
        }
        try {
            return URLDecoder.decode(matcher.group(1).replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String mayusculas(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim().toUpperCase(Locale.ROOT);
    }
}
