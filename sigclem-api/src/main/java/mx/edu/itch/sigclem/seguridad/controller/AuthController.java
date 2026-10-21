package mx.edu.itch.sigclem.seguridad.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.auditoria.domain.AccionAuditoria;
import mx.edu.itch.sigclem.auditoria.domain.RegistroAuditoria;
import mx.edu.itch.sigclem.auditoria.service.AuditoriaService;
import mx.edu.itch.sigclem.seguridad.dto.AuthResponse;
import mx.edu.itch.sigclem.seguridad.dto.LoginRequest;
import mx.edu.itch.sigclem.seguridad.dto.PerfilResponse;
import mx.edu.itch.sigclem.seguridad.service.IntentosLoginService;
import mx.edu.itch.sigclem.seguridad.service.JwtService;
import mx.edu.itch.sigclem.seguridad.service.PerfilService;
import mx.edu.itch.sigclem.seguridad.service.UsuarioAutenticado;

/** CU-01 Iniciar sesión. */
@Tag(name = "01. Autenticación", description = "CU-01 Iniciar sesión con JWT (opcional en modo pruebas).")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final PerfilService perfilService;
    private final IntentosLoginService intentos;
    private final AuditoriaService auditoria;

    @Operation(summary = "Iniciar sesión y obtener token JWT", description = "Rol: [Público]")
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        String usuario = req.usuario().trim();
        try {
            intentos.verificarNoBloqueado(usuario);
        } catch (RuntimeException ex) {
            auditoria.registrarDe(usuario, null, AccionAuditoria.CUENTA_BLOQUEADA, RegistroAuditoria.RECHAZADO,
                    "Intento de inicio de sesión con la cuenta bloqueada.");
            throw ex;
        }
        Authentication auth;
        try {
            auth = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(usuario, req.contrasena()));
        } catch (BadCredentialsException ex) {
            intentos.registrarFallo(usuario);
            auditoria.registrarDe(usuario, null, AccionAuditoria.INICIO_SESION_FALLIDO, RegistroAuditoria.RECHAZADO,
                    "Usuario o contraseña incorrectos.");
            throw ex;
        }
        intentos.registrarExito(usuario);
        UsuarioAutenticado u = (UsuarioAutenticado) auth.getPrincipal();
        auditoria.registrarDe(u.getUsername(), u.rol().name(), AccionAuditoria.INICIO_SESION, RegistroAuditoria.OK, null);
        return new AuthResponse(jwtService.generarToken(u), "Bearer", jwtService.getExpiracionSegundos(),
                perfilService.perfil(u));
    }

    @Operation(summary = "Perfil del usuario actual", description = "Rol: [Cualquier usuario]")
    @GetMapping("/perfil")
    public PerfilResponse perfil(@AuthenticationPrincipal UsuarioAutenticado usuario) {
        return perfilService.perfil(usuario);
    }
}
