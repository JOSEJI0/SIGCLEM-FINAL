package mx.edu.itch.sigclem.notificaciones.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.notificaciones.domain.Notificacion;

public interface NotificacionRepository extends JpaRepository<Notificacion, Integer> {

    @EntityGraph(attributePaths = {"origen", "destino"})
    List<Notificacion> findTop100ByOrigenNombreUsuarioOrderByFechaEnvioDesc(String nombreUsuario);

    @EntityGraph(attributePaths = {"origen", "destino"})
    List<Notificacion> findTop100ByDestinoNombreUsuarioOrderByFechaEnvioDesc(String nombreUsuario);

    Optional<Notificacion> findByIdAndDestinoNombreUsuario(Integer id, String nombreUsuario);

    @EntityGraph(attributePaths = {"origen", "origen.rol"})
    List<Notificacion> findTop200ByDestinoIdOrderByFechaEnvioDescIdDesc(Integer idUsuario);

    @EntityGraph(attributePaths = {"destino", "destino.rol"})
    List<Notificacion> findTop500ByOrigenIdOrderByFechaEnvioDescIdDesc(Integer idUsuario);

    Optional<Notificacion> findByIdAndDestinoId(Integer id, Integer idUsuario);

    long countByDestinoIdAndLeidaFalse(Integer idUsuario);
}
