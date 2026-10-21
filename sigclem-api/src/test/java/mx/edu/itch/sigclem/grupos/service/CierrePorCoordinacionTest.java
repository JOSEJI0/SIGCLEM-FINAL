package mx.edu.itch.sigclem.grupos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.domain.AvanceNivel;
import mx.edu.itch.sigclem.alumnos.domain.EstatusAvance;
import mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.ReglaNegocioException;
import mx.edu.itch.sigclem.grupos.domain.AjusteAvance;
import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.grupos.domain.Inscripcion;
import mx.edu.itch.sigclem.grupos.repository.AjusteAvanceRepository;
import mx.edu.itch.sigclem.grupos.repository.GrupoRepository;
import mx.edu.itch.sigclem.grupos.repository.InscripcionRepository;
import mx.edu.itch.sigclem.pagos.domain.AutorizacionPago;
import mx.edu.itch.sigclem.pagos.repository.AutorizacionPagoRepository;

/** Cierre del acta por coordinación aunque haya cursos sin liquidar, y asignación posterior del nivel (cambio 13). */
@ExtendWith(MockitoExtension.class)
class CierrePorCoordinacionTest {

    private static final String OBSERVACION = "El alumno se comprometió a pagar en la segunda quincena.";

    @Mock
    private GrupoRepository grupoRepository;
    @Mock
    private InscripcionRepository inscripcionRepository;
    @Mock
    private NivelRepository nivelRepository;
    @Mock
    private AvanceNivelRepository avanceNivelRepository;
    @Mock
    private AutorizacionPagoRepository autorizacionRepository;
    @Mock
    private AjusteAvanceRepository ajusteAvanceRepository;
    @InjectMocks
    private GrupoService service;

    @Test
    void coordinacionCierraConObservacionYDejaPendienteElNivelDeQuienNoPago() {
        Grupo grupo = grupoAbierto();
        Inscripcion pagado = inscripcion(grupo, "22520001", 90, null);
        Inscripcion sinPago = inscripcion(grupo, "22520002", 85, 7);
        AvanceNivel avancePagado = new AvanceNivel("22520001", 0, EstatusAvance.CURSANDO);
        when(inscripcionRepository.findByGrupoId(4)).thenReturn(List.of(pagado, sinPago));
        when(autorizacionRepository.findByIdIn(anyList())).thenReturn(List.of(cargo(7, "Pendiente")));
        when(avanceNivelRepository.findByIdNumeroControlAndIdIdNivel("22520001", 0)).thenReturn(Optional.of(avancePagado));

        service.cerrarActaPorCoordinacion(4, OBSERVACION, coordinador());

        assertEquals(EstatusAvance.ACREDITADO, avancePagado.getEstatus());
        assertFalse(pagado.isAvancePendiente());
        assertTrue(sinPago.isAvancePendiente());
        verify(avanceNivelRepository, never()).findByIdNumeroControlAndIdIdNivel("22520002", 0);
        verify(grupo).setEstatusActa("Cerrada");
        verify(grupo).setCerradaPor("Coordinación");
        verify(grupo).setObservacionCierre(OBSERVACION);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AjusteAvance>> ajustes = ArgumentCaptor.forClass(List.class);
        verify(ajusteAvanceRepository).saveAll(ajustes.capture());
        assertEquals(1, ajustes.getValue().size());
        AjusteAvance ajuste = ajustes.getValue().getFirst();
        assertEquals("22520002", ajuste.getNumeroControl());
        assertEquals(AjusteAvance.CIERRE_SIN_PAGO, ajuste.getTipo());
        assertEquals("Pendiente", ajuste.getEstatusPago());
        assertEquals("coordinador", ajuste.getUsuario());
    }

    @Test
    void elCierreDeCoordinacionExigeUnaObservacion() {
        assertThrows(DatosInvalidosException.class,
                () -> service.cerrarActaPorCoordinacion(4, "  corta ", coordinador()));
        verify(grupoRepository, never()).save(any());
    }

    @Test
    void coordinacionAsignaDespuesElNivelPendienteYQuedaRegistrado() {
        Grupo grupo = mock(Grupo.class, RETURNS_DEEP_STUBS);
        when(grupo.getId()).thenReturn(4);
        when(grupoRepository.findById(4)).thenReturn(Optional.of(grupo));
        Inscripcion inscripcion = inscripcion(grupo, "22520002", 85, 7);
        inscripcion.setAvancePendiente(true);
        AvanceNivel avance = new AvanceNivel("22520002", 0, EstatusAvance.CURSANDO);
        when(inscripcionRepository.findByGrupoId(4)).thenReturn(List.of(inscripcion));
        when(autorizacionRepository.findByIdIn(anyList())).thenReturn(List.of(cargo(7, "Liquidada")));
        when(avanceNivelRepository.findByIdNumeroControlAndIdIdNivel("22520002", 0)).thenReturn(Optional.of(avance));

        service.asignarNivelPendiente(4, "22520002", OBSERVACION, coordinador());

        assertEquals(EstatusAvance.ACREDITADO, avance.getEstatus());
        assertFalse(inscripcion.isAvancePendiente());
        ArgumentCaptor<AjusteAvance> ajuste = ArgumentCaptor.forClass(AjusteAvance.class);
        verify(ajusteAvanceRepository).save(ajuste.capture());
        assertEquals(AjusteAvance.NIVEL_ASIGNADO, ajuste.getValue().getTipo());
        assertEquals("Acreditado", ajuste.getValue().getEstatusNuevo());
        assertEquals("Liquidada", ajuste.getValue().getEstatusPago());
    }

    @Test
    void noSeAsignaDosVecesElMismoNivel() {
        Grupo grupo = mock(Grupo.class, RETURNS_DEEP_STUBS);
        when(grupo.getId()).thenReturn(4);
        when(grupoRepository.findById(4)).thenReturn(Optional.of(grupo));
        Inscripcion yaAsignada = inscripcion(grupo, "22520002", 85, 7);
        when(inscripcionRepository.findByGrupoId(4)).thenReturn(List.of(yaAsignada));

        assertThrows(ReglaNegocioException.class,
                () -> service.asignarNivelPendiente(4, "22520002", OBSERVACION, coordinador()));
        verify(ajusteAvanceRepository, never()).save(any());
    }

    private Grupo grupoAbierto() {
        Grupo grupo = mock(Grupo.class, RETURNS_DEEP_STUBS);
        when(grupo.getId()).thenReturn(4);
        when(grupo.getEstatusActa()).thenReturn("En captura");
        when(grupoRepository.findById(4)).thenReturn(Optional.of(grupo));
        return grupo;
    }

    private static Inscripcion inscripcion(Grupo grupo, String numeroControl, int calificacion, Integer idCargo) {
        Alumno alumno = new Alumno();
        alumno.setNumeroControl(numeroControl);
        alumno.setNombre("Alumno");
        alumno.setApellidoPaterno(numeroControl);
        Inscripcion inscripcion = new Inscripcion();
        inscripcion.setAlumno(alumno);
        inscripcion.setGrupo(grupo);
        inscripcion.setCalificacionFinal(calificacion);
        inscripcion.setIdAutorizacion(idCargo);
        return inscripcion;
    }

    private static AutorizacionPago cargo(int id, String estatus) {
        AutorizacionPago cargo = new AutorizacionPago();
        cargo.setId(id);
        cargo.setEstatus(estatus);
        return cargo;
    }

    private static Authentication coordinador() {
        return new UsernamePasswordAuthenticationToken("coordinador", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_COORDINADOR")));
    }
}
