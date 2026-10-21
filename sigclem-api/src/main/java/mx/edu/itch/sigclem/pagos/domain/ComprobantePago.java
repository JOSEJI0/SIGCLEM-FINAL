package mx.edu.itch.sigclem.pagos.domain;

import java.math.BigDecimal;
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
import mx.edu.itch.sigclem.seguridad.domain.Usuario;

@Entity
@Table(name = "comprobante_pago")
@Getter
@Setter
@NoArgsConstructor
public class ComprobantePago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_comprobante")
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_autorizacion", nullable = false)
    private AutorizacionPago autorizacion;

    @Column(name = "uuid_cfdi", unique = true, length = 36)
    private String uuidCfdi;

    @Column(name = "rfc_emisor", length = 13)
    private String rfcEmisor;

    @Column(precision = 10, scale = 2)
    private BigDecimal total;

    @Column(name = "contenido_qr", length = 255)
    private String contenidoQr;

    @Column(nullable = false, length = 255)
    private String archivo;

    @Column(name = "fecha_carga", nullable = false, insertable = false, updatable = false)
    private LocalDateTime fechaCarga;

    @Column(nullable = false, length = 15)
    private String estatus = "Pendiente";

    @Column(name = "motivo_rechazo", length = 200)
    private String motivoRechazo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_valida")
    private Usuario usuarioValida;

    /** De dónde salieron UUID, RFC y total: QR leído en el servidor, QR enviado por el alumno o captura. */
    @Column(name = "origen_datos", length = 15)
    private String origenDatos;

    /** Últimos 8 caracteres del sello digital del CFDI (parámetro fe del QR), necesarios para consultar al SAT. */
    @Column(name = "sello_final", length = 8)
    private String selloFinal;

    @Column(name = "estado_sat", length = 20)
    private String estadoSat;

    @Column(name = "detalle_sat", length = 200)
    private String detalleSat;

    @Column(name = "fecha_consulta_sat")
    private LocalDateTime fechaConsultaSat;

    @Column(name = "cotejo_descripcion", length = 15)
    private String cotejoDescripcion;

    @Column(name = "detalle_cotejo", length = 400)
    private String detalleCotejo;

    @Column(name = "descripcion_leida", length = 500)
    private String descripcionLeida;
}
