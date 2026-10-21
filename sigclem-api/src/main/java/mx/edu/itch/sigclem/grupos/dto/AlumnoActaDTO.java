package mx.edu.itch.sigclem.grupos.dto;

import java.math.BigDecimal;

import mx.edu.itch.sigclem.grupos.domain.Inscripcion;
import mx.edu.itch.sigclem.pagos.domain.AutorizacionPago;

/**
 * Alumno inscrito en el grupo. {@code estatusPago}: estatus del cargo del curso (Pendiente, Parcial o Liquidada);
 * las inscripciones anteriores, hechas al validar el pago, se consideran liquidadas. {@code avancePendiente}: el acta
 * se cerró sin el pago y coordinación aún no asigna el nivel.
 */
public record AlumnoActaDTO(String numeroControl, String nombreCompleto, Integer calificacionFinal,
        String estatusPago, BigDecimal saldoPendiente, boolean avancePendiente) {

    public static AlumnoActaDTO desde(Inscripcion inscripcion) {
        return desde(inscripcion, null);
    }

    public static AlumnoActaDTO desde(Inscripcion inscripcion, AutorizacionPago cargo) {
        String estatus = cargo != null ? cargo.getEstatus()
                : inscripcion.getIdComprobante() != null ? "Liquidada" : "Sin cargo";
        return new AlumnoActaDTO(inscripcion.getAlumno().getNumeroControl(),
                inscripcion.getAlumno().nombreCompleto(), inscripcion.getCalificacionFinal(),
                estatus, cargo == null ? null : cargo.getSaldoPendiente(), inscripcion.isAvancePendiente());
    }
}
