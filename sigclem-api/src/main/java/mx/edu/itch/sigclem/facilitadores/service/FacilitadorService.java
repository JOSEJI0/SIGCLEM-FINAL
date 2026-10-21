package mx.edu.itch.sigclem.facilitadores.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.comun.excepcion.ConflictoException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.facilitadores.domain.Facilitador;
import mx.edu.itch.sigclem.facilitadores.dto.FacilitadorCreadoDTO;
import mx.edu.itch.sigclem.facilitadores.dto.FacilitadorDTO;
import mx.edu.itch.sigclem.facilitadores.dto.FacilitadorRequest;
import mx.edu.itch.sigclem.facilitadores.repository.FacilitadorRepository;
import mx.edu.itch.sigclem.seguridad.domain.Rol;
import mx.edu.itch.sigclem.seguridad.domain.RolSistema;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.RolRepository;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class FacilitadorService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final FacilitadorRepository facilitadorRepository;
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public FacilitadorCreadoDTO registrar(FacilitadorRequest req) {
        String correo = req.correo().trim().toLowerCase(Locale.ROOT);
        if (facilitadorRepository.existsByCorreoIgnoreCase(correo)
                || usuarioRepository.existsByNombreUsuario(correo)) {
            throw new ConflictoException("FAC-001", "El correo ya está registrado en el sistema.");
        }

        Rol rol = rolRepository.findByNombre(RolSistema.FACILITADOR.nombreBd())
                .orElseThrow(() -> new IllegalStateException("Falta el rol Facilitador en el catálogo"));
        boolean asignada = req.contrasena() != null && !req.contrasena().isBlank();
        String contrasena = asignada ? req.contrasena() : generarContrasenaTemporal();
        Usuario usuario = usuarioRepository.save(
                new Usuario(correo, passwordEncoder.encode(contrasena), rol));

        Facilitador facilitador = new Facilitador();
        facilitador.setUsuario(usuario);
        aplicarDatos(facilitador, req, correo);
        Facilitador guardado = facilitadorRepository.save(facilitador);
        // Solo se devuelve la contraseña cuando la generó el sistema; la asignada ya la conoce coordinación.
        return new FacilitadorCreadoDTO(FacilitadorDTO.desde(guardado), correo, asignada ? null : contrasena);
    }

    @Transactional(readOnly = true)
    public List<FacilitadorDTO> listar() {
        return facilitadorRepository.findAllByOrderByApellidoPaternoAscApellidoMaternoAscNombreAsc()
                .stream().map(FacilitadorDTO::desde).toList();
    }

    @Transactional
    public FacilitadorDTO actualizar(Integer id, FacilitadorRequest req) {
        Facilitador facilitador = buscar(id);
        String correo = req.correo().trim().toLowerCase(Locale.ROOT);
        if (!correo.equalsIgnoreCase(facilitador.getCorreo())
                && (facilitadorRepository.existsByCorreoIgnoreCase(correo)
                        || usuarioRepository.existsByNombreUsuario(correo))) {
            throw new ConflictoException("FAC-001", "El correo ya está registrado en el sistema.");
        }
        facilitador.getUsuario().setNombreUsuario(correo);
        if (req.contrasena() != null && !req.contrasena().isBlank()) {
            facilitador.getUsuario().setContrasenaHash(passwordEncoder.encode(req.contrasena()));
        }
        aplicarDatos(facilitador, req, correo);
        return FacilitadorDTO.desde(facilitadorRepository.save(facilitador));
    }

    /** Coordinación asigna directamente una nueva contraseña al facilitador. */
    @Transactional
    public FacilitadorDTO cambiarContrasena(Integer id, String contrasena) {
        Facilitador facilitador = buscar(id);
        facilitador.getUsuario().setContrasenaHash(passwordEncoder.encode(contrasena));
        return FacilitadorDTO.desde(facilitadorRepository.save(facilitador));
    }

    /** Datos del facilitador autenticado. */
    @Transactional(readOnly = true)
    public FacilitadorDTO propio(String nombreUsuario) {
        return FacilitadorDTO.desde(propioEntidad(nombreUsuario));
    }

    /** El facilitador actualiza su teléfono; nombre y correo (su usuario de acceso) los administra coordinación. */
    @Transactional
    public FacilitadorDTO actualizarTelefono(String nombreUsuario, String telefono) {
        Facilitador facilitador = propioEntidad(nombreUsuario);
        facilitador.setTelefono(telefono == null || telefono.isBlank() ? null : telefono.trim());
        return FacilitadorDTO.desde(facilitadorRepository.save(facilitador));
    }

    public Facilitador propioEntidad(String nombreUsuario) {
        return facilitadorRepository.findByUsuarioId(usuarioRepository.findByNombreUsuario(nombreUsuario)
                        .orElseThrow(() -> new RecursoNoEncontradoException("FAC-003", "No se encontró la cuenta autenticada."))
                        .getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("FAC-003", "La cuenta no corresponde a un facilitador."));
    }

    @Transactional
    public FacilitadorDTO cambiarEstado(Integer id, boolean activo) {
        Facilitador facilitador = buscar(id);
        facilitador.getUsuario().setActivo(activo);
        return FacilitadorDTO.desde(facilitadorRepository.save(facilitador));
    }

    private Facilitador buscar(Integer id) {
        return facilitadorRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("FAC-002",
                        "No existe un facilitador con identificador " + id + "."));
    }

    private void aplicarDatos(Facilitador facilitador, FacilitadorRequest req, String correo) {
        facilitador.setNombre(req.nombre().trim());
        facilitador.setApellidoPaterno(req.apellidoPaterno().trim());
        facilitador.setApellidoMaterno(req.apellidoMaterno() == null || req.apellidoMaterno().isBlank()
                ? null : req.apellidoMaterno().trim());
        facilitador.setCorreo(correo);
        facilitador.setTelefono(req.telefono() == null || req.telefono().isBlank()
                ? null : req.telefono().trim());
    }

    private String generarContrasenaTemporal() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
