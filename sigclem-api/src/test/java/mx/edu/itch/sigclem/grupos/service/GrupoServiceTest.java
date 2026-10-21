package mx.edu.itch.sigclem.grupos.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import mx.edu.itch.sigclem.comun.excepcion.ReglaNegocioException;
import mx.edu.itch.sigclem.catalogos.domain.Periodo;
import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.grupos.domain.Inscripcion;
import mx.edu.itch.sigclem.grupos.repository.GrupoRepository;
import mx.edu.itch.sigclem.grupos.repository.InscripcionRepository;

@ExtendWith(MockitoExtension.class)
class GrupoServiceTest {

    @Mock
    private GrupoRepository grupoRepository;
    @Mock
    private InscripcionRepository inscripcionRepository;
    @InjectMocks
    private GrupoService service;

    @Test
    void cerrarActaRechazaUnGrupoSinAlumnos() {
        Grupo grupo = mock(Grupo.class);
        when(grupo.getId()).thenReturn(4);
        when(grupo.getEstatusActa()).thenReturn("Abierta");
        when(grupo.getPeriodo()).thenReturn(periodoVigente());
        when(grupoRepository.findById(4)).thenReturn(Optional.of(grupo));
        when(inscripcionRepository.findByGrupoId(4)).thenReturn(List.of());

        assertThrows(ReglaNegocioException.class, () -> service.cerrarActa(4, coordinador()));

        verify(grupoRepository, never()).save(grupo);
    }

    @Test
    void cerrarActaRechazaSiHayAlumnosSinCalificacion() {
        Grupo grupo = mock(Grupo.class);
        Inscripcion sinCalificacion = mock(Inscripcion.class);
        when(grupo.getId()).thenReturn(4);
        when(grupo.getEstatusActa()).thenReturn("En captura");
        when(grupo.getPeriodo()).thenReturn(periodoVigente());
        when(sinCalificacion.getCalificacionFinal()).thenReturn(null);
        when(grupoRepository.findById(4)).thenReturn(Optional.of(grupo));
        when(inscripcionRepository.findByGrupoId(4)).thenReturn(List.of(sinCalificacion));

        assertThrows(ReglaNegocioException.class, () -> service.cerrarActa(4, coordinador()));

        verify(grupoRepository, never()).save(grupo);
    }

    @Test
    void actaCerradaNoSePuedeVolverAModificar() {
        Grupo grupo = mock(Grupo.class);
        when(grupo.getEstatusActa()).thenReturn("Cerrada");
        when(grupo.getPeriodo()).thenReturn(periodoVigente());
        when(grupoRepository.findById(4)).thenReturn(Optional.of(grupo));

        assertThrows(ReglaNegocioException.class, () -> service.cerrarActa(4, coordinador()));

        verify(inscripcionRepository, never()).findByGrupoId(4);
    }

    @Test
    void cerrarActaRechazaLaEdicionDeUnPeriodoAnterior() {
        Grupo grupo = mock(Grupo.class);
        Periodo anterior = new Periodo();
        anterior.setFechaInicio(LocalDate.now().minusMonths(2));
        anterior.setFechaFin(LocalDate.now().minusMonths(1));
        when(grupo.getPeriodo()).thenReturn(anterior);
        when(grupoRepository.findById(4)).thenReturn(Optional.of(grupo));

        assertThrows(ReglaNegocioException.class, () -> service.cerrarActa(4, coordinador()));

        verify(inscripcionRepository, never()).findByGrupoId(4);
        verify(grupoRepository, never()).save(grupo);
    }

    private Periodo periodoVigente() {
        Periodo periodo = new Periodo();
        periodo.setFechaInicio(LocalDate.now().minusDays(1));
        periodo.setFechaFin(LocalDate.now().plusDays(1));
        return periodo;
    }

    private Authentication coordinador() {
        return new UsernamePasswordAuthenticationToken("coordinador", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_COORDINADOR")));
    }
}
