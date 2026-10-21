package mx.edu.itch.sigclem.auditoria.dto;

import java.time.LocalDateTime;

import mx.edu.itch.sigclem.auditoria.domain.AccionAuditoria;
import mx.edu.itch.sigclem.auditoria.domain.RegistroAuditoria;

/** Línea de la bitácora de auditoría para la pantalla de administración. */
public record RegistroAuditoriaDTO(Long id, LocalDateTime fecha, String usuario, String rol, String accion,
        String accionDescripcion, String resultado, String detalle, String ip) {

    public static RegistroAuditoriaDTO desde(RegistroAuditoria r) {
        return new RegistroAuditoriaDTO(r.getId(), r.getFecha(), r.getUsuario(), r.getRol(), r.getAccion(),
                descripcion(r.getAccion()), r.getResultado(), r.getDetalle(), r.getIp());
    }

    private static String descripcion(String accion) {
        try {
            return AccionAuditoria.valueOf(accion).descripcion();
        } catch (IllegalArgumentException e) {
            return accion;
        }
    }
}
