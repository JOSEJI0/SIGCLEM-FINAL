package mx.edu.itch.sigclem.auditoria.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import mx.edu.itch.sigclem.auditoria.domain.RegistroAuditoria;

public interface RegistroAuditoriaRepository extends JpaRepository<RegistroAuditoria, Long> {

    /** Consulta paginada con filtros opcionales por usuario y por acción, de la más reciente a la más antigua. */
    @Query("""
            select r from RegistroAuditoria r
            where (:usuario = '' or lower(r.usuario) like lower(concat('%', :usuario, '%')))
              and (:accion = '' or r.accion = :accion)
            order by r.fecha desc
            """)
    Page<RegistroAuditoria> buscar(@Param("usuario") String usuario, @Param("accion") String accion, Pageable pageable);
}
