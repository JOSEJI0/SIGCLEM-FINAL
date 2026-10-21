package mx.edu.itch.sigclem.facilitadores.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.facilitadores.domain.Facilitador;

public interface FacilitadorRepository extends JpaRepository<Facilitador, Integer> {

    boolean existsByCorreoIgnoreCase(String correo);

    java.util.Optional<Facilitador> findByCorreoIgnoreCase(String correo);

    @EntityGraph(attributePaths = "usuario")
    Optional<Facilitador> findByUsuarioId(Integer idUsuario);

    @EntityGraph(attributePaths = "usuario")
    List<Facilitador> findAllByOrderByApellidoPaternoAscApellidoMaternoAscNombreAsc();
}
