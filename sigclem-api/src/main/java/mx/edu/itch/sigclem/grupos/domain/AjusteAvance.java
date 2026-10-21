package mx.edu.itch.sigclem.grupos.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bitácora de los cambios de nivel hechos por coordinación: cierre sin pago y asignación posterior del nivel. */
@Entity
@Table(name = "ajuste_avance")
@Getter
@Setter
@NoArgsConstructor
public class AjusteAvance {

    public static final String CIERRE_SIN_PAGO = "Cierre sin pago";
    public static final String NIVEL_ASIGNADO = "Nivel asignado";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ajuste")
    private Integer id;

    @Column(name = "numero_control", nullable = false, length = 10)
    private String numeroControl;

    @Column(name = "id_nivel", nullable = false)
    private Integer idNivel;

    @Column(name = "id_inscripcion")
    private Integer idInscripcion;

    @Column(nullable = false, length = 20)
    private String tipo;

    /** Estatus del nivel después del ajuste (Acreditado o Reprobado); vacío si el nivel quedó pendiente. */
    @Column(name = "estatus_nuevo", length = 12)
    private String estatusNuevo;

    /** Estatus del cargo del curso en el momento del ajuste. */
    @Column(name = "estatus_pago", length = 12)
    private String estatusPago;

    @Column(nullable = false, length = 500)
    private String observacion;

    @Column(nullable = false, length = 80)
    private String usuario;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public AjusteAvance(Inscripcion inscripcion, String tipo, String estatusNuevo, String estatusPago,
            String observacion, String usuario) {
        this.numeroControl = inscripcion.getAlumno().getNumeroControl();
        this.idNivel = inscripcion.getGrupo().getNivel().getId();
        this.idInscripcion = inscripcion.getId();
        this.tipo = tipo;
        this.estatusNuevo = estatusNuevo;
        this.estatusPago = estatusPago;
        this.observacion = observacion;
        this.usuario = usuario;
        this.fecha = LocalDateTime.now();
    }
}
