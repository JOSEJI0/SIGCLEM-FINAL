package mx.edu.itch.sigclem.pagos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.domain.AvanceNivel;
import mx.edu.itch.sigclem.alumnos.domain.EstatusAvance;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.domain.Periodo;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository;
import mx.edu.itch.sigclem.comun.excepcion.ConflictoException;
import mx.edu.itch.sigclem.comun.excepcion.ReglaNegocioException;
import mx.edu.itch.sigclem.grupos.repository.GrupoRepository;
import mx.edu.itch.sigclem.grupos.repository.InscripcionRepository;
import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.pagos.domain.AutorizacionPago;
import mx.edu.itch.sigclem.pagos.domain.ComprobantePago;
import mx.edu.itch.sigclem.pagos.domain.ConceptoPago;
import mx.edu.itch.sigclem.pagos.dto.TarifaRequest;
import mx.edu.itch.sigclem.pagos.dto.ValidarComprobanteRequest;
import mx.edu.itch.sigclem.pagos.repository.AutorizacionPagoRepository;
import mx.edu.itch.sigclem.pagos.repository.ComprobantePagoRepository;
import mx.edu.itch.sigclem.pagos.repository.ConceptoPagoRepository;
import mx.edu.itch.sigclem.pagos.repository.TarifaPagoRepository;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.notificaciones.repository.NotificacionRepository;

@ExtendWith(MockitoExtension.class)
class PagoServiceTest {

    @Mock
    private TarifaPagoRepository tarifaRepository;
    @Mock
    private ConceptoPagoRepository conceptoRepository;
    @Mock
    private AutorizacionPagoRepository autorizacionRepository;
    @Mock
    private ComprobantePagoRepository comprobanteRepository;
    @Mock
    private AlumnoRepository alumnoRepository;
    @Mock
    private AvanceNivelRepository avanceRepository;
    @Mock
    private NivelRepository nivelRepository;
    @Mock
    private PeriodoRepository periodoRepository;
    @Mock
    private GrupoRepository grupoRepository;
    @Mock
    private InscripcionRepository inscripcionRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ArchivoComprobanteService archivoService;
    @Mock
    private NotificacionRepository notificacionRepository;
    @Mock
    private VerificacionComprobanteService verificacion;

    @InjectMocks
    private PagoService service;

