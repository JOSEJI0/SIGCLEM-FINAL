package mx.edu.itch.sigclem.lenguasmaternas.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.comun.excepcion.ConflictoException;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.lenguasmaternas.domain.LenguaMaterna;
import mx.edu.itch.sigclem.lenguasmaternas.domain.RegistroLenguaMaterna;
import mx.edu.itch.sigclem.lenguasmaternas.dto.EstatusLenguaRequest;
import mx.edu.itch.sigclem.lenguasmaternas.dto.LenguaMaternaDTO;
import mx.edu.itch.sigclem.lenguasmaternas.dto.LenguaMaternaRequest;
import mx.edu.itch.sigclem.lenguasmaternas.dto.RegistroLenguaDTO;
import mx.edu.itch.sigclem.lenguasmaternas.dto.RegistroLenguaRequest;
import mx.edu.itch.sigclem.lenguasmaternas.repository.LenguaMaternaRepository;
import mx.edu.itch.sigclem.lenguasmaternas.repository.RegistroLenguaMaternaRepository;
import mx.edu.itch.sigclem.seguridad.domain.RolSistema;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class LenguaMaternaService {

    private final LenguaMaternaRepository lenguaRepository;
    private final RegistroLenguaMaternaRepository registroRepository;
    private final AlumnoRepository alumnoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ArchivoLenguaMaternaService archivoService;

    @Transactional(readOnly = true)
    public List<LenguaMaternaDTO> listarLenguas() {
        return lenguaRepository.findAllByOrderByNombreAsc().stream().map(LenguaMaternaDTO::desde).toList();
    }

    @Transactional
    public LenguaMaternaDTO crearLengua(LenguaMaternaRequest solicitud) {
        String nombre = solicitud.nombre().trim();
        if (lenguaRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ConflictoException("LENG-004", "Esa lengua materna ya está registrada.");
        }
        LenguaMaterna lengua = new LenguaMaterna();
        lengua.setNombre(nombre);
        return LenguaMaternaDTO.desde(lenguaRepository.save(lengua));
    }

    @Transactional(readOnly = true)
    public List<RegistroLenguaDTO> listarPropios(String nombreUsuario) {
        Alumno alumno = alumnoDelUsuario(nombreUsuario);
        return registroRepository.findByAlumnoNumeroControlOrderByFechaRegistroDescIdDesc(
                        alumno.getNumeroControl()).stream()
                .map(RegistroLenguaDTO::desde).toList();
    }

    @Transactional
    public ResultadoRegistro registrar(
            RegistroLenguaRequest solicitud,
            MultipartFile documentoGeneral,
            MultipartFile curp,
            MultipartFile domicilio,
            MultipartFile ine,
            String nombreUsuario) {
        Alumno alumno = alumnoDelUsuario(nombreUsuario);
        if (!alumno.isHablaLenguaMaterna()) {
            throw new mx.edu.itch.sigclem.comun.excepcion.ReglaNegocioException("LENG-014",
                    "El apartado de lengua materna es solo para alumnos que indicaron hablar una en su registro.");
        }
        LenguaMaterna lengua = buscarLengua(solicitud.idLengua());
        RegistroLenguaMaterna existente = registroRepository.findByAlumnoNumeroControlAndLenguaId(
                alumno.getNumeroControl(), lengua.getId()).orElse(null);
        if (existente != null) {
            if (!"Rechazado".equals(existente.getEstatus())) {
                throw new DatosInvalidosException("LENG-005",
                        "Ya existe un registro para esta lengua; solo se puede actualizar si fue rechazado.");
            }
        }

        boolean hayCurp = hay(curp) || (existente != null && existente.getDocumentoCurp() != null);
        boolean hayDomicilio = hay(domicilio) || (existente != null && existente.getDocumentoDomicilio() != null);
        boolean hayIne = hay(ine) || (existente != null && existente.getDocumentoIne() != null);
        if (!(hayCurp && hayDomicilio && hayIne)) {
            throw new DatosInvalidosException("LENG-001",
            "Debes adjuntar tu CURP, tu comprobante de domicilio y tu INE.");
        }

        RegistroLenguaMaterna registro = existente == null ? new RegistroLenguaMaterna() : existente;
        registro.setAlumno(alumno);
        registro.setLengua(lengua);
        registro.setNivelDominio(limpiar(solicitud.nivelDominio()));
        registro.setEstatus("Pendiente");
        registro.setFechaRegistro(LocalDate.now());
        registro.setUsuarioValida(null);
        registro.setDocumentoCurp(guardarReemplazando(curp, registro.getDocumentoCurp()));
        registro.setDocumentoDomicilio(guardarReemplazando(domicilio, registro.getDocumentoDomicilio()));
        registro.setDocumentoIne(guardarReemplazando(ine, registro.getDocumentoIne()));
        
        if (curp != null && !curp.isEmpty()) {
            registro.setDocumentoCurp(archivoService.guardar(curp));
        }
        if (domicilio != null && !domicilio.isEmpty()) {
            registro.setDocumentoDomicilio(archivoService.guardar(domicilio));
        }
        if (ine != null && !ine.isEmpty()) {
            registro.setDocumentoIne(archivoService.guardar(ine));
        }
        if (documentoGeneral != null && !documentoGeneral.isEmpty()) {
            registro.setDocumento(archivoService.guardar(documentoGeneral));
        }
        
        RegistroLenguaDTO resultado = RegistroLenguaDTO.desde(registroRepository.save(registro));
        return new ResultadoRegistro(resultado, existente != null);
    }

    private static boolean hay(MultipartFile f) { return f != null && !f.isEmpty(); }
    /** Guarda el archivo nuevo y borra el anterior; si no hay archivo nuevo conserva el actual. */
    private String guardarReemplazando(MultipartFile nuevo, String actual) {
    if (!hay(nuevo)) return actual;
    String nombre = archivoService.guardar(nuevo);
    if (actual != null) archivoService.eliminar(actual);
    return nombre;
    }

    @Transactional
    public RegistroLenguaDTO subirDocumentosCoordinacion(
            Integer id, MultipartFile intercultural, MultipartFile constancia, String nombreUsuario) {
        RegistroLenguaMaterna registro = registroRepository.findConRelacionesById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("LENG-006", "No se encontró el registro."));
        Usuario coordinador = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("LENG-008", "No se encontró la cuenta autenticada."));
        if (coordinador.getRol().comoRolSistema() != RolSistema.COORDINADOR
                && coordinador.getRol().comoRolSistema() != RolSistema.ADMIN) {
            throw new DatosInvalidosException("LENG-009", "Solo coordinación puede subir documentos de acreditación.");
        }

        if (intercultural != null && !intercultural.isEmpty()) {
            registro.setDocumentoIntercultural(archivoService.guardar(intercultural));
        }
        if (constancia != null && !constancia.isEmpty()) {
            registro.setConstanciaAcreditacion(archivoService.guardar(constancia));
        }
        if (!hay(intercultural) && !hay(constancia)) {
    throw new DatosInvalidosException("LENG-015", "Adjunta el documento de la universidad intercultural o la constancia de acreditación.");
        } 
            registro.setDocumentoIntercultural(guardarReemplazando(intercultural, registro.getDocumentoIntercultural()));
            registro.setConstanciaAcreditacion(guardarReemplazando(constancia, registro.getConstanciaAcreditacion()));

        return RegistroLenguaDTO.desde(registroRepository.save(registro));
    }

    @Transactional(readOnly = true)
    public List<RegistroLenguaDTO> listarRegistros() {
        return registroRepository.findAllByOrderByFechaRegistroDescIdDesc().stream()
                .map(RegistroLenguaDTO::desde).toList();
    }

    @Transactional
    public RegistroLenguaDTO cambiarEstatus(Integer id, EstatusLenguaRequest solicitud, String nombreUsuario) {
        RegistroLenguaMaterna registro = registroRepository.findConRelacionesById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("LENG-006", "No se encontró el registro."));
        if (!"Pendiente".equals(registro.getEstatus())) {
            throw new DatosInvalidosException("LENG-007", "Solo se pueden revisar registros pendientes.");
        }
        Usuario coordinador = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("LENG-008", "No se encontró la cuenta autenticada."));
        if (coordinador.getRol().comoRolSistema() != RolSistema.COORDINADOR
                && coordinador.getRol().comoRolSistema() != RolSistema.ADMIN) {
            throw new DatosInvalidosException("LENG-009", "Solo coordinación puede revisar registros.");
        }
        registro.setEstatus(solicitud.estatus());
        registro.setUsuarioValida(coordinador);
        return RegistroLenguaDTO.desde(registro);
    }

    @Transactional(readOnly = true)
    public DocumentoLengua obtenerDocumento(Integer id, String tipo, String nombreUsuario, boolean coordinacion) {
        RegistroLenguaMaterna registro = registroRepository.findConRelacionesById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("LENG-006", "No se encontró el registro."));
        if (!coordinacion) {
            Alumno alumno = alumnoDelUsuario(nombreUsuario);
            if (!registro.getAlumno().getNumeroControl().equals(alumno.getNumeroControl())) {
                throw new RecursoNoEncontradoException("LENG-006", "No se encontró el documento.");
            }
        }
        String nombreArchivo = null;
        if (tipo != null) {
            switch (tipo.toLowerCase().trim()) {
                case "curp" -> nombreArchivo = registro.getDocumentoCurp();
                case "domicilio" -> nombreArchivo = registro.getDocumentoDomicilio();
                case "ine" -> nombreArchivo = registro.getDocumentoIne();
                case "intercultural" -> nombreArchivo = registro.getDocumentoIntercultural();
                case "constancia" -> nombreArchivo = registro.getConstanciaAcreditacion();
                case "general" -> nombreArchivo = registro.getDocumento();
            }
        }
        if (nombreArchivo == null) {
            if (registro.getDocumentoCurp() != null) nombreArchivo = registro.getDocumentoCurp();
            else if (registro.getDocumento() != null) nombreArchivo = registro.getDocumento();
            else if (registro.getDocumentoIne() != null) nombreArchivo = registro.getDocumentoIne();
            else if (registro.getDocumentoDomicilio() != null) nombreArchivo = registro.getDocumentoDomicilio();
            else if (registro.getDocumentoIntercultural() != null) nombreArchivo = registro.getDocumentoIntercultural();
            else if (registro.getConstanciaAcreditacion() != null) nombreArchivo = registro.getConstanciaAcreditacion();
        }
        if (nombreArchivo == null && tipo != null) {
            throw new RecursoNoEncontradoException("LENG-002", "Ese documento aún no se ha cargado.");
        }
        return new DocumentoLengua(archivoService.cargar(nombreArchivo),
                archivoService.tipoContenido(nombreArchivo));
    }

    @Transactional(readOnly = true)
    public DocumentoLengua obtenerDocumento(Integer id, String nombreUsuario, boolean coordinacion) {   
        return obtenerDocumento(id, null, nombreUsuario, coordinacion);
    }

    private Alumno alumnoDelUsuario(String nombreUsuario) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("LENG-008", "No se encontró la cuenta autenticada."));
        return alumnoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("LENG-010", "No se encontró el expediente del alumno."));
    }

    private LenguaMaterna buscarLengua(Integer id) {
        if (id == null) throw new DatosInvalidosException("LENG-011", "Selecciona una lengua materna.");
        return lenguaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("LENG-012", "No se encontró la lengua materna."));
    }

    private String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public record DocumentoLengua(Resource recurso, String tipoContenido) {
    }

    public record ResultadoRegistro(RegistroLenguaDTO registro, boolean actualizado) {
    }
}
