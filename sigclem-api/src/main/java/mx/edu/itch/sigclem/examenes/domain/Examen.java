package mx.edu.itch.sigclem.examenes.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;

/** Examen de colocación o de cuatro habilidades (ART-05, ART-06). */
@Entity
@Table(name = "examen")
@Getter
@Setter
@NoArgsConstructor
public class Examen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_examen")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "numero_control")
    private Alumno alumno;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(name = "fecha_hora_aplicacion", nullable = false)
    private LocalDateTime fechaHoraAplicacion;

    @Column(name = "puntaje_total")
    private Integer puntajeTotal;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_evaluador")
    private Usuario evaluador;

    @Column(name = "firma_evaluador", length = 255)
    private String firmaEvaluador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_nivel_asignado")
    private Nivel nivelAsignado;

    @OneToMany(mappedBy = "examen", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ResultadoCriterio> resultados = new ArrayList<>();

    /** Borrado lógico: el examen deja de mostrarse pero se conserva en la base de datos. */
    @Column(nullable = false)
    private boolean oculto;

    /** El resultado se captura asignando directamente el nivel (ya no se usa la rúbrica). */
    public boolean estaConcluido() {
        return nivelAsignado != null;
    }
}
