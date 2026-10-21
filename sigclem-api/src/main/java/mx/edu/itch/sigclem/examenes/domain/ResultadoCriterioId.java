package mx.edu.itch.sigclem.examenes.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoCriterioId implements Serializable {

    @Column(name = "id_examen")
    private Integer idExamen;

    @Column(name = "id_criterio")
    private Integer idCriterio;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ResultadoCriterioId that)) return false;
        return Objects.equals(idExamen, that.idExamen) && Objects.equals(idCriterio, that.idCriterio);
    }

    @Override
    public int hashCode() {
        return Objects.hash(idExamen, idCriterio);
    }
}
