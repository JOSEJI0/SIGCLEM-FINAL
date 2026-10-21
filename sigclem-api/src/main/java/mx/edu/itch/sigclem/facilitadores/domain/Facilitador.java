package mx.edu.itch.sigclem.facilitadores.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;

@Entity
@Table(name = "facilitador")
@Getter
@Setter
@NoArgsConstructor
public class Facilitador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_facilitador")
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_usuario", nullable = false, unique = true)
    private Usuario usuario;

    @Column(nullable = false, length = 60)
    private String nombre;

    @Column(name = "apellido_paterno", nullable = false, length = 40)
    private String apellidoPaterno;

    @Column(name = "apellido_materno", length = 40)
    private String apellidoMaterno;

    @Column(nullable = false, unique = true, length = 80)
    private String correo;

    @Column(length = 15)
    private String telefono;

    public String nombreCompleto() {
        return String.join(" ", nombre, apellidoPaterno, apellidoMaterno == null ? "" : apellidoMaterno).trim();
    }
}
