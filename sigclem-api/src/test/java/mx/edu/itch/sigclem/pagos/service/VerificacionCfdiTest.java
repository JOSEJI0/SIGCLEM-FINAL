package mx.edu.itch.sigclem.pagos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;

/** Lectura del QR del CFDI, cotejo de la descripción y respuesta del SAT, con los recibos reales del TecNM. */
class VerificacionCfdiTest {

    private static final String QR_650 = "https://verificacfdi.facturaelectronica.sat.gob.mx/default.aspx?&id="
            + "05528398-BC9D-4F97-9011-93AF8CD18E63&re=TNM140723GFA&rr=XAXX010101000&tt=650.00&fe=P/Dk7w==";

    private static final String RECIBO_CURSO = """
            Cantidad Unidad Clave de Unidad Clave Producto/Servicio Descripción Valor Unitario Descuentos Impuestos
            1 SERVICIO E48 86121700 CURSO DE LENGUA EXTRANJERA MODALIDAD PRESENCIAL 650.00 0.00 Base: 650.00
            ISIC 22520187 TEPETITLAN MANCILLA JOSE DE JESUS
            Importe con letra: SEISCIENTOS CINCUENTA PESOS 00/100 M.N.
            """;

    private static final String RECIBO_REINSCRIPCION = """
            Descripción
            REINSCRIPCIÓN LICENCIATURA ISIC 22520187 TEPETITLAN MANCILLA JOSE DE JESUS
            Importe con letra: DOS MIL QUINIENTOS PESOS 00/100 M.N.
            """;

    @Test
    void interpretaElQrDelVerificadorDelSat() {
        CfdiQr qr = CfdiQr.interpretar(QR_650);

        assertEquals("05528398-BC9D-4F97-9011-93AF8CD18E63", qr.uuid());
        assertEquals("TNM140723GFA", qr.rfcEmisor());
        assertEquals("XAXX010101000", qr.rfcReceptor());
        assertEquals(new BigDecimal("650.00"), qr.total());
        assertEquals("P/Dk7w==", qr.selloFinal());
    }

    @Test
    void conservaElSignoMasDelSelloYNormalizaElTotalConCeros() {
        CfdiQr qr = CfdiQr.interpretar("?re=TNM140723GFA&rr=XAXX010101000&tt=0000000650.000000"
                + "&id=05528398-bc9d-4f97-9011-93af8cd18e63&fe=ab+c/12=");

        assertEquals("ab+c/12=", qr.selloFinal());
        assertEquals("0000000650.000000", qr.totalTexto());
        assertEquals(new BigDecimal("650.00"), qr.total());
        assertEquals("05528398-BC9D-4F97-9011-93AF8CD18E63", qr.uuid());
    }

    @Test
    void ignoraQrQueNoSonDeUnCfdi() {
        assertNull(CfdiQr.interpretar("https://www.itchilpancingo.edu.mx"));
        assertNull(CfdiQr.interpretar("?id=no-es-uuid&re=TNM140723GFA&tt=650"));
        assertNull(CfdiQr.interpretar(null));
    }

    @Test
    void elCotejoCoincideConElNumeroDeControlYElConcepto() {
        var resultado = CotejoDescripcion.cotejar(RECIBO_CURSO, "22520187", "José de Jesús", "Tepetitlán",
                "Mancilla", "Curso de nivel");

        assertEquals(CotejoDescripcion.COINCIDE, resultado.estatus());
        assertTrue(resultado.descripcion().contains("CURSO DE LENGUA EXTRANJERA"));
    }

    @Test
    void elCotejoDetectaUnReciboDeOtroConcepto() {
        var resultado = CotejoDescripcion.cotejar(RECIBO_REINSCRIPCION, "22520187", "José de Jesús", "Tepetitlán",
                "Mancilla", "Curso de nivel");

        assertEquals(CotejoDescripcion.PARCIAL, resultado.estatus());
        assertTrue(resultado.detalle().contains("no corresponde"));
    }

    @Test
    void elCotejoDetectaUnReciboDeOtroAlumno() {
        var resultado = CotejoDescripcion.cotejar(RECIBO_CURSO, "22520999", "Ana", "López", "Ruiz",
                "Curso de nivel");

        assertEquals(CotejoDescripcion.NO_COINCIDE, resultado.estatus());
    }

    @Test
    void elCotejoToleraErroresDelOcrEnElNumeroDeControl() {
        var resultado = CotejoDescripcion.cotejar(RECIBO_CURSO.replace("22520187", "2252O187"), "22520187",
                "José de Jesús", "Tepetitlán", "Mancilla", "Curso de nivel");

        assertEquals(CotejoDescripcion.COINCIDE, resultado.estatus());
    }

    @Test
    void sinTextoElCotejoQuedaParaRevisionVisual() {
        assertEquals(CotejoDescripcion.SIN_TEXTO,
                CotejoDescripcion.cotejar(null, "22520187", "A", "B", "C", "Curso de nivel").estatus());
    }

    @Test
    void interpretaLasRespuestasDelServicioDelSat() {
        String vigente = """
                <s:Envelope><s:Body><ConsultaResponse><ConsultaResult xmlns:a="http://schemas.datacontract.org/2004/07/Sat.Cfdi.Negocio.ConsultaCfdi.Servicio">
                <a:CodigoEstatus>S - Comprobante obtenido satisfactoriamente.</a:CodigoEstatus><a:EsCancelable>Cancelable sin aceptación</a:EsCancelable>
                <a:Estado>Vigente</a:Estado><a:EstatusCancelacion/><a:ValidacionEFOS>200</a:ValidacionEFOS>
                </ConsultaResult></ConsultaResponse></s:Body></s:Envelope>""";
        String noEncontrado = "<a:CodigoEstatus>N - 601: La expresión impresa proporcionada no es válida.</a:CodigoEstatus>"
                + "<a:Estado>No Encontrado</a:Estado>";

        assertEquals(ConsultaSatService.VIGENTE, ConsultaSatService.interpretar(vigente).estado());
        assertEquals(ConsultaSatService.CANCELADO,
                ConsultaSatService.interpretar("<a:Estado>Cancelado</a:Estado>").estado());
        assertEquals(ConsultaSatService.NO_ENCONTRADO, ConsultaSatService.interpretar(noEncontrado).estado());
        assertEquals(ConsultaSatService.NO_DISPONIBLE, ConsultaSatService.interpretar("<html>error</html>").estado());
    }

    @Test
    void sinSelloNoSeConsultaAlSat() {
        var sat = new ConsultaSatService(true, "http://localhost:1/no-usado", 1);

        assertEquals(ConsultaSatService.SIN_VERIFICAR,
                sat.consultar("05528398-BC9D-4F97-9011-93AF8CD18E63", "TNM140723GFA", "XAXX010101000", "650.00",
                        null).estado());
    }

    @Test
    void aceptaPdfJpgYPngPorSuFirmaYRechazaOtrosArchivos() {
        var archivos = new ArchivoComprobanteService(System.getProperty("java.io.tmpdir"));

        assertEquals(ArchivoComprobanteService.Formato.PDF, archivos.detectarFormato("%PDF-1.7".getBytes()));
        assertEquals(ArchivoComprobanteService.Formato.PNG,
                archivos.detectarFormato(new byte[] { (byte) 0x89, 'P', 'N', 'G', 13, 10 }));
        assertEquals(ArchivoComprobanteService.Formato.JPG,
                archivos.detectarFormato(new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 0 }));
        assertThrows(DatosInvalidosException.class, () -> archivos.detectarFormato("GIF89a".getBytes()));
    }
}
