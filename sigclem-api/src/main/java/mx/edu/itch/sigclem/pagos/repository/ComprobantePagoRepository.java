package mx.edu.itch.sigclem.pagos.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import mx.edu.itch.sigclem.pagos.domain.ComprobantePago;

public interface ComprobantePagoRepository extends JpaRepository<ComprobantePago, Integer> {

    boolean existsByAutorizacionIdAndEstatusIn(Integer autorizacionId, Collection<String> estatus);

    boolean existsByAutorizacionIdAndEstatus(Integer autorizacionId, String estatus);

    boolean existsByAutorizacionId(Integer autorizacionId);

    boolean existsByUuidCfdiIgnoreCase(String uuidCfdi);

    /** Por cargo: [idAutorizacion, comprobantes, comprobantes pendientes], en una sola consulta. */
    @Query("""
            select c.autorizacion.id, count(c), sum(case when c.estatus = 'Pendiente' then 1 else 0 end)
            from ComprobantePago c group by c.autorizacion.id
            """)
    List<Object[]> contarPorAutorizacion();

    @EntityGraph(attributePaths = { "autorizacion", "autorizacion.alumno", "autorizacion.nivel",
            "autorizacion.periodo" })
    List<ComprobantePago> findByEstatusOrderByFechaCargaAsc(String estatus);

    @EntityGraph(attributePaths = { "autorizacion", "autorizacion.alumno", "autorizacion.nivel",
            "autorizacion.periodo" })
    Optional<ComprobantePago> findConRelacionesById(Integer id);

    @EntityGraph(attributePaths = { "autorizacion", "autorizacion.alumno", "autorizacion.nivel",
            "autorizacion.periodo" })
    Optional<ComprobantePago> findFirstByAutorizacionIdOrderByFechaCargaDescIdDesc(Integer autorizacionId);

    @EntityGraph(attributePaths = { "autorizacion", "autorizacion.alumno", "autorizacion.nivel",
            "autorizacion.periodo" })
    List<ComprobantePago> findByAutorizacionIdOrderByFechaCargaAscIdAsc(Integer autorizacionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ComprobantePago c where c.id = :id")
    @EntityGraph(attributePaths = { "autorizacion", "autorizacion.alumno", "autorizacion.nivel",
            "autorizacion.periodo" })
    Optional<ComprobantePago> bloquearPorId(@Param("id") Integer id);
}
