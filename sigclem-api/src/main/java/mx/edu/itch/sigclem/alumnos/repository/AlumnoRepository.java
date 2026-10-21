package mx.edu.itch.sigclem.alumnos.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;

import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import jakarta.persistence.LockModeType;

public interface AlumnoRepository extends JpaRepository<Alumno, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Alumno a where a.numeroControl = :numeroControl")
    Optional<Alumno> bloquearPorNumeroControl(@Param("numeroControl") String numeroControl);

    Optional<Alumno> findByUsuarioId(Integer idUsuario);

    @EntityGraph(attributePaths = "carrera")
    Optional<Alumno> findConCarreraByNumeroControl(String numeroControl);

    @EntityGraph(attributePaths = "carrera")
    @Query("""
            select a from Alumno a
            where :busqueda = ''
               or lower(a.numeroControl) like lower(concat('%', :busqueda, '%'))
               or lower(a.nombre) like lower(concat('%', :busqueda, '%'))
               or lower(a.apellidoPaterno) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(a.apellidoMaterno, '')) like lower(concat('%', :busqueda, '%'))
               or lower(concat(a.nombre, ' ', a.apellidoPaterno, ' ',
                    coalesce(a.apellidoMaterno, ''))) like lower(concat('%', :busqueda, '%'))
               or lower(coalesce(a.correo, '')) like lower(concat('%', :busqueda, '%'))
               or lower(a.carrera.nombre) like lower(concat('%', :busqueda, '%'))
            """)
    Page<Alumno> buscarCoincidencias(@Param("busqueda") String busqueda, Pageable pageable);

    /** Filtros por partes de la consulta de alumnos; el nivel actual se filtra después, en el servicio. */
    @EntityGraph(attributePaths = "carrera")
    @Query("""
            select a from Alumno a
            where (:idCarrera is null or a.carrera.id = :idCarrera)
              and (:numeroControl = '' or lower(a.numeroControl) like lower(concat('%', :numeroControl, '%')))
              and (:nombre = '' or lower(concat(a.nombre, ' ', a.apellidoPaterno, ' ',
                    coalesce(a.apellidoMaterno, ''))) like lower(concat('%', :nombre, '%')))
              and (:busqueda = ''
                   or lower(a.numeroControl) like lower(concat('%', :busqueda, '%'))
                   or lower(concat(a.nombre, ' ', a.apellidoPaterno, ' ',
                        coalesce(a.apellidoMaterno, ''))) like lower(concat('%', :busqueda, '%'))
                   or lower(coalesce(a.correo, '')) like lower(concat('%', :busqueda, '%'))
                   or lower(a.carrera.nombre) like lower(concat('%', :busqueda, '%')))
            order by a.apellidoPaterno, a.apellidoMaterno, a.nombre
            """)
    java.util.List<Alumno> filtrar(@Param("busqueda") String busqueda, @Param("idCarrera") Integer idCarrera,
            @Param("numeroControl") String numeroControl, @Param("nombre") String nombre);
}
