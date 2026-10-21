package mx.edu.itch.sigclem.catalogos.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.catalogos.domain.Periodo;

public interface PeriodoRepository extends JpaRepository<Periodo, Integer> {

    boolean existsByNombreIgnoreCase(String nombre);

    List<Periodo> findAllByOrderByFechaInicioDesc();

    Optional<Periodo> findFirstByFechaInicioLessThanEqualAndFechaFinGreaterThanEqualOrderByFechaInicioDesc(
            LocalDate fechaInicio, LocalDate fechaFin);
}
