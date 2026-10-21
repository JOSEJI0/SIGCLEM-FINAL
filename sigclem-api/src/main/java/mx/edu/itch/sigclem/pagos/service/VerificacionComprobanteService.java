package mx.edu.itch.sigclem.pagos.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.pagos.domain.ComprobantePago;

/**
 * Verificación automática de un comprobante (opción híbrida): QR leído en el servidor, estado del CFDI ante el
 * SAT y cotejo de la descripción del recibo con el alumno y el concepto del cargo. El resultado queda en el
 * comprobante como apoyo; la validación final sigue siendo de coordinación.
 */
@Service
@RequiredArgsConstructor
public class VerificacionComprobanteService {

    private final LectorComprobanteService lector;
    private final ConsultaSatService consultaSat;
    private final ArchivoComprobanteService archivoService;

    @Value("${app.cfdi.rfc-receptor:XAXX010101000}")
    private String rfcReceptorPorOmision;

    /** Lee QR y texto del archivo subido. */
    public LectorComprobanteService.Lectura leer(byte[] contenido) {
        return lector.leer(contenido, archivoService.detectarFormato(contenido));
    }

    /** Vuelve a leer el archivo almacenado del comprobante. */
    public LectorComprobanteService.Lectura releer(ComprobantePago comprobante) {
        return leer(archivoService.contenido(comprobante.getArchivo()));
    }

    /** Consulta al SAT con los datos ya registrados en el comprobante y guarda el resultado en él. */
    public ConsultaSatService.ResultadoSat consultarSat(ComprobantePago comprobante) {
        CfdiQr qr = CfdiQr.interpretar(comprobante.getContenidoQr());
        boolean qrDelMismoCfdi = qr != null && qr.uuid().equalsIgnoreCase(String.valueOf(comprobante.getUuidCfdi()));
        String receptor = qrDelMismoCfdi && qr.rfcReceptor() != null ? qr.rfcReceptor() : rfcReceptorPorOmision;
        String total = qrDelMismoCfdi ? qr.totalTexto()
                : comprobante.getTotal() == null ? null : comprobante.getTotal().toPlainString();
        String sello = comprobante.getSelloFinal() != null ? comprobante.getSelloFinal()
                : qrDelMismoCfdi ? qr.selloFinal() : null;
        if (comprobante.getSelloFinal() == null && sello != null) {
            comprobante.setSelloFinal(sello);
        }
        ConsultaSatService.ResultadoSat resultado = consultaSat.consultar(comprobante.getUuidCfdi(),
                comprobante.getRfcEmisor(), receptor, total, sello);
        comprobante.setEstadoSat(resultado.estado());
        comprobante.setDetalleSat(resultado.detalle());
        comprobante.setFechaConsultaSat(LocalDateTime.now());
        return resultado;
    }

    /** Coteja el texto del recibo con el alumno y el concepto del cargo y guarda el resultado. */
    public void cotejar(ComprobantePago comprobante, String texto) {
        var autorizacion = comprobante.getAutorizacion();
        var alumno = autorizacion.getAlumno();
        CotejoDescripcion.Resultado resultado = CotejoDescripcion.cotejar(texto, alumno.getNumeroControl(),
                alumno.getNombre(), alumno.getApellidoPaterno(), alumno.getApellidoMaterno(),
                autorizacion.getConcepto());
        comprobante.setCotejoDescripcion(resultado.estatus());
        comprobante.setDetalleCotejo(resultado.detalle().length() > 400
                ? resultado.detalle().substring(0, 400) : resultado.detalle());
        comprobante.setDescripcionLeida(resultado.descripcion());
    }
}
