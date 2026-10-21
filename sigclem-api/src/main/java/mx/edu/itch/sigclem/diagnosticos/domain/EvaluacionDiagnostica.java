package mx.edu.itch.sigclem.diagnosticos.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
import mx.edu.itch.sigclem.seguridad.domain.Usuario;

@Entity
@Table(name = "evaluacion_diagnostica")
@Getter
@Setter
@NoArgsConstructor
public class EvaluacionDiagnostica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_diagnostico")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "numero_control", nullable = false)
    private Alumno alumno;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDate fechaSolicitud;

    @Column(name = "fecha_hora_aplicacion")
    private LocalDateTime fechaHoraAplicacion;

    @Column(length = 255)
    private String enlace;

    private Integer puntaje;

    @Column(length = 20)
    private String recomendacion;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(nullable = false, length = 12)
    private String estatus = "Solicitada";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_programa")
    private Usuario usuarioPrograma;
}
