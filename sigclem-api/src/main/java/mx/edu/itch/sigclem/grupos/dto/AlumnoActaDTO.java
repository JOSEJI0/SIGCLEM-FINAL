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
        String estatusPago, BigDecimal saldoPendiente, boolean avancePendiente, Integer idNivelAcreditado,
        String nivelAcreditado, Integer calificacionSecundaria, String estatusPagoSecundario,
        BigDecimal saldoPendienteSecundario, boolean avancePendienteSecundario) {

    public AlumnoActaDTO(String numeroControl, String nombreCompleto, Integer calificacionFinal,
            String estatusPago, BigDecimal saldoPendiente, boolean avancePendiente, Integer idNivelAcreditado,
            String nivelAcreditado) {
        this(numeroControl, nombreCompleto, calificacionFinal, estatusPago, saldoPendiente, avancePendiente,
                idNivelAcreditado, nivelAcreditado, null, null, null, false);
    }

    public static AlumnoActaDTO desde(Inscripcion inscripcion) {
        return desde(inscripcion, null);
    }

    public static AlumnoActaDTO desde(Inscripcion inscripcion, AutorizacionPago cargo) {
        String estatus = cargo != null ? cargo.getEstatus()
                : inscripcion.getIdComprobante() != null ? "Liquidada" : "Sin cargo";
        return new AlumnoActaDTO(inscripcion.getAlumno().getNumeroControl(),
                inscripcion.getAlumno().nombreCompleto(), inscripcion.getCalificacionFinal(),
                estatus, cargo == null ? null : cargo.getSaldoPendiente(), inscripcion.isAvancePendiente(),
                inscripcion.getNivelAcreditado() == null ? null : inscripcion.getNivelAcreditado().getId(),
                inscripcion.getNivelAcreditado() == null ? null : inscripcion.getNivelAcreditado().getNombre(),
                inscripcion.getCalificacionSecundaria(), null, null, inscripcion.isAvancePendienteSecundario());
    }

    public static AlumnoActaDTO desde(Inscripcion inscripcion, AutorizacionPago cargo,
            AutorizacionPago cargoSecundario) {
        AlumnoActaDTO base = desde(inscripcion, cargo);
        String estatusSecundario = cargoSecundario != null ? cargoSecundario.getEstatus()
                : inscripcion.getIdComprobanteSecundario() != null ? "Liquidada"
                        : inscripcion.getIdAutorizacionSecundaria() != null ? "Pendiente" : null;
        return new AlumnoActaDTO(base.numeroControl(), base.nombreCompleto(), base.calificacionFinal(), base.estatusPago(),
                base.saldoPendiente(), base.avancePendiente(), base.idNivelAcreditado(), base.nivelAcreditado(),
                base.calificacionSecundaria(), estatusSecundario,
                cargoSecundario == null ? null : cargoSecundario.getSaldoPendiente(),
                base.avancePendienteSecundario());
    }
}
