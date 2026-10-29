package mx.edu.itch.sigclem.alumnos.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.edu.itch.sigclem.catalogos.domain.Carrera;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;

/** Datos generales del expediente digital (ART-01). */
@Entity
@Table(name = "alumno")
@Getter
@Setter
@NoArgsConstructor
public class Alumno {

    @Id
    @Column(name = "numero_control", length = 10)
    private String numeroControl;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", unique = true)
    private Usuario usuario;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(name = "apellido_paterno", nullable = false, length = 40)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", length = 40)
    private String apellidoMaterno;

    /** Sexo del alumno: 'H' (hombre) o 'M' (mujer); nulo en los expedientes anteriores al cambio 15. */
    @Column(length = 1)
    private String sexo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_carrera")
    private Carrera carrera;

    @Column(nullable = false)
    private Integer semestre;

    /** Última vez que el alumno confirmó o actualizó su semestre. */
    @Column(name = "fecha_actualizacion_semestre")
    private LocalDate fechaActualizacionSemestre;

    @Column(length = 80)
    private String correo;

    @Column(name = "fecha_inscripcion", nullable = false)
    private LocalDate fechaInscripcion;

    /** Respuesta del registro: solo quien habla una lengua materna ve ese apartado. */
    @Column(name = "habla_lengua_materna", nullable = false)
    private boolean hablaLenguaMaterna;

    /** «Activo» o «Baja». Un alumno de baja no puede iniciar sesión ni aparece en listas y reportes activos. */
    @Column(nullable = false, length = 10)
    private String estatus = "Activo";

    @Column(name = "motivo_baja", length = 200)
    private String motivoBaja;

    @Column(name = "fecha_baja")
    private LocalDate fechaBaja;

    @Column(name = "liberado")
    private Boolean liberado = false;

    @OneToMany(mappedBy = "alumno", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HistorialAcademico> historial = new ArrayList<>();

    public boolean estaDeBaja() {
        return "Baja".equals(estatus);
    }

    public String nombreCompleto() {
        return String.join(" ", nombre, apellidoPaterno, apellidoMaterno == null ? "" : apellidoMaterno).trim();
    }
}
