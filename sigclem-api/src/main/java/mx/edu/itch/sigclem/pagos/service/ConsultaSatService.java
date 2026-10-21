package mx.edu.itch.sigclem.pagos.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Consulta el estado de un CFDI en el servicio público del SAT (ConsultaCFDIService), el mismo que usa el
 * verificador al que apunta el QR del comprobante. No requiere credenciales.
 */
@Service
public class ConsultaSatService {

    public static final String VIGENTE = "Vigente";
    public static final String CANCELADO = "Cancelado";
    public static final String NO_ENCONTRADO = "No encontrado";
    public static final String NO_DISPONIBLE = "No disponible";
    public static final String SIN_VERIFICAR = "Sin verificar";

    private static final Logger LOG = LoggerFactory.getLogger(ConsultaSatService.class);
    private static final String SOAP_ACTION = "http://tempuri.org/IConsultaCFDIService/Consulta";

    public record ResultadoSat(String estado, String detalle) {
    }

    private final boolean habilitado;
    private final String url;
    private final Duration tiempoMaximo;
    private final HttpClient cliente;

    public ConsultaSatService(
            @Value("${app.sat.habilitado:true}") boolean habilitado,
            @Value("${app.sat.url:https://consultaqr.facturaelectronica.sat.gob.mx/ConsultaCFDIService.svc}") String url,
            @Value("${app.sat.tiempo-maximo-segundos:10}") long segundos) {
        this.habilitado = habilitado;
        this.url = url;
        this.tiempoMaximo = Duration.ofSeconds(segundos);
        this.cliente = HttpClient.newBuilder().connectTimeout(tiempoMaximo).build();
    }

    /**
     * @param total      total tal como aparece en el QR ({@code tt}) o, en captura manual, con dos decimales
     * @param selloFinal últimos 8 caracteres del sello digital del CFDI; sin él el SAT no responde
     */
    public ResultadoSat consultar(String uuid, String rfcEmisor, String rfcReceptor, String total, String selloFinal) {
        if (!habilitado) {
            return new ResultadoSat(SIN_VERIFICAR, "La consulta automática al SAT está desactivada.");
        }
        if (uuid == null || rfcEmisor == null || rfcReceptor == null || total == null) {
            return new ResultadoSat(SIN_VERIFICAR, "Faltan datos del CFDI para consultar al SAT.");
        }
        if (selloFinal == null || selloFinal.isBlank()) {
            return new ResultadoSat(SIN_VERIFICAR,
                    "Falta el final del sello digital (fe); el SAT lo exige para consultar el CFDI.");
        }
        String expresion = "?re=" + rfcEmisor + "&rr=" + rfcReceptor + "&tt=" + total + "&id=" + uuid
                + "&fe=" + selloFinal;
        String sobre = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tem="http://tempuri.org/">
                  <soapenv:Header/>
                  <soapenv:Body>
                    <tem:Consulta>
                      <tem:expresionImpresa><![CDATA[%s]]></tem:expresionImpresa>
                    </tem:Consulta>
                  </soapenv:Body>
                </soapenv:Envelope>""".formatted(expresion.replace("]]>", ""));
        try {
            HttpRequest peticion = HttpRequest.newBuilder(URI.create(url))
                    .timeout(tiempoMaximo)
                    .header("Content-Type", "text/xml; charset=utf-8")
                    .header("SOAPAction", SOAP_ACTION)
                    .POST(HttpRequest.BodyPublishers.ofString(sobre, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                LOG.warn("El SAT respondió HTTP {} al consultar el CFDI {}", respuesta.statusCode(), uuid);
                return new ResultadoSat(NO_DISPONIBLE, "El servicio del SAT respondió con error " + respuesta.statusCode() + ".");
            }
            return interpretar(respuesta.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ResultadoSat(NO_DISPONIBLE, "La consulta al SAT se interrumpió.");
        } catch (Exception e) {
            LOG.warn("No se pudo consultar el CFDI {} en el SAT: {}", uuid, e.toString());
            return new ResultadoSat(NO_DISPONIBLE, "No hubo respuesta del SAT; intenta reverificar más tarde.");
        }
    }

    static ResultadoSat interpretar(String xml) {
        String estado = etiqueta(xml, "Estado");
        String codigo = etiqueta(xml, "CodigoEstatus");
        List<String> partes = new ArrayList<>();
        if (codigo != null) partes.add(codigo);
        String cancelacion = etiqueta(xml, "EstatusCancelacion");
        if (cancelacion != null && !cancelacion.isBlank()) partes.add("Cancelación: " + cancelacion);
        String efos = etiqueta(xml, "ValidacionEFOS");
        if (efos != null && !efos.isBlank() && !"200".equals(efos.trim())) partes.add("EFOS: " + efos);
        String detalle = String.join(" · ", partes);
        if (detalle.length() > 200) detalle = detalle.substring(0, 200);

        String normalizado = estado == null ? "" : estado.trim().toLowerCase(Locale.ROOT);
        if (normalizado.equals("vigente")) return new ResultadoSat(VIGENTE, detalle);
        if (normalizado.equals("cancelado")) return new ResultadoSat(CANCELADO, detalle);
        if (normalizado.startsWith("no encontrado")) return new ResultadoSat(NO_ENCONTRADO, detalle);
        return new ResultadoSat(NO_DISPONIBLE, detalle.isBlank() ? "Respuesta del SAT no reconocida." : detalle);
    }

    private static String etiqueta(String xml, String nombre) {
        Matcher m = Pattern.compile("<(?:\\w+:)?" + nombre + "(?:\\s[^>]*)?>([^<]*)</(?:\\w+:)?" + nombre + ">")
                .matcher(xml == null ? "" : xml);
        return m.find() ? m.group(1).trim() : null;
    }
}
