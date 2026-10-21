package mx.edu.itch.sigclem.pagos.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Concepto de pago con su monto general (todos los niveles pagan lo mismo).
 * {@code requiereNivel} es opcional: indica que el cargo se liga al nivel que cursa el alumno (como «Curso de nivel»).
 */
public record ConceptoRequest(
        @NotBlank(message = "Escribe el nombre del concepto") @Size(max = 30) String nombre,
        @NotNull(message = "Escribe el monto del concepto")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
        @Digits(integer = 8, fraction = 2, message = "El monto admite máximo dos decimales")
        BigDecimal monto,
        Boolean requiereNivel) {
}
