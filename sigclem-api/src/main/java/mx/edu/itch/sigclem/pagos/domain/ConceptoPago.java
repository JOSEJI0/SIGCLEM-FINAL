package mx.edu.itch.sigclem.pagos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "concepto_pago")
@Getter
@Setter
@NoArgsConstructor
public class ConceptoPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_concepto")
    private Integer id;

    @Column(nullable = false, unique = true, length = 30)
    private String nombre;

    @Column(name = "requiere_nivel", nullable = false)
    private boolean requiereNivel;

    @Column(nullable = false)
    private boolean activo = true;
}
