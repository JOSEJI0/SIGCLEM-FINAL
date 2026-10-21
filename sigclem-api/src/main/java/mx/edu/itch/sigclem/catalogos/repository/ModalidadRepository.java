package mx.edu.itch.sigclem.catalogos.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.catalogos.domain.Modalidad;

public interface ModalidadRepository extends JpaRepository<Modalidad, Integer> {

    List<Modalidad> findAllByOrderByNombreAsc();
}
