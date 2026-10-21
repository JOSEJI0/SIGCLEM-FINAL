package mx.edu.itch.sigclem.seguridad.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.seguridad.domain.Usuario;

public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    Optional<Usuario> findByNombreUsuario(String nombreUsuario);

    boolean existsByNombreUsuario(String nombreUsuario);

    boolean existsByRolNombre(String nombreRol);

    Optional<Usuario> findFirstByRolNombreAndActivoTrueOrderByIdAsc(String nombreRol);
}
