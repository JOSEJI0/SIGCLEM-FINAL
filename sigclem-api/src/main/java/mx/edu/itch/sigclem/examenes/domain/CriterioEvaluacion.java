package mx.edu.itch.sigclem.examenes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Catálogo de los cinco criterios de la rúbrica (ART-05, ART-06). */
@Entity
@Table(name = "criterio_evaluacion")
@Getter
@NoArgsConstructor
public class CriterioEvaluacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_criterio")
    private Integer id;

    @Column(nullable = false, unique = true, length = 30)
    private String nombre;

    public CriterioEvaluacion(Integer id, String nombre) {
        this.id = id;
        this.nombre = nombre;
    }
}
