package mx.edu.itch.sigclem.grupos.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import mx.edu.itch.sigclem.grupos.domain.Inscripcion;

public interface InscripcionRepository extends JpaRepository<Inscripcion, Integer> {

    @EntityGraph(attributePaths = { "alumno", "alumno.carrera", "grupo", "grupo.nivel", "grupo.periodo" })
    @Query("select i from Inscripcion i")
    List<Inscripcion> findAllConDatosHistoricos();

    long countByGrupoId(Integer grupoId);

    @EntityGraph(attributePaths = { "alumno", "grupo" })
    List<Inscripcion> findByGrupoId(Integer grupoId);

    @EntityGraph(attributePaths = { "alumno", "grupo", "grupo.nivel", "grupo.periodo" })
    List<Inscripcion> findByGrupo_Periodo_Id(Integer periodoId);

    boolean existsByAlumnoNumeroControlAndGrupoId(String numeroControl, Integer grupoId);

    @EntityGraph(attributePaths = { "grupo", "grupo.nivel" })
    List<Inscripcion> findByAlumnoNumeroControl(String numeroControl);

    java.util.Optional<Inscripcion> findByAlumnoNumeroControlAndGrupoId(String numeroControl, Integer grupoId);

    java.util.Optional<Inscripcion> findByIdAutorizacion(Integer idAutorizacion);

    @EntityGraph(attributePaths = { "grupo", "grupo.nivel" })
    List<Inscripcion> findByIdAutorizacionIn(java.util.Collection<Integer> idsAutorizacion);
}
