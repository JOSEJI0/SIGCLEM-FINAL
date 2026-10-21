package mx.edu.itch.sigclem.diagnosticos.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.diagnosticos.domain.EvaluacionDiagnostica;

public interface EvaluacionDiagnosticaRepository extends JpaRepository<EvaluacionDiagnostica, Integer> {

    boolean existsByAlumnoNumeroControlAndEstatusIn(String numeroControl, List<String> estatus);

    @EntityGraph(attributePaths = "alumno")
    List<EvaluacionDiagnostica> findByAlumnoNumeroControlOrderByFechaSolicitudDescIdDesc(String numeroControl);

    @EntityGraph(attributePaths = "alumno")
    List<EvaluacionDiagnostica> findAllByOrderByFechaSolicitudDescIdDesc();

    @EntityGraph(attributePaths = "alumno")
    java.util.Optional<EvaluacionDiagnostica> findConAlumnoById(Integer id);
}
