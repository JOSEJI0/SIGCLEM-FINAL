package mx.edu.itch.sigclem.seguridad.service;

import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.seguridad.domain.RolSistema;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

/**
 * Contraseñas administradas por coordinación: el coordinador cambia la suya y asigna una nueva a los alumnos y a la
 * jefa del DGTV (las de los facilitadores se asignan en su módulo). Nadie más cambia contraseñas desde el sistema.
 */
@Service
@RequiredArgsConstructor
public class CuentaService {

    private final UsuarioRepository usuarioRepository;
    private final AlumnoRepository alumnoRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void cambiarPropia(String nombreUsuario, String actual, String nueva) {
        Usuario usuario = usuarioRepository.findByNombreUsuario(nombreUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("CTA-001", "No se encontró la cuenta autenticada."));
        if (!passwordEncoder.matches(actual, usuario.getContrasenaHash())) {
            throw new DatosInvalidosException("CTA-002", "La contraseña actual no es correcta.");
        }
        if (actual.equals(nueva)) {
            throw new DatosInvalidosException("CTA-003", "La nueva contraseña debe ser distinta de la actual.");
        }
        asignar(usuario, nueva);
    }

    @Transactional
    public void asignarAlumno(String numeroControl, String nueva) {
        String control = numeroControl.trim().toUpperCase(Locale.ROOT);
        var alumno = alumnoRepository.findById(control)
                .orElseThrow(() -> new RecursoNoEncontradoException("CTA-005", "No existe el alumno " + control + "."));
        asignar(alumno.getUsuario(), nueva);
    }

    @Transactional
    public void asignarJefa(String nueva) {
        Usuario jefa = usuarioRepository.findFirstByRolNombreAndActivoTrueOrderByIdAsc(RolSistema.JEFA_DGTV.nombreBd())
                .orElseThrow(() -> new RecursoNoEncontradoException("CTA-006", "No existe una cuenta activa de la jefa del DGTV."));
        asignar(jefa, nueva);
    }

    private void asignar(Usuario usuario, String nueva) {
        validar(nueva);
        usuario.setContrasenaHash(passwordEncoder.encode(nueva));
        usuarioRepository.save(usuario);
    }

    static void validar(String contrasena) {
        if (contrasena == null || contrasena.length() < 8 || contrasena.length() > 72) {
            throw new DatosInvalidosException("CTA-004", "La contraseña debe tener entre 8 y 72 caracteres.");
        }
        if (!contrasena.matches(".*[A-Za-zÁÉÍÓÚÑáéíóúñ].*") || !contrasena.matches(".*\\d.*")) {
            throw new DatosInvalidosException("CTA-004", "La contraseña debe incluir al menos una letra y un número.");
        }
    }
}
