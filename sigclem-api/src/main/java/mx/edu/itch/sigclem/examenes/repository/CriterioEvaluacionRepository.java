package mx.edu.itch.sigclem.examenes.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.examenes.domain.CriterioEvaluacion;

public interface CriterioEvaluacionRepository extends JpaRepository<CriterioEvaluacion, Integer> {

    List<CriterioEvaluacion> findAllByOrderByIdAsc();
}
