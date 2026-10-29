package mx.edu.itch.sigclem.alumnos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.edu.itch.sigclem.alumnos.domain.HistorialAcademico;

public interface HistorialAcademicoRepository extends JpaRepository<HistorialAcademico, Long> {

    // Usamos @Modifying para que haga un DELETE directo en la BD sin cargar los datos en memoria
    @Modifying
    @Query("DELETE FROM HistorialAcademico h WHERE h.alumno.numeroControl = :numeroControl")
    void deleteByAlumnoId(@Param("numeroControl") String numeroControl);
}