package mx.edu.itch.sigclem.examenes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Puntaje de un examen en un criterio específico (ART-05, ART-06). */
@Entity
@Table(name = "resultado_criterio")
@Getter
@Setter
@NoArgsConstructor
public class ResultadoCriterio {

    @EmbeddedId
    private ResultadoCriterioId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idExamen")
    @JoinColumn(name = "id_examen")
    private Examen examen;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("idCriterio")
    @JoinColumn(name = "id_criterio")
    private CriterioEvaluacion criterio;

    @Column(nullable = false)
    private Integer puntaje;

    @Column(length = 255)
    private String nota;

    public ResultadoCriterio(Examen examen, CriterioEvaluacion criterio, Integer puntaje, String nota) {
        this.id = new ResultadoCriterioId(examen.getId(), criterio.getId());
        this.examen = examen;
        this.criterio = criterio;
        this.puntaje = puntaje;
        this.nota = nota;
    }
}
