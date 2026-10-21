package mx.edu.itch.sigclem.grupos.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.grupos.domain.Grupo;

public interface GrupoRepository extends JpaRepository<Grupo, Integer> {

    boolean existsByPeriodoIdAndNivelIdAndModalidadIdAndClave(
            Integer periodoId, Integer nivelId, Integer modalidadId, String clave);

    @EntityGraph(attributePaths = { "nivel", "periodo", "modalidad", "aula", "facilitador" })
    List<Grupo> findByPeriodoId(Integer periodoId);

    @EntityGraph(attributePaths = { "nivel", "periodo", "modalidad", "aula", "facilitador" })
    List<Grupo> findByPeriodoIdAndFacilitadorId(Integer periodoId, Integer facilitadorId);

    /** Oferta para el alumno: grupos de un nivel en periodos que aún no terminan. */
    @EntityGraph(attributePaths = { "nivel", "periodo", "modalidad", "aula", "facilitador" })
    List<Grupo> findByNivelIdAndPeriodoFechaFinGreaterThanEqual(Integer nivelId, java.time.LocalDate fecha);

    @EntityGraph(attributePaths = { "nivel", "periodo", "modalidad", "aula", "facilitador" })
    Optional<Grupo> findConDetallesById(Integer id);

    @EntityGraph(attributePaths = { "nivel", "periodo" })
    List<Grupo> findByFacilitadorId(Integer facilitadorId);

    boolean existsByPeriodoIdAndNivelIdAndModalidadIdAndClaveAndIdNot(
            Integer periodoId, Integer nivelId, Integer modalidadId, String clave, Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from Grupo g join fetch g.nivel join fetch g.periodo where g.id = :id")
    Optional<Grupo> bloquearPorId(@Param("id") Integer id);
}
