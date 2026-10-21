package mx.edu.itch.sigclem.constancias.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.comun.excepcion.ReglaNegocioException;
import mx.edu.itch.sigclem.constancias.dto.ConstanciaAvanceDTO;
import mx.edu.itch.sigclem.constancias.repository.ConstanciaAvanceRepository;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

/**
 * Constancia de avance por nivel de inglés: se emite por cada nivel acreditado (acta cerrada con calificación
 * de 70 o más), con el formato de la Coordinación de Lenguas Extranjeras.
 */
@Service
@RequiredArgsConstructor
public class ConstanciaAvanceService {

    static final int CALIFICACION_MINIMA = 70;

    private final ConstanciaAvanceRepository repository;
    private final AlumnoRepository alumnoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConstanciaAvancePdfService pdfService;

    @Transactional(readOnly = true)
    public List<ConstanciaAvanceDTO> listarPropias(String nombreUsuario) {
        return listar(numeroControlDe(nombreUsuario));
    }

    @Transactional(readOnly = true)
    public List<ConstanciaAvanceDTO> listar(String numeroControl) {
        String control = numeroControl.trim().toUpperCase(java.util.Locale.ROOT);
        if (!alumnoRepository.existsById(control)) {
            throw new RecursoNoEncontradoException("CAV-001", "No existe el expediente del alumno " + control + ".");
        }
        return repository.buscarAcreditadas(control, CALIFICACION_MINIMA).stream()
                .map(ConstanciaAvanceDTO::desde).toList();
    }

    @Transactional(readOnly = true)
    public byte[] generarPdf(Integer idInscripcion, String nombreUsuario, boolean personalAutorizado) {
        var inscripcion = repository.buscarConDetalle(idInscripcion)
                .orElseThrow(() -> new RecursoNoEncontradoException("CAV-002", "No existe la inscripción indicada."));
        if (!personalAutorizado
                && !inscripcion.getAlumno().getNumeroControl().equals(numeroControlDe(nombreUsuario))) {
            throw new ReglaNegocioException("CAV-003", "Solo puedes descargar las constancias de tu propio avance.");
        }
        if (!"Cerrada".equals(inscripcion.getGrupo().getEstatusActa())
                || inscripcion.getCalificacionFinal() == null
                || inscripcion.getCalificacionFinal() < CALIFICACION_MINIMA
                || inscripcion.isAvancePendiente()) {
            throw new ReglaNegocioException("CAV-004",
                    "La constancia de avance solo se emite para niveles acreditados con acta cerrada.");
        }
        try {
            return pdfService.generar(ConstanciaAvanceDTO.desde(inscripcion));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar la constancia de avance.", e);
        }
    }

    private String numeroControlDe(String nombreUsuario) {
        var usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("CAV-005", "No se encontró la cuenta autenticada."));
        return alumnoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("CAV-006", "No se encontró el expediente del alumno."))
                .getNumeroControl();
    }
}
