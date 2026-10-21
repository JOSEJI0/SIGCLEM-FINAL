package mx.edu.itch.sigclem.boletas.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.boletas.dto.BoletaDTO;
import mx.edu.itch.sigclem.boletas.repository.BoletaRepository;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class BoletaService {

    private final UsuarioRepository usuarioRepository;
    private final AlumnoRepository alumnoRepository;
    private final BoletaRepository boletaRepository;

    @Transactional(readOnly = true)
    public List<BoletaDTO> obtenerBoletas(String nombreUsuario) {
        var usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("BOL-001",
                        "No se encontró la cuenta autenticada."));
        var alumno = alumnoRepository.findByUsuarioId(usuario.getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("BOL-002",
                        "No se encontró el expediente del alumno."));
        return boletaRepository.buscarPorNumeroControl(alumno.getNumeroControl()).stream()
                .map(BoletaDTO::desde)
                .toList();
    }
}
