package mx.edu.itch.sigclem.auditoria.domain;

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

/** Registro de una acción sensible (tabla REGISTRO_AUDITORIA, migración V18). Es de solo inserción. */
@Entity
@Table(name = "registro_auditoria")
@Getter
@Setter
@NoArgsConstructor
public class RegistroAuditoria {

    public static final String OK = "OK";
    public static final String RECHAZADO = "RECHAZADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_registro")
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(length = 50)
    private String usuario;

    @Column(length = 30)
    private String rol;

    @Column(nullable = false, length = 40)
    private String accion;

    @Column(nullable = false, length = 12)
    private String resultado;

    @Column(length = 500)
    private String detalle;

    @Column(length = 45)
    private String ip;

    public RegistroAuditoria(String usuario, String rol, String accion, String resultado, String detalle, String ip) {
        this.fecha = LocalDateTime.now();
        this.usuario = usuario;
        this.rol = rol;
        this.accion = accion;
        this.resultado = resultado;
        this.detalle = detalle;
        this.ip = ip;
    }
}
