package mx.edu.itch.sigclem.pagos.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import mx.edu.itch.sigclem.pagos.domain.TarifaPago;

public interface TarifaPagoRepository extends JpaRepository<TarifaPago, Integer> {

    @EntityGraph(attributePaths = "nivel")
    List<TarifaPago> findByActivaTrueOrderByConceptoAscIdAsc();

    @EntityGraph(attributePaths = "nivel")
    List<TarifaPago> findAllByOrderByConceptoAscIdAsc();

    @EntityGraph(attributePaths = "nivel")
    Optional<TarifaPago> findByIdAndActivaTrue(Integer id);

    List<TarifaPago> findAllByConceptoPagoIdAndActivaTrue(Integer idConcepto);

    boolean existsByConceptoAndNivelIdAndActivaTrue(String concepto, Integer nivelId);

    Optional<TarifaPago> findFirstByConceptoAndNivelIdAndActivaTrue(String concepto, Integer nivelId);

    boolean existsByConceptoPagoId(Integer idConcepto);

    /** Tarifa general (sin nivel) del concepto: la activa o, si no hay, la más reciente. */
    Optional<TarifaPago> findFirstByConceptoPagoIdAndNivelIsNullOrderByActivaDescIdDesc(Integer idConcepto);

    Optional<TarifaPago> findFirstByConceptoAndNivelIsNullAndActivaTrue(String concepto);

    List<TarifaPago> findAllByConceptoPagoId(Integer idConcepto);

    boolean existsByConceptoAndNivelIsNullAndActivaTrue(String concepto);
}
