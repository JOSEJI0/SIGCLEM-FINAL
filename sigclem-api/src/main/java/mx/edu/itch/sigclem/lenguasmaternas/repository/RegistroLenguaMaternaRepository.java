package mx.edu.itch.sigclem.lenguasmaternas.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.lenguasmaternas.domain.RegistroLenguaMaterna;

public interface RegistroLenguaMaternaRepository extends JpaRepository<RegistroLenguaMaterna, Integer> {

    @EntityGraph(attributePaths = {"alumno", "lengua", "usuarioValida"})
    List<RegistroLenguaMaterna> findByAlumnoNumeroControlOrderByFechaRegistroDescIdDesc(String numeroControl);

    @EntityGraph(attributePaths = {"alumno", "lengua", "usuarioValida"})
    List<RegistroLenguaMaterna> findAllByOrderByFechaRegistroDescIdDesc();

    @EntityGraph(attributePaths = {"alumno", "lengua", "usuarioValida"})
    Optional<RegistroLenguaMaterna> findConRelacionesById(Integer id);

    @EntityGraph(attributePaths = {"alumno", "lengua", "usuarioValida"})
    Optional<RegistroLenguaMaterna> findByAlumnoNumeroControlAndLenguaId(String numeroControl, Integer lenguaId);
}
