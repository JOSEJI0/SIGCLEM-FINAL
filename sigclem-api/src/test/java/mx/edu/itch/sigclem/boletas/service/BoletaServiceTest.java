package mx.edu.itch.sigclem.boletas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.boletas.repository.BoletaRepository;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.domain.Periodo;
import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.grupos.domain.Inscripcion;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class BoletaServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private AlumnoRepository alumnoRepository;
    @Mock
    private BoletaRepository boletaRepository;
    @InjectMocks
    private BoletaService service;

    @Test
    void soloPublicaCalificacionUnaVezCerradaElActa() {
        Usuario usuario = new Usuario();
        usuario.setId(12);
        Alumno alumno = new Alumno();
        alumno.setNumeroControl("A001234567");
        Nivel nivel = mock(Nivel.class);
        when(nivel.getNumero()).thenReturn(2);
        when(nivel.getNombre()).thenReturn("Básico 2");
        Periodo periodo = new Periodo();
        periodo.setNombre("Enero-Junio");
        periodo.setFechaInicio(LocalDate.of(2026, 1, 1));
        periodo.setFechaFin(LocalDate.of(2026, 6, 30));

        Grupo grupo = new Grupo();
        grupo.setClave("A");
        grupo.setNivel(nivel);
        grupo.setPeriodo(periodo);
        grupo.setEstatusActa("En captura");
        Inscripcion inscripcion = new Inscripcion();
        inscripcion.setGrupo(grupo);
        inscripcion.setOportunidad("01");
        inscripcion.setCalificacionFinal(85);

        when(usuarioRepository.findByNombreUsuario("A001234567")).thenReturn(Optional.of(usuario));
        when(alumnoRepository.findByUsuarioId(12)).thenReturn(Optional.of(alumno));
        when(boletaRepository.buscarPorNumeroControl("A001234567")).thenReturn(List.of(inscripcion));

        var boleta = service.obtenerBoletas("A001234567").getFirst();

        assertThat(boleta.periodo()).isEqualTo("Enero-Junio");
        assertThat(boleta.calificacion()).isNull();
        assertThat(boleta.resultado()).isEqualTo("Pendiente de cierre");
    }

    @Test
    void clasificaCalificacionDeSetentaComoAcreditada() {
        var boleta = boletaCerradaConCalificacion(70);

        assertThat(boleta.calificacion()).isEqualTo(70);
        assertThat(boleta.resultado()).isEqualTo("Acreditado");
    }

    @Test
    void clasificaCalificacionMenorASetentaComoNoAcreditada() {
        var boleta = boletaCerradaConCalificacion(69);

        assertThat(boleta.calificacion()).isEqualTo(69);
        assertThat(boleta.resultado()).isEqualTo("No acreditado");
        assertThat(boleta.fechaCierreActa()).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    private mx.edu.itch.sigclem.boletas.dto.BoletaDTO boletaCerradaConCalificacion(Integer calificacion) {
        Inscripcion inscripcion = new Inscripcion();
        Grupo grupo = new Grupo();
        grupo.setNivel(nivel(1));
        grupo.setPeriodo(new Periodo());
        grupo.setEstatusActa("Cerrada");
        grupo.setFechaCierreActa(LocalDate.of(2026, 6, 30));
        inscripcion.setGrupo(grupo);
        inscripcion.setCalificacionFinal(calificacion);
        return mx.edu.itch.sigclem.boletas.dto.BoletaDTO.desde(inscripcion);
    }

    private Nivel nivel(Integer numero) {
        Nivel nivel = mock(Nivel.class);
        when(nivel.getNumero()).thenReturn(numero);
        when(nivel.getNombre()).thenReturn("Básico " + numero);
        return nivel;
    }
}