    @Test
    void crearTarifaImpideDosTarifasActivasParaElMismoNivel() {
        prepararConceptoCurso();
        Nivel nivel = mock(Nivel.class);
        when(nivel.getId()).thenReturn(1);
        when(nivelRepository.findById(1)).thenReturn(Optional.of(nivel));
        when(tarifaRepository.existsByConceptoAndNivelIdAndActivaTrue("Curso de nivel", 1)).thenReturn(true);

        assertThrows(ConflictoException.class,
                () -> service.crearTarifa(new TarifaRequest("Curso de nivel", 1, new BigDecimal("1250.00"))));

        verify(tarifaRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validarComprobanteRechazaAbonoSuperiorAlSaldo() {
        ComprobantePago comprobante = new ComprobantePago();
        AutorizacionPago autorizacion = new AutorizacionPago();
        autorizacion.setId(77);
        autorizacion.setEstatus("Pendiente");
        autorizacion.setSaldoPendiente(new BigDecimal("1250.00"));
        autorizacion.setMontoEsperado(new BigDecimal("1250.00"));
        comprobante.setAutorizacion(autorizacion);
        comprobante.setEstatus("Pendiente");
        comprobante.setUuidCfdi("52FDE753-84E9-4C12-8A91-AE7A3B47F7DD");
        comprobante.setRfcEmisor("TNM140723GFA");
        comprobante.setTotal(new BigDecimal("1300.00"));
        when(comprobanteRepository.bloquearPorId(7)).thenReturn(Optional.of(comprobante));
        when(autorizacionRepository.bloquearPorId(77)).thenReturn(Optional.of(autorizacion));

        assertThrows(ReglaNegocioException.class,
                () -> service.validarComprobante(7, new ValidarComprobanteRequest(3), coordinacion()));

        verify(inscripcionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void validarComprobanteRechazaUnComprobanteYaProcesado() {
        ComprobantePago comprobante = new ComprobantePago();
        comprobante.setEstatus("Validado");
        when(comprobanteRepository.bloquearPorId(7)).thenReturn(Optional.of(comprobante));

        assertThrows(ReglaNegocioException.class,
                () -> service.validarComprobante(7, new ValidarComprobanteRequest(3), coordinacion()));

        verify(comprobanteRepository, never()).save(comprobante);
    }

    @Test
    void validarPagoDeCursoAplicaInscripcionYMarcaNivelComoCursando() {
        Alumno alumno = new Alumno();
        alumno.setNumeroControl("22520187");
        Nivel nivel = mock(Nivel.class);
        when(nivel.getId()).thenReturn(1);
        when(nivel.getNombre()).thenReturn("Básico 1");
        Periodo periodo = new Periodo();
        periodo.setId(2);
        periodo.setNombre("Enero-Junio");
        periodo.setFechaInicio(LocalDate.now().minusDays(1));
        periodo.setFechaFin(LocalDate.now().plusDays(30));

        AutorizacionPago autorizacion = new AutorizacionPago();
        autorizacion.setId(88);
        autorizacion.setAlumno(alumno);
        autorizacion.setConcepto("Curso de nivel");
        autorizacion.setNivel(nivel);
        autorizacion.setPeriodo(periodo);
        autorizacion.setMontoEsperado(new BigDecimal("1250.00"));
        autorizacion.setSaldoPendiente(new BigDecimal("1250.00"));
        autorizacion.setEstatus("Pendiente");
        when(autorizacionRepository.bloquearPorId(88)).thenReturn(Optional.of(autorizacion));
        ComprobantePago comprobante = new ComprobantePago();
        comprobante.setAutorizacion(autorizacion);
        comprobante.setEstatus("Pendiente");
        comprobante.setUuidCfdi("52FDE753-84E9-4C12-8A91-AE7A3B47F7DD");
        comprobante.setRfcEmisor("TNM140723GFA");
        comprobante.setTotal(new BigDecimal("1250.00"));

        Grupo grupo = mock(Grupo.class);
        when(grupo.getId()).thenReturn(33);
        when(grupo.getNivel()).thenReturn(nivel);
        when(grupo.getPeriodo()).thenReturn(periodo);
        when(grupo.getCupo()).thenReturn(25);
        when(grupo.getEstatusActa()).thenReturn("Abierta");
        when(comprobanteRepository.bloquearPorId(5)).thenReturn(Optional.of(comprobante));
        when(grupoRepository.bloquearPorId(33)).thenReturn(Optional.of(grupo));
        when(inscripcionRepository.existsByAlumnoNumeroControlAndGrupoId("22520187", 33)).thenReturn(false);
        when(inscripcionRepository.countByGrupoId(33)).thenReturn(3L);
        AvanceNivel avance = new AvanceNivel("22520187", 1, EstatusAvance.HABILITADO);
        when(avanceRepository.findByIdNumeroControlAndIdIdNivel("22520187", 1)).thenReturn(Optional.of(avance));
        when(usuarioRepository.findByNombreUsuario("coordinador")).thenReturn(Optional.of(new Usuario()));
        when(comprobanteRepository.save(any(ComprobantePago.class))).thenAnswer(inv -> inv.getArgument(0));

        var resultado = service.validarComprobante(5, new ValidarComprobanteRequest(33), coordinacion());

        assertEquals("Validado", resultado.estatus());
        assertEquals(EstatusAvance.CURSANDO, avance.getEstatus());
        verify(inscripcionRepository).save(any());
        verify(avanceRepository).save(avance);
    }

    @Test
    void validarAbonoParcialActualizaSaldoSinInscribirAlAlumno() {
        Alumno alumno = new Alumno();
        alumno.setNumeroControl("22520187");
        Periodo periodo = new Periodo();
        periodo.setId(2);
        periodo.setNombre("Enero-Junio");

        AutorizacionPago autorizacion = new AutorizacionPago();
        autorizacion.setId(89);
        autorizacion.setAlumno(alumno);
        autorizacion.setConcepto("Examen de colocación");
        autorizacion.setPeriodo(periodo);
        autorizacion.setMontoEsperado(new BigDecimal("1250.00"));
        autorizacion.setSaldoPendiente(new BigDecimal("1250.00"));
        autorizacion.setEstatus("Pendiente");

        ComprobantePago comprobante = new ComprobantePago();
        comprobante.setAutorizacion(autorizacion);
        comprobante.setEstatus("Pendiente");
        comprobante.setUuidCfdi("52FDE753-84E9-4C12-8A91-AE7A3B47F7DD");
        comprobante.setRfcEmisor("TNM140723GFA");
        comprobante.setTotal(new BigDecimal("500.00"));

        when(comprobanteRepository.bloquearPorId(6)).thenReturn(Optional.of(comprobante));
        when(autorizacionRepository.bloquearPorId(89)).thenReturn(Optional.of(autorizacion));
        when(usuarioRepository.findByNombreUsuario("coordinador")).thenReturn(Optional.of(new Usuario()));
        when(comprobanteRepository.save(any(ComprobantePago.class))).thenAnswer(inv -> inv.getArgument(0));

        var resultado = service.validarComprobante(6, null, coordinacion());

        assertEquals("Validado", resultado.estatus());
        assertEquals("Parcial", autorizacion.getEstatus());
        assertEquals(new BigDecimal("750.00"), autorizacion.getSaldoPendiente());
        verify(inscripcionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void crearTarifaNormalizaMontoAjustandoDosDecimales() {
        prepararConceptoCurso();
        Nivel nivel = mock(Nivel.class);
        when(nivel.getId()).thenReturn(1);
        when(nivelRepository.findById(1)).thenReturn(Optional.of(nivel));
        when(tarifaRepository.existsByConceptoAndNivelIdAndActivaTrue("Curso de nivel", 1)).thenReturn(false);
        when(tarifaRepository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        var creada = service.crearTarifa(new TarifaRequest("Curso de nivel", 1, new BigDecimal("1250")));

        assertEquals(new BigDecimal("1250.00"), creada.monto());
    }

    @Test
    void cargarComprobanteRechazaDatosDelQrQueNoCoincidenConElCfdi() {
        Alumno alumno = new Alumno();
        alumno.setNumeroControl("22520187");
        AutorizacionPago autorizacion = new AutorizacionPago();
        autorizacion.setAlumno(alumno);
        when(autorizacionRepository.bloquearPorId(9)).thenReturn(Optional.of(autorizacion));
        when(comprobanteRepository.existsByAutorizacionIdAndEstatus(9, "Pendiente")).thenReturn(false);
        when(comprobanteRepository.existsByUuidCfdiIgnoreCase("52FDE753-84E9-4C12-8A91-AE7A3B47F7DD"))
                .thenReturn(false);
        when(verificacion.leer(any())).thenReturn(new LectorComprobanteService.Lectura(null, null, null));
        var auth = new UsernamePasswordAuthenticationToken("22520187", "n/a");
        String qr = "https://verificacfdi.facturaelectronica.sat.gob.mx/?id=52FDE753-84E9-4C12-8A91-AE7A3B47F7DD"
                + "&re=TNM140723GFA&tt=1250.00";

        assertThrows(mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException.class,
                () -> service.cargarComprobante(9, "52FDE753-84E9-4C12-8A91-AE7A3B47F7DD",
                        "TNM140723GFA", "1200.00", qr, mock(org.springframework.web.multipart.MultipartFile.class), auth));

        verify(archivoService, never()).guardar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void cargarComprobanteTomaLosDatosDelQrLeidoEnElServidorYConsultaAlSat() {
        AutorizacionPago autorizacion = cargoAbierto(new BigDecimal("650.00"));
        when(autorizacionRepository.bloquearPorId(9)).thenReturn(Optional.of(autorizacion));
        String qr = "https://verificacfdi.facturaelectronica.sat.gob.mx/default.aspx?&id=05528398-BC9D-4F97-9011-93AF8CD18E63"
                + "&re=TNM140723GFA&rr=XAXX010101000&tt=650.00&fe=P/Dk7w==";
        when(verificacion.leer(any())).thenReturn(new LectorComprobanteService.Lectura(qr, null, null));
        when(verificacion.consultarSat(any())).thenReturn(new ConsultaSatService.ResultadoSat("Vigente", "S"));
        when(archivoService.guardar(any())).thenReturn("archivo.pdf");
        when(comprobanteRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var dto = service.cargarComprobante(9, null, null, null, null,
                mock(org.springframework.web.multipart.MultipartFile.class),
                new UsernamePasswordAuthenticationToken("22520187", "n/a"));

        assertEquals("05528398-BC9D-4F97-9011-93AF8CD18E63", dto.uuidCfdi());
        assertEquals("TNM140723GFA", dto.rfcEmisor());
        assertEquals(new BigDecimal("650.00"), dto.total());
        assertEquals("QR servidor", dto.origenDatos());
        verify(verificacion).cotejar(any(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void cargarComprobanteRechazaUnCfdiCanceladoAnteElSat() {
        AutorizacionPago autorizacion = cargoAbierto(new BigDecimal("650.00"));
        when(autorizacionRepository.bloquearPorId(9)).thenReturn(Optional.of(autorizacion));
        String qr = "?id=05528398-BC9D-4F97-9011-93AF8CD18E63&re=TNM140723GFA&rr=XAXX010101000&tt=650.00&fe=P/Dk7w==";
        when(verificacion.leer(any())).thenReturn(new LectorComprobanteService.Lectura(qr, null, null));
        when(verificacion.consultarSat(any())).thenReturn(new ConsultaSatService.ResultadoSat("Cancelado", "S"));

        assertThrows(ReglaNegocioException.class, () -> service.cargarComprobante(9, null, null, null, null,
                mock(org.springframework.web.multipart.MultipartFile.class),
                new UsernamePasswordAuthenticationToken("22520187", "n/a")));

        verify(archivoService, never()).guardar(any());
        verify(comprobanteRepository, never()).save(any());
    }

    @Test
    void validarComprobanteBloqueaUnCfdiCancelado() {
        AutorizacionPago autorizacion = cargoAbierto(new BigDecimal("650.00"));
        autorizacion.setId(9);
        ComprobantePago comprobante = new ComprobantePago();
        comprobante.setAutorizacion(autorizacion);
        comprobante.setUuidCfdi("05528398-BC9D-4F97-9011-93AF8CD18E63");
        comprobante.setRfcEmisor("TNM140723GFA");
        comprobante.setTotal(new BigDecimal("650.00"));
        comprobante.setEstadoSat("Cancelado");
        when(comprobanteRepository.bloquearPorId(5)).thenReturn(Optional.of(comprobante));
        when(autorizacionRepository.bloquearPorId(9)).thenReturn(Optional.of(autorizacion));

        assertThrows(ReglaNegocioException.class, () -> service.validarComprobante(5, null, coordinacion()));
    }

    private AutorizacionPago cargoAbierto(BigDecimal saldo) {
        Alumno alumno = new Alumno();
        alumno.setNumeroControl("22520187");
        AutorizacionPago autorizacion = new AutorizacionPago();
        autorizacion.setAlumno(alumno);
        autorizacion.setConcepto("Curso de nivel");
        autorizacion.setEstatus("Pendiente");
        autorizacion.setMontoEsperado(saldo);
        autorizacion.setSaldoPendiente(saldo);
        Periodo periodo = new Periodo();
        periodo.setNombre("Agosto-Diciembre");
        autorizacion.setPeriodo(periodo);
        return autorizacion;
    }

    private Authentication coordinacion() {
        return new UsernamePasswordAuthenticationToken("coordinador", "n/a");
    }

    private void prepararConceptoCurso() {
        ConceptoPago concepto = new ConceptoPago();
        concepto.setNombre("Curso de nivel");
        concepto.setRequiereNivel(true);
        when(conceptoRepository.findByNombreAndActivoTrue("Curso de nivel")).thenReturn(Optional.of(concepto));
    }
}
