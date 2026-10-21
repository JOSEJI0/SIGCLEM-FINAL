package mx.edu.itch.sigclem.grupos.repository;

import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.edu.itch.sigclem.grupos.domain.Horario;

public interface HorarioRepository extends JpaRepository<Horario, Integer> {

    @Query("""
            select h from Horario h
            where h.diaSemana = :dia
              and h.horaInicio < :fin and h.horaFin > :inicio
              and h.grupo.periodo.id = :periodoId
              and (h.grupo.facilitador.id = :facilitadorId or h.grupo.aula.id = :aulaId)
              and h.grupo.id <> :grupoExcluido
            """)
    // Devuelve una lista: con Optional, dos o más traslapes simultáneos lanzaban
    // IncorrectResultSizeDataAccessException (error 500) en lugar del mensaje GRU-011.
    List<Horario> buscarTraslape(
            @Param("dia") Integer dia,
            @Param("inicio") LocalTime inicio,
            @Param("fin") LocalTime fin,
            @Param("periodoId") Integer periodoId,
            @Param("facilitadorId") Integer facilitadorId,
            @Param("aulaId") Integer aulaId,
            @Param("grupoExcluido") Integer grupoExcluido);
}
