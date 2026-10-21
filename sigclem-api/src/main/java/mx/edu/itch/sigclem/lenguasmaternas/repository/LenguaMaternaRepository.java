package mx.edu.itch.sigclem.lenguasmaternas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.lenguasmaternas.domain.LenguaMaterna;

public interface LenguaMaternaRepository extends JpaRepository<LenguaMaterna, Integer> {

    List<LenguaMaterna> findAllByOrderByNombreAsc();

    boolean existsByNombreIgnoreCase(String nombre);
}
