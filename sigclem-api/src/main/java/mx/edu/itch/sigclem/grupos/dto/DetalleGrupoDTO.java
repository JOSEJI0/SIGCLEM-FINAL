package mx.edu.itch.sigclem.grupos.dto;

import java.time.LocalDateTime;
import java.util.List;

import mx.edu.itch.sigclem.grupos.domain.AjusteAvance;

/**
 * Vista de coordinación de un grupo: alumnos asignados (con su estatus de pago), interesados de la encuesta y la
 * bitácora de observaciones de coordinación (cierre sin pago y niveles asignados después).
 */
public record DetalleGrupoDTO(GrupoDTO grupo, List<AlumnoActaDTO> inscritos, List<Interesado> interesados,
        List<Observacion> observaciones) {

    public record Interesado(String numeroControl, String nombreCompleto, String carrera, LocalDateTime fechaRegistro) {
    }

    public record Observacion(String numeroControl, String tipo, String estatusNivel, String estatusPago,
            String observacion, String usuario, LocalDateTime fecha) {

        public static Observacion desde(AjusteAvance a) {
            return new Observacion(a.getNumeroControl(), a.getTipo(), a.getEstatusNuevo(), a.getEstatusPago(),
                    a.getObservacion(), a.getUsuario(), a.getFecha());
        }
    }
}
