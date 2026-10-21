package mx.edu.itch.sigclem.seguridad.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.edu.itch.sigclem.seguridad.domain.Rol;
import mx.edu.itch.sigclem.seguridad.domain.RolSistema;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.RolRepository;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

/**
 * Crea las cuentas iniciales del coordinador y de la jefa del DGTV a partir de variables de entorno,
 * solo si todavía no existe una cuenta con ese rol. Así no se guardan contraseñas en el código.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatosIniciales implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.inicial.coordinador.usuario:}")
    private String coordinadorUsuario;
    @Value("${app.inicial.coordinador.contrasena:}")
    private String coordinadorContrasena;
    @Value("${app.inicial.jefa.usuario:}")
    private String jefaUsuario;
    @Value("${app.inicial.jefa.contrasena:}")
    private String jefaContrasena;
    @Value("${app.inicial.admin.usuario:}")
    private String adminUsuario;
    @Value("${app.inicial.admin.contrasena:}")
    private String adminContrasena;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        crearSiNoExiste(RolSistema.COORDINADOR, coordinadorUsuario, coordinadorContrasena);
        crearSiNoExiste(RolSistema.JEFA_DGTV, jefaUsuario, jefaContrasena);
        // Opcional: cuenta de administrador (mayor privilegio). Solo se crea si se definen sus variables.
        crearSiNoExiste(RolSistema.ADMIN, adminUsuario, adminContrasena);
    }

    private void crearSiNoExiste(RolSistema rolSistema, String usuario, String contrasena) {
        if (usuarioRepository.existsByRolNombre(rolSistema.nombreBd())) {
            return;
        }
        if (usuario == null || usuario.isBlank() || contrasena == null || contrasena.length() < 8) {
            log.warn("No existe una cuenta con rol {}. Define sus variables de entorno para crearla.", rolSistema);
            return;
        }
        Rol rol = rolRepository.findByNombre(rolSistema.nombreBd())
                .orElseThrow(() -> new IllegalStateException("Falta el rol " + rolSistema.nombreBd()));
        usuarioRepository.save(new Usuario(usuario.trim(), passwordEncoder.encode(contrasena), rol));
        log.info("Cuenta inicial creada para el rol {}: {}", rolSistema, usuario);
    }
}
