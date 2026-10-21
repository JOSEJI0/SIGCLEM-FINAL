package mx.edu.itch.sigclem.boletas.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.edu.itch.sigclem.grupos.domain.Inscripcion;

public interface BoletaRepository extends JpaRepository<Inscripcion, Integer> {

    @EntityGraph(attributePaths = {"grupo", "grupo.nivel", "grupo.periodo"})
    @Query("""
            select i from Inscripcion i
            where i.alumno.numeroControl = :numeroControl
            order by i.grupo.periodo.fechaInicio desc, i.grupo.nivel.numero asc, i.grupo.clave asc
            """)
    List<Inscripcion> buscarPorNumeroControl(@Param("numeroControl") String numeroControl);
}
