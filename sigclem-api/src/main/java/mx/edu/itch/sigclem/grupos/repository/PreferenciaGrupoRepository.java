package mx.edu.itch.sigclem.grupos.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.grupos.domain.PreferenciaGrupo;

public interface PreferenciaGrupoRepository extends JpaRepository<PreferenciaGrupo, Integer> {

    Optional<PreferenciaGrupo> findByAlumnoNumeroControlAndPeriodoId(String numeroControl, Integer periodoId);

    @EntityGraph(attributePaths = { "grupo", "grupo.nivel", "periodo" })
    List<PreferenciaGrupo> findByAlumnoNumeroControlAndPeriodoIdIn(String numeroControl, Collection<Integer> periodos);

    @EntityGraph(attributePaths = { "alumno", "alumno.carrera" })
    List<PreferenciaGrupo> findByGrupoIdAndEstatusOrderByFechaRegistroAsc(Integer grupoId, String estatus);

    long countByGrupoIdAndEstatus(Integer grupoId, String estatus);
}
