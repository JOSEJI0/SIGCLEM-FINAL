package mx.edu.itch.sigclem.constancias.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;

/** Firmantes y textos del oficio de las constancias (un solo registro, id = 1), editables por coordinación. */
@Entity
@Table(name = "configuracion_constancia")
@Getter
@Setter
@NoArgsConstructor
public class ConfiguracionConstancia {

    public static final int ID = 1;

    @Id
    @Column(name = "id_configuracion")
    private Integer id;

    @Column(name = "subdirector_nombre", nullable = false, length = 100)
    private String subdirectorNombre;

    /** «F» o «M»: define «Subdirectora» o «Subdirector» en las constancias. */
    @Column(name = "subdirector_genero", nullable = false, length = 1)
    private String subdirectorGenero;

    @Column(name = "jefe_dgtv_nombre", nullable = false, length = 100)
    private String jefeDgtvNombre;

    @Column(name = "jefe_dgtv_genero", nullable = false, length = 1)
    private String jefeDgtvGenero;

    @Column(name = "coordinador_nombre", nullable = false, length = 100)
    private String coordinadorNombre;

    @Column(name = "coordinador_genero", nullable = false, length = 1)
    private String coordinadorGenero;

    /** Lema institucional bajo «ATENTAMENTE»; un renglón por línea. */
    @Column(nullable = false, length = 200)
    private String lema;

    @Column(name = "registro_coordinacion", nullable = false, length = 100)
    private String registroCoordinacion;

    /** Inicio del número de oficio; se completa con el folio y el año: PREFIJO/FOLIO/AÑO. */
    @Column(name = "prefijo_oficio", nullable = false, length = 60)
    private String prefijoOficio;

    /** Bloque «c.c.p.» con las iniciales al pie del oficio; un renglón por línea. */
    @Column(nullable = false, length = 200)
    private String copias;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_actualiza")
    private Usuario usuarioActualiza;
}
