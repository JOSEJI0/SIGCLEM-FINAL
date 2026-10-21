package mx.edu.itch.sigclem.alumnos.domain;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AvanceNivelId implements Serializable {

    @Column(name = "numero_control", length = 10)
    private String numeroControl;

    @Column(name = "id_nivel")
    private Integer idNivel;
}
