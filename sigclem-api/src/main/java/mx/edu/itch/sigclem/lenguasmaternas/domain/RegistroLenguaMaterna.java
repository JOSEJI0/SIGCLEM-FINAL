package mx.edu.itch.sigclem.lenguasmaternas.domain;

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
@Table(name = "registro_lengua_materna")
@Getter
@Setter
@NoArgsConstructor
public class RegistroLenguaMaterna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_registro")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "numero_control", nullable = false)
    private Alumno alumno;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_lengua", nullable = false)
    private LenguaMaterna lengua;

    @Column(name = "nivel_dominio", length = 20)
    private String nivelDominio;

    @Column(length = 255)
    private String documento;

    @Column(name = "documento_curp", length = 255)
    private String documentoCurp;

    @Column(name = "documento_domicilio", length = 255)
    private String documentoDomicilio;

    @Column(name = "documento_ine", length = 255)
    private String documentoIne;

    @Column(name = "documento_intercultural", length = 255)
    private String documentoIntercultural;

    @Column(name = "constancia_acreditacion", length = 255)
    private String constanciaAcreditacion;

    @Column(name = "folio_constancia", length = 20, unique = true)
    private String folioConstancia;

    @Column(name = "constancia_word", length = 255)
    private String constanciaWord;

    @Column(nullable = false, length = 10)
    private String estatus = "Pendiente";

    @Column(name = "documento_rechazado", length = 20)
    private String documentoRechazado;

    @Column(name = "motivo_rechazo", length = 500)
    private String motivoRechazo;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDate fechaRegistro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_valida")
    private Usuario usuarioValida;
}
