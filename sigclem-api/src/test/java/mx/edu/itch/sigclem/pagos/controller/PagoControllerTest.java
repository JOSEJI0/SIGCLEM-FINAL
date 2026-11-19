package mx.edu.itch.sigclem.pagos.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import mx.edu.itch.sigclem.pagos.service.PagoService;

@ExtendWith(MockitoExtension.class)
class PagoControllerTest {

    @Mock
    private PagoService pagoService;
    @InjectMocks
    private PagoController controller;

    @Test
    void coordinacionAdministradorYJefaSonPersonalDePagosPeroElAlumnoNo() {
        assertTrue(PagoController.esPersonalDePagos(con("ROLE_COORDINADOR")));
        // La jerarquía ADMIN > COORDINADOR no cambia las autoridades del token: ADMIN debe reconocerse aquí.
        assertTrue(PagoController.esPersonalDePagos(con("ROLE_ADMIN")));
        assertTrue(PagoController.esPersonalDePagos(con("ROLE_JEFA_DGTV")));
        assertFalse(PagoController.esPersonalDePagos(con("ROLE_ALUMNO")));
    }

    @Test
    void elArchivoDelComprobanteSeEntregaEnLineaConSuTipoReal() {
        Authentication alumno = con("ROLE_ALUMNO");
        when(pagoService.obtenerArchivo(eq(4), any(), eq(false))).thenReturn(new PagoService.ArchivoComprobante(
                new ByteArrayResource(new byte[] { 1 }), MediaType.IMAGE_PNG, "png"));

        var respuesta = controller.archivo(4, alumno);

        verify(pagoService).obtenerArchivo(4, alumno, false);
        assertEquals(MediaType.IMAGE_PNG, respuesta.getHeaders().getContentType());
        assertEquals("inline; filename=\"comprobante-4.png\"",
                respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION));
        assertEquals("private, no-store", respuesta.getHeaders().getCacheControl());
    }

    @Test
    void elAdministradorConsultaElComprobanteComoPersonalDePagos() {
        Authentication admin = con("ROLE_ADMIN");
        when(pagoService.obtenerArchivo(eq(4), any(), eq(true))).thenReturn(new PagoService.ArchivoComprobante(
                new ByteArrayResource(new byte[] { 1 }), MediaType.APPLICATION_PDF, "pdf"));

        assertEquals(MediaType.APPLICATION_PDF, controller.archivo(4, admin).getHeaders().getContentType());
    }

    private static Authentication con(String rol) {
        return new UsernamePasswordAuthenticationToken("usuario", "n/a", List.of(new SimpleGrantedAuthority(rol)));
    }
}
