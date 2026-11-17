package mx.edu.itch.sigclem.alumnos.domain;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Registro histórico de un nivel cursado antes de operar en el sistema (o fuera de él). Se guarda tal como venía en
 * el concentrado: los campos vacíos se quedan vacíos y la calificación original se conserva en
 * {@code calificacionTexto}.
 */
@Entity
@Table(name = "historial_academico")
@Data
public class HistorialAcademico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumno_id", referencedColumnName = "numero_control",
    nullable = false)
    @lombok.ToString.Exclude
    @lombok.EqualsAndHashCode.Exclude
    private Alumno alumno;

    private String nivel;
    private String periodo;
    private String facilitador;
    private String grupo;
    private String horario;
    private String aula;
    /** Calificación numérica (0-100) cuando se pudo interpretar; nula en «NA», «N/A» o vacía. */
    private Double calificacion;

    /** Calificación tal como venía escrita en el Excel, por ejemplo «100 (Cien)» o «NA BAJA». */
    @Column(name = "calificacion_texto", length = 60)
    private String calificacionTexto;

    /** {@code true} con 70 o más, {@code false} con «NA» o menos de 70, nulo si no se pudo determinar. */
    private Boolean acreditado;

    @Column(name = "observacion_historica", length = 500)
    private String observacionHistorica;
}
