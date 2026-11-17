package mx.edu.itch.sigclem.grupos.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import mx.edu.itch.sigclem.grupos.domain.Inscripcion;

public interface InscripcionRepository extends JpaRepository<Inscripcion, Integer> {

    @EntityGraph(attributePaths = { "alumno", "alumno.carrera", "grupo", "grupo.nivel", "grupo.nivelSecundario", "grupo.periodo" })
    @Query("select i from Inscripcion i")
    List<Inscripcion> findAllConDatosHistoricos();

    long countByGrupoId(Integer grupoId);

    @EntityGraph(attributePaths = { "alumno", "grupo", "grupo.nivel", "grupo.nivelSecundario" })
    List<Inscripcion> findByGrupoId(Integer grupoId);

    @EntityGraph(attributePaths = { "alumno", "grupo", "grupo.nivel", "grupo.nivelSecundario", "grupo.periodo" })
    List<Inscripcion> findByGrupo_Periodo_Id(Integer periodoId);

    boolean existsByAlumnoNumeroControlAndGrupoId(String numeroControl, Integer grupoId);

    @EntityGraph(attributePaths = { "grupo", "grupo.nivel", "grupo.nivelSecundario" })
    List<Inscripcion> findByAlumnoNumeroControl(String numeroControl);

    java.util.Optional<Inscripcion> findByAlumnoNumeroControlAndGrupoId(String numeroControl, Integer grupoId);

    java.util.Optional<Inscripcion> findByIdAutorizacion(Integer idAutorizacion);

    java.util.Optional<Inscripcion> findByIdAutorizacionSecundaria(Integer idAutorizacionSecundaria);

    @EntityGraph(attributePaths = { "grupo", "grupo.nivel", "grupo.nivelSecundario" })
    List<Inscripcion> findByIdAutorizacionIn(java.util.Collection<Integer> idsAutorizacion);

    @EntityGraph(attributePaths = { "grupo", "grupo.nivel", "grupo.nivelSecundario" })
    List<Inscripcion> findByIdAutorizacionSecundariaIn(java.util.Collection<Integer> idsAutorizacion);
}
