package mx.edu.itch.sigclem.alumnos.domain;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Estado del alumno en cada nivel (ART-10). */
@Entity
@Table(name = "avance_nivel")
@Getter
@Setter
@NoArgsConstructor
public class AvanceNivel {

    @EmbeddedId
    private AvanceNivelId id;

    @Convert(converter = EstatusAvance.Convertidor.class)
    @Column(nullable = false, length = 12)
    private EstatusAvance estatus;

    @Column(length = 30)
    private String origen;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDate fechaRegistro;

    public AvanceNivel(String numeroControl, Integer idNivel, EstatusAvance estatus) {
        this.id = new AvanceNivelId(numeroControl, idNivel);
        this.estatus = estatus;
        this.fechaRegistro = LocalDate.now();
    }
}
