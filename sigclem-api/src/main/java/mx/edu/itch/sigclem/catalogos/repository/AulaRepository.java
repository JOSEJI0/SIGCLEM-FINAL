package mx.edu.itch.sigclem.catalogos.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.catalogos.domain.Aula;

public interface AulaRepository extends JpaRepository<Aula, Integer> {

    boolean existsByNombreIgnoreCase(String nombre);

    List<Aula> findAllByOrderByNombreAsc();
}
