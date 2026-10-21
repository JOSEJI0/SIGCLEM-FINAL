package mx.edu.itch.sigclem.constancias.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Imagen del membrete de las constancias; se reemplaza cada año cuando cambian los logos. */
@Entity
@Table(name = "imagen_constancia")
@Getter
@Setter
@NoArgsConstructor
public class ImagenConstancia {

    public enum Tipo {
        /** Franja superior del oficio de acreditación (logos de Educación, TecNM y emblema del año). */
        ENCABEZADO,
        /** Franja inferior del oficio (imagen del año, dirección y logos de certificaciones). */
        PIE,
        /** Logo de la Coordinación de Lenguas Extranjeras y Maternas (constancia de avance por nivel). */
        LOGO_COORDINACION
    }

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Tipo tipo;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] contenido;

    @Column(name = "tipo_contenido", nullable = false, length = 20)
    private String tipoContenido;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;
}
