package mx.edu.itch.sigclem.seguridad.service;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import mx.edu.itch.sigclem.seguridad.domain.RolSistema;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;

/** Identidad del usuario dentro del contexto de seguridad. */
public record UsuarioAutenticado(Integer id, String nombreUsuario, String contrasenaHash, RolSistema rol,
        boolean activo) implements UserDetails {

    public static UsuarioAutenticado desde(Usuario u) {
        return new UsuarioAutenticado(u.getId(), u.getNombreUsuario(), u.getContrasenaHash(),
                u.getRol().comoRolSistema(), u.isActivo());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(rol.autoridad()));
    }

    @Override
    public String getPassword() {
        return contrasenaHash;
    }

    @Override
    public String getUsername() {
        return nombreUsuario;
    }

    @Override
    public boolean isEnabled() {
        return activo;
    }
}
