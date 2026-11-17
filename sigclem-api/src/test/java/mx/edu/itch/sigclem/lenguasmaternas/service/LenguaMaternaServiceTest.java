package mx.edu.itch.sigclem.lenguasmaternas.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.service.AlumnoService;
import mx.edu.itch.sigclem.catalogos.domain.Carrera;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.constancias.domain.FolioConstanciaUsado;
import mx.edu.itch.sigclem.constancias.dto.MembreteConstancia;
import mx.edu.itch.sigclem.constancias.repository.FolioConstanciaUsadoRepository;
import mx.edu.itch.sigclem.constancias.service.ArchivoConstanciaService;
import mx.edu.itch.sigclem.constancias.service.ConfiguracionConstanciaService;
import mx.edu.itch.sigclem.constancias.service.ConstanciaWordService;
import mx.edu.itch.sigclem.seguridad.domain.Rol;
import mx.edu.itch.sigclem.seguridad.domain.RolSistema;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.lenguasmaternas.domain.RegistroLenguaMaterna;
import mx.edu.itch.sigclem.lenguasmaternas.dto.EstatusLenguaRequest;
import mx.edu.itch.sigclem.lenguasmaternas.dto.RegistroLenguaDTO;
import mx.edu.itch.sigclem.lenguasmaternas.repository.LenguaMaternaRepository;
import mx.edu.itch.sigclem.lenguasmaternas.repository.RegistroLenguaMaternaRepository;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@ExtendWith(MockitoExtension.class)
class LenguaMaternaServiceTest {

    @Mock
    private LenguaMaternaRepository lenguaRepository;
    @Mock
    private RegistroLenguaMaternaRepository registroRepository;
    @Mock
    private AlumnoRepository alumnoRepository;
    @Mock
    private AlumnoService alumnoService;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ArchivoLenguaMaternaService archivoService;
    @Mock
    private ArchivoConstanciaService archivoConstanciaService;
    @Mock
    private ConstanciaWordService constanciaWordService;
    @Mock
    private ConfiguracionConstanciaService configuracionConstanciaService;
    @Mock
    private FolioConstanciaUsadoRepository folioRepository;
    @InjectMocks
    private LenguaMaternaService service;

    @Test
    void noPermiteValidarSinConstanciaYDocumentoIntercultural() {
        when(registroRepository.findConRelacionesById(7)).thenReturn(Optional.of(new RegistroLenguaMaterna()));

        assertThrows(DatosInvalidosException.class,
                () -> service.cambiarEstatus(7, new EstatusLenguaRequest("Validado", null, null, null), "coordinador"));
    }

    @Test
    void noPermiteRechazarSinDocumentoObservadoOMotivo() {
        RegistroLenguaMaterna registro = new RegistroLenguaMaterna();
        registro.setEstatus("Pendiente");
        when(registroRepository.findConRelacionesById(7)).thenReturn(Optional.of(registro));

        assertThrows(DatosInvalidosException.class,
                () -> service.cambiarEstatus(7, new EstatusLenguaRequest("Rechazado", "curp", " ", null), "coordinador"));
    }

    @Test
    void generaYAsociaConstanciaWordAlValidarConFolio() {
        RegistroLenguaMaterna registro = registroPendienteConEvidencia();
        Usuario coordinador = new Usuario();
        coordinador.setNombreUsuario("coordinador");
        Rol rol = mock(Rol.class);
        when(rol.comoRolSistema()).thenReturn(RolSistema.COORDINADOR);
        coordinador.setRol(rol);
        when(registroRepository.findConRelacionesById(7)).thenReturn(Optional.of(registro));
        when(usuarioRepository.findByNombreUsuario("coordinador")).thenReturn(Optional.of(coordinador));
        when(folioRepository.existsById("LM2026A01")).thenReturn(false);
        when(configuracionConstanciaService.membreteVigente()).thenReturn(MembreteConstancia.porOmision());
        when(constanciaWordService.generarLenguaMaterna(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("LM2026A01"), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(new byte[] { 1, 2, 3 });
        when(archivoConstanciaService.guardarWord("lengua-materna", 7, new byte[] { 1, 2, 3 }))
                .thenReturn("constancias/lengua-materna-7.docx");
        when(registroRepository.save(registro)).thenReturn(registro);

        RegistroLenguaDTO resultado = service.cambiarEstatus(
                7, new EstatusLenguaRequest("Validado", null, null, "LM2026A01"), "coordinador");

        assertEquals("Validado", resultado.estatus());
        assertEquals("LM2026A01", resultado.folioConstancia());
        assertEquals("constancias/lengua-materna-7.docx", registro.getConstanciaWord());
        verify(folioRepository).saveAndFlush(org.mockito.ArgumentMatchers.any(FolioConstanciaUsado.class));
        verify(alumnoService).liberarIdiomaPorLenguaMaterna("22520001");
    }

    private RegistroLenguaMaterna registroPendienteConEvidencia() {
        RegistroLenguaMaterna registro = new RegistroLenguaMaterna();
        registro.setId(7);
        registro.setEstatus("Pendiente");
        registro.setDocumentoIntercultural("evidencia.pdf");
        Alumno alumno = new Alumno();
        alumno.setNumeroControl("22520001");
        alumno.setNombre("Ana");
        alumno.setApellidoPaterno("López");
        Carrera carrera = mock(Carrera.class);
        when(carrera.getNombre()).thenReturn("Ingeniería Civil");
        when(carrera.getClavePlan()).thenReturn("IC-2020");
        alumno.setCarrera(carrera);
        registro.setAlumno(alumno);
        mx.edu.itch.sigclem.lenguasmaternas.domain.LenguaMaterna lengua =
                new mx.edu.itch.sigclem.lenguasmaternas.domain.LenguaMaterna();
        lengua.setId(3);
        lengua.setNombre("Me'phaa");
        registro.setLengua(lengua);
        return registro;
    }
}
