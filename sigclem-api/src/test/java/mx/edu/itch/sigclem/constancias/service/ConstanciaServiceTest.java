package mx.edu.itch.sigclem.constancias.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.domain.AvanceNivel;
import mx.edu.itch.sigclem.alumnos.domain.AvanceNivelId;
import mx.edu.itch.sigclem.alumnos.domain.EstatusAvance;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository;
import mx.edu.itch.sigclem.catalogos.domain.Carrera;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.comun.excepcion.ConflictoException;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.constancias.domain.Constancia;
import mx.edu.itch.sigclem.constancias.dto.AutorizarConstanciaRequest;
import mx.edu.itch.sigclem.constancias.dto.ConstanciaDTO;
import mx.edu.itch.sigclem.constancias.dto.RechazarConstanciaRequest;
import mx.edu.itch.sigclem.constancias.repository.ConstanciaRepository;
import mx.edu.itch.sigclem.notificaciones.domain.Notificacion;
import mx.edu.itch.sigclem.notificaciones.repository.NotificacionRepository;
import mx.edu.itch.sigclem.seguridad.domain.Rol;
import mx.edu.itch.sigclem.seguridad.domain.RolSistema;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import org.springframework.core.io.Resource;

@ExtendWith(MockitoExtension.class)
class ConstanciaServiceTest {

    @Mock private ConstanciaRepository constanciaRepository;
    @Mock private AlumnoRepository alumnoRepository;
    @Mock private AvanceNivelRepository avanceNivelRepository;
    @Mock private NivelRepository nivelRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private NotificacionRepository notificacionRepository;
    @Mock private ConstanciaPdfService pdfService;
    @Mock private ArchivoConstanciaService archivoService;
    @InjectMocks private ConstanciaService service;

    private Usuario alumnoUsuario;
    private Alumno alumno;
    private List<Nivel> niveles;

    @BeforeEach
    void preparar() {
        alumnoUsuario = usuario("22520001", 1, RolSistema.ALUMNO);
        alumno = new Alumno();
        alumno.setNumeroControl("22520001");
        alumno.setUsuario(alumnoUsuario);
        alumno.setNombre("Ana");
        alumno.setApellidoPaterno("López");
        Carrera carrera = mock(Carrera.class);
        lenient().when(carrera.getNombre()).thenReturn("Ingeniería Civil");
        lenient().when(carrera.getClavePlan()).thenReturn("IC-2020");
        alumno.setCarrera(carrera);
        niveles = niveles(10);

        lenient().when(usuarioRepository.findByNombreUsuario("22520001")).thenReturn(Optional.of(alumnoUsuario));
        lenient().when(alumnoRepository.findByUsuarioId(1)).thenReturn(Optional.of(alumno));
        lenient().when(nivelRepository.findAllByOrderByNumeroAsc()).thenReturn(niveles);
    }

    @Test
    void impideSolicitarConstanciaSiFaltanNiveles() {
        when(avanceNivelRepository.findByIdNumeroControl("22520001"))
                .thenReturn(avancesAcreditadosHasta(9));

        assertThatThrownBy(() -> service.solicitar("22520001"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("10. Nivel 10");
        verify(constanciaRepository, never()).saveAndFlush(any());
    }

    @Test
    void registraSolicitudSoloConLosDiezNivelesYNotificaACoordinacion() {
        when(avanceNivelRepository.findByIdNumeroControl("22520001"))
                .thenReturn(avancesAcreditadosHasta(10));
        when(constanciaRepository.existsByAlumnoNumeroControlAndIdiomaIgnoreCaseAndEstatusIn(
                "22520001", "Inglés", List.of("Solicitada", "Autorizada", "Emitida"))).thenReturn(false);
        when(constanciaRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            Constancia constancia = invocation.getArgument(0);
            constancia.setId(7);
            return constancia;
        });
        Usuario coordinador = usuario("coordinador", 2, RolSistema.COORDINADOR);
        when(usuarioRepository.findFirstByRolNombreAndActivoTrueOrderByIdAsc("Coordinador"))
                .thenReturn(Optional.of(coordinador));

        ConstanciaDTO respuesta = service.solicitar("22520001");

        assertThat(respuesta.estatus()).isEqualTo("Solicitada");
        assertThat(respuesta.nivelesAcreditados()).isEqualTo(10);
        assertThat(respuesta.totalHoras()).isEqualTo(300);
        assertThat(respuesta.nivelMcer()).isEqualTo("B1");
        ArgumentCaptor<Notificacion> notificacion = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository).saveAndFlush(notificacion.capture());
        assertThat(notificacion.getValue().getDestino()).isSameAs(coordinador);
        assertThat(notificacion.getValue().getConstancia()).isNotNull();
        assertThat(notificacion.getValue().isLeida()).isFalse();
    }

