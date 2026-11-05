package mx.edu.itch.sigclem.alumnos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.edu.itch.sigclem.alumnos.domain.HistorialAcademico;

public interface HistorialAcademicoRepository extends JpaRepository<HistorialAcademico, Long> {

    @EntityGraph(attributePaths = { "alumno", "alumno.carrera" })
    @Query("select h from HistorialAcademico h")
    java.util.List<HistorialAcademico> findAllConAlumno();

    @Query("""
            select h from HistorialAcademico h
            where h.alumno.numeroControl = :numeroControl
              and h.nivel = :nivel
              and h.periodo = :periodo
              and coalesce(h.grupo, '') = :grupo
            order by h.id
            """)
    java.util.List<HistorialAcademico> buscarPorClaveHistorica(
            @Param("numeroControl") String numeroControl,
            @Param("nivel") String nivel,
            @Param("periodo") String periodo,
            @Param("grupo") String grupo);

    // Usamos @Modifying para que haga un DELETE directo en la BD sin cargar los datos en memoria
    @Modifying
    @Query("DELETE FROM HistorialAcademico h WHERE h.alumno.numeroControl = :numeroControl")
    void deleteByAlumnoId(@Param("numeroControl") String numeroControl);
}