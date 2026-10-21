package mx.edu.itch.sigclem.pagos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.pagos.domain.ConceptoPago;

public interface ConceptoPagoRepository extends JpaRepository<ConceptoPago, Integer> {

    List<ConceptoPago> findByActivoTrueOrderByNombreAsc();

    List<ConceptoPago> findAllByOrderByNombreAsc();

    Optional<ConceptoPago> findByNombreAndActivoTrue(String nombre);

    Optional<ConceptoPago> findByIdAndActivoTrue(Integer id);

    boolean existsByNombreIgnoreCase(String nombre);
}
