package mx.edu.itch.sigclem.grupos.domain;

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
import mx.edu.itch.sigclem.catalogos.domain.Periodo;

/**
 * Encuesta de inicio de semestre: el grupo (curso y horario) que el alumno eligió entre los abiertos para su
 * siguiente nivel. Coordinación lo usa para asignarlo; una sola elección por alumno y periodo.
 */
@Entity
@Table(name = "preferencia_grupo")
@Getter
@Setter
@NoArgsConstructor
public class PreferenciaGrupo {

    public static final String PENDIENTE = "Pendiente";
    public static final String ASIGNADA = "Asignada";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_preferencia")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "numero_control", nullable = false)
    private Alumno alumno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_grupo", nullable = false)
    private Grupo grupo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_periodo", nullable = false)
    private Periodo periodo;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @Column(nullable = false, length = 10)
    private String estatus = PENDIENTE;
}
