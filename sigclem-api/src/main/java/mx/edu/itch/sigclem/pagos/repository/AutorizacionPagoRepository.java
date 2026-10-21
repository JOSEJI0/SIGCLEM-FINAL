package mx.edu.itch.sigclem.pagos.repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import mx.edu.itch.sigclem.pagos.domain.AutorizacionPago;

public interface AutorizacionPagoRepository extends JpaRepository<AutorizacionPago, Integer> {

    @EntityGraph(attributePaths = { "alumno", "nivel", "periodo" })
    List<AutorizacionPago> findByAlumnoNumeroControlOrderByFechaEmisionDescIdDesc(String numeroControl);

    @EntityGraph(attributePaths = { "alumno", "nivel", "periodo" })
    Optional<AutorizacionPago> findByIdAndAlumnoNumeroControl(Integer id, String numeroControl);

    @EntityGraph(attributePaths = { "alumno", "nivel", "periodo" })
    Optional<AutorizacionPago> findFirstByAlumnoNumeroControlAndConceptoAndNivelIdAndPeriodoIdAndEstatusInOrderByFechaEmisionDescIdDesc(
            String numeroControl, String concepto, Integer nivelId, Integer periodoId, Collection<String> estatus);

    @EntityGraph(attributePaths = { "alumno", "nivel", "periodo" })
    Optional<AutorizacionPago> findFirstByAlumnoNumeroControlAndConceptoAndNivelIsNullAndPeriodoIdAndEstatusInOrderByFechaEmisionDescIdDesc(
            String numeroControl, String concepto, Integer periodoId, Collection<String> estatus);

    boolean existsByConcepto(String concepto);

    @EntityGraph(attributePaths = { "alumno", "nivel", "periodo" })
    List<AutorizacionPago> findAllByOrderByFechaEmisionDescIdDesc();

    @EntityGraph(attributePaths = { "alumno", "nivel", "periodo" })
    List<AutorizacionPago> findByIdIn(Collection<Integer> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AutorizacionPago a where a.id = :id")
    @EntityGraph(attributePaths = { "alumno", "nivel", "periodo" })
    Optional<AutorizacionPago> bloquearPorId(@Param("id") Integer id);
}
