package mx.edu.itch.sigclem.constancias.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.edu.itch.sigclem.constancias.domain.Constancia;

public interface ConstanciaRepository extends JpaRepository<Constancia, Integer> {

    boolean existsByAlumnoNumeroControlAndIdiomaIgnoreCaseAndEstatusIn(
            String numeroControl, String idioma, List<String> estatus);

    boolean existsByFolioIgnoreCase(String folio);

    @EntityGraph(attributePaths = {"alumno", "alumno.carrera", "usuarioAutoriza"})
    List<Constancia> findAllByOrderByFechaSolicitudDescIdDesc();

    @EntityGraph(attributePaths = {"alumno", "alumno.carrera", "usuarioAutoriza"})
    List<Constancia> findAllByEstatusOrderByFechaSolicitudDescIdDesc(String estatus);

    @EntityGraph(attributePaths = {"alumno", "alumno.carrera", "usuarioAutoriza"})
    List<Constancia> findByAlumnoNumeroControlOrderByFechaSolicitudDescIdDesc(String numeroControl);

    @EntityGraph(attributePaths = {"alumno", "alumno.carrera", "usuarioAutoriza"})
    Optional<Constancia> findConAlumnoById(Integer id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from Constancia c
            join fetch c.alumno a
            join fetch a.carrera
            where c.id = :id
            """)
    Optional<Constancia> buscarParaActualizar(@Param("id") Integer id);
}
