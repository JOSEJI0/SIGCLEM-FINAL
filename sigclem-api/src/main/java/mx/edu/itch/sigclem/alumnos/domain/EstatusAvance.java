package mx.edu.itch.sigclem.alumnos.domain;

import java.util.Arrays;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Estados del avance de nivel (Figura 22). La etiqueta coincide con el CHECK de la tabla AVANCE_NIVEL. */
public enum EstatusAvance {
    NO_PERMITIDO("No permitido"),
    HABILITADO("Habilitado"),
    CURSANDO("Cursando"),
    ACREDITADO("Acreditado"),
    REPROBADO("Reprobado"),
    EXENTO("Exento");

    private final String etiqueta;

    EstatusAvance(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    public boolean cubreNivel() {
        return this == ACREDITADO || this == EXENTO;
    }

    public static EstatusAvance desdeEtiqueta(String valor) {
        return Arrays.stream(values()).filter(e -> e.etiqueta.equals(valor)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Estatus de avance desconocido: " + valor));
    }

    @Converter
    public static class Convertidor implements AttributeConverter<EstatusAvance, String> {
        @Override
        public String convertToDatabaseColumn(EstatusAvance e) {
            return e == null ? null : e.etiqueta;
        }

        @Override
        public EstatusAvance convertToEntityAttribute(String valor) {
            return valor == null ? null : desdeEtiqueta(valor);
        }
    }
}
