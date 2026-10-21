package mx.edu.itch.sigclem.grupos.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.grupos.domain.AjusteAvance;

public interface AjusteAvanceRepository extends JpaRepository<AjusteAvance, Integer> {

    List<AjusteAvance> findByIdInscripcionInOrderByFechaAsc(List<Integer> idsInscripcion);
}
