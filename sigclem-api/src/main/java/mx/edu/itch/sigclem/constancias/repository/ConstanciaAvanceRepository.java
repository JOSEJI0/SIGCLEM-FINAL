package mx.edu.itch.sigclem.constancias.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.edu.itch.sigclem.grupos.domain.Inscripcion;

/** Inscripciones con acta cerrada y calificación aprobatoria: base de la constancia de avance por nivel. */
public interface ConstanciaAvanceRepository extends JpaRepository<Inscripcion, Integer> {

    @EntityGraph(attributePaths = { "alumno", "alumno.carrera", "grupo", "grupo.nivel", "grupo.periodo",
            "grupo.facilitador" })
    @Query("""
            select i from Inscripcion i
            where i.alumno.numeroControl = :numeroControl
              and i.grupo.estatusActa = 'Cerrada'
              and i.calificacionFinal >= :minima
              and i.avancePendiente = false
            order by i.grupo.nivel.numero asc, i.grupo.periodo.fechaInicio asc
            """)
    List<Inscripcion> buscarAcreditadas(@Param("numeroControl") String numeroControl, @Param("minima") int minima);

    @EntityGraph(attributePaths = { "alumno", "alumno.carrera", "grupo", "grupo.nivel", "grupo.periodo",
            "grupo.facilitador" })
    @Query("select i from Inscripcion i where i.id = :id")
    Optional<Inscripcion> buscarConDetalle(@Param("id") Integer id);
}
