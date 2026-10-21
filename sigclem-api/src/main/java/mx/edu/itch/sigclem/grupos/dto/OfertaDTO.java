package mx.edu.itch.sigclem.grupos.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cursos que el alumno puede elegir: solo grupos de su siguiente nivel (sin saltar niveles).
 *
 * @param nivelNumero  nivel que le corresponde cursar (null si ya acreditó el idioma)
 * @param mensaje      explicación cuando no puede elegir (por ejemplo, porque ya cursa un nivel)
 * @param grupos       grupos abiertos de ese nivel; {@code elegido} marca su elección actual
 * @param inscripciones grupos a los que coordinación ya lo asignó, con su cargo de pago
 */
public record OfertaDTO(Integer nivelNumero, String nivel, String mensaje, List<GrupoOfertado> grupos,
        List<Asignacion> inscripciones) {

    public record GrupoOfertado(GrupoDTO grupo, boolean elegido, String estatusEleccion, boolean lleno) {
    }

    public record Asignacion(GrupoDTO grupo, Integer idCargo, BigDecimal montoCargo, BigDecimal saldoPendiente,
            String estatusPago) {
    }
}