    @Test
    void noPermiteDuplicarUnaSolicitudActivaDelMismoIdioma() {
        when(avanceNivelRepository.findByIdNumeroControl("22520001"))
                .thenReturn(avancesAcreditadosHasta(10));
        when(constanciaRepository.existsByAlumnoNumeroControlAndIdiomaIgnoreCaseAndEstatusIn(
                "22520001", "Inglés", List.of("Solicitada", "Autorizada", "Emitida"))).thenReturn(true);

        assertThatThrownBy(() -> service.solicitar("22520001"))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("Ya existe");
        verify(constanciaRepository, never()).saveAndFlush(any());
    }

    @Test
    void emitePdfConFolioUnicoYNotificaALaJefatura() throws Exception {
        Constancia pendiente = constanciaPendiente();
        when(constanciaRepository.buscarParaActualizar(4)).thenReturn(Optional.of(pendiente));
        when(constanciaRepository.existsByFolioIgnoreCase("ITCH2026A001")).thenReturn(false);
        Usuario coordinador = usuario("coordinador", 2, RolSistema.COORDINADOR);
        when(usuarioRepository.findByNombreUsuario("coordinador")).thenReturn(Optional.of(coordinador));
        Usuario jefa = usuario("jefa.dgtv", 3, RolSistema.JEFA_DGTV);
        when(usuarioRepository.findFirstByRolNombreAndActivoTrueOrderByIdAsc("Jefa del DGTV")).thenReturn(Optional.of(jefa));
        byte[] pdf = "%PDF-test".getBytes();
        when(pdfService.generar(any())).thenReturn(pdf);
        when(archivoService.guardar(4, pdf)).thenReturn("constancias/4.pdf");

        ConstanciaDTO emitida = service.autorizar(
                4, new AutorizarConstanciaRequest("ITCH2026A001"), "coordinador");

        assertThat(emitida.estatus()).isEqualTo("Emitida");
        assertThat(emitida.folio()).isEqualTo("ITCH2026A001");
        assertThat(emitida.fechaExpedicion()).isEqualTo(LocalDate.now());
        ArgumentCaptor<Notificacion> notificacion = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository).saveAndFlush(notificacion.capture());
        assertThat(notificacion.getValue().getDestino()).isSameAs(jefa);
        assertThat(notificacion.getValue().getOrigen().getNombreUsuario()).isEqualTo("coordinador");
        assertThat(notificacion.getValue().getConstancia()).isSameAs(pendiente);
    }

    @Test
    void rechazaUnFolioYaUtilizado() {
        when(constanciaRepository.buscarParaActualizar(4)).thenReturn(Optional.of(constanciaPendiente()));
        when(constanciaRepository.existsByFolioIgnoreCase("REPETIDO")).thenReturn(true);

        assertThatThrownBy(() -> service.autorizar(
                4, new AutorizarConstanciaRequest("REPETIDO"), "coordinador"))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("folio ya está asignado");
    }

    @Test
    void rechazaSolicitudConMotivoYNotificaAlAlumno() {
        Constancia pendiente = constanciaPendiente();
        when(constanciaRepository.buscarParaActualizar(4)).thenReturn(Optional.of(pendiente));
        Usuario coordinador = usuario("coordinador", 2, RolSistema.COORDINADOR);
        when(usuarioRepository.findByNombreUsuario("coordinador")).thenReturn(Optional.of(coordinador));
        when(constanciaRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConstanciaDTO rechazada = service.rechazar(
                4, new RechazarConstanciaRequest("Revisa el expediente académico."), "coordinador");

        assertThat(rechazada.estatus()).isEqualTo("Rechazada");
        assertThat(rechazada.motivoRechazo()).isEqualTo("Revisa el expediente académico.");
        ArgumentCaptor<Notificacion> notificacion = ArgumentCaptor.forClass(Notificacion.class);
        verify(notificacionRepository).saveAndFlush(notificacion.capture());
        assertThat(notificacion.getValue().getDestino()).isSameAs(alumnoUsuario);
        assertThat(notificacion.getValue().getOrigen()).isSameAs(coordinador);
        assertThat(notificacion.getValue().getConstancia()).isSameAs(pendiente);
    }

    @Test
    void alumnoSoloPuedeDescargarSuPropiaConstanciaEmitida() {
        Constancia emitida = constanciaPendiente();
        emitida.setEstatus("Emitida");
        emitida.setArchivoPdf("constancias/4.pdf");
        when(constanciaRepository.findConAlumnoById(4)).thenReturn(Optional.of(emitida));
        Resource pdf = mock(Resource.class);
        when(archivoService.cargar("constancias/4.pdf")).thenReturn(pdf);

        assertThat(service.descargarPdf(4, "22520001", true)).isSameAs(pdf);

        Alumno otroAlumno = new Alumno();
        otroAlumno.setNumeroControl("22520002");
        emitida.setAlumno(otroAlumno);
        assertThatThrownBy(() -> service.descargarPdf(4, "22520001", true))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    private List<Nivel> niveles(int cantidad) {
        return java.util.stream.IntStream.rangeClosed(1, cantidad).mapToObj(numero -> {
            Nivel nivel = mock(Nivel.class);
            lenient().when(nivel.getId()).thenReturn(numero);
            lenient().when(nivel.getNumero()).thenReturn(numero);
            lenient().when(nivel.getNombre()).thenReturn("Nivel " + numero);
            lenient().when(nivel.getHoras()).thenReturn(30);
            lenient().when(nivel.getNivelMcer()).thenReturn(numero == 10 ? "B1" : "A1");
            return nivel;
        }).toList();
    }

    private List<AvanceNivel> avancesAcreditadosHasta(int numero) {
        return java.util.stream.IntStream.rangeClosed(1, numero).mapToObj(nivel -> {
            AvanceNivel avance = new AvanceNivel();
            avance.setId(new AvanceNivelId("22520001", nivel));
            avance.setEstatus(EstatusAvance.ACREDITADO);
            return avance;
        }).toList();
    }

    private Constancia constanciaPendiente() {
        Constancia constancia = new Constancia();
        constancia.setId(4);
        constancia.setAlumno(alumno);
        constancia.setFechaSolicitud(LocalDate.now());
        constancia.setIdioma("Inglés");
        constancia.setNivelesAcreditados(10);
        constancia.setTotalHoras(300);
        constancia.setNivelMcer("B1");
        constancia.setEstatus("Solicitada");
        return constancia;
    }

    private Usuario usuario(String nombre, Integer id, RolSistema tipo) {
        Usuario usuario = new Usuario();
        usuario.setId(id);
        usuario.setNombreUsuario(nombre);
        Rol rol = mock(Rol.class);
        lenient().when(rol.comoRolSistema()).thenReturn(tipo);
        usuario.setRol(rol);
        return usuario;
    }
}
