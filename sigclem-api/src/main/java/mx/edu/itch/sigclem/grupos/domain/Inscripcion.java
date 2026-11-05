package mx.edu.itch.sigclem.grupos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;

@Entity
@Table(name = "inscripcion")
@Getter
@Setter
@NoArgsConstructor
public class Inscripcion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_inscripcion")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "numero_control", nullable = false)
    private Alumno alumno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grupo", nullable = false)
    private Grupo grupo;

    /** Comprobante con el que se liquidó el curso; {@code null} mientras el alumno no paga. */
    @Column(name = "id_comprobante", unique = true)
    private Integer idComprobante;

    /** Cargo del curso generado al asignar al alumno al grupo (papeleta de pago). */
    @Column(name = "id_autorizacion", unique = true)
    private Integer idAutorizacion;

    @Column(nullable = false, length = 2)
    private String oportunidad = "01";

    @Column(name = "calificacion_final")
    private Integer calificacionFinal;

    /** Nivel seleccionado por coordinación al cerrar el acta de un curso Intensivo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_nivel_acreditado")
    private Nivel nivelAcreditado;

    /**
     * El acta se cerró sin que el alumno liquidara el curso: la calificación queda registrada, pero el nivel no se
     * aplica hasta que coordinación lo asigna de forma individual.
     */
    @Column(name = "avance_pendiente", nullable = false)
    private boolean avancePendiente;
}
