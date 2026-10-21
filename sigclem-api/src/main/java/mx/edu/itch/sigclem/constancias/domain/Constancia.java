package mx.edu.itch.sigclem.constancias.domain;

import java.time.LocalDate;

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
@Table(name = "constancia")
@Getter
@Setter
@NoArgsConstructor
public class Constancia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_constancia")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "numero_control", nullable = false)
    private Alumno alumno;

    @Column(length = 20, unique = true)
    private String folio;

    @Column(name = "fecha_solicitud", nullable = false)
    private LocalDate fechaSolicitud;

    @Column(name = "fecha_expedicion")
    private LocalDate fechaExpedicion;

    @Column(nullable = false, length = 30)
    private String idioma = "Inglés";

    @Column(name = "niveles_acreditados", nullable = false)
    private Integer nivelesAcreditados;

    @Column(name = "total_horas", nullable = false)
    private Integer totalHoras;

    @Column(name = "nivel_mcer", nullable = false, length = 2)
    private String nivelMcer;

    @Column(nullable = false, length = 12)
    private String estatus = "Solicitada";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_autoriza")
    private Usuario usuarioAutoriza;

    @Column(name = "archivo_pdf", length = 255)
    private String archivoPdf;

    @Column(name = "motivo_rechazo", length = 200)
    private String motivoRechazo;
}
