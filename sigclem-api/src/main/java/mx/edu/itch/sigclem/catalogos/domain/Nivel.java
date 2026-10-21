package mx.edu.itch.sigclem.catalogos.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Catálogo de los diez niveles internos de inglés. */
@Entity
@Table(name = "nivel")
@Getter
@NoArgsConstructor
public class Nivel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_nivel")
    private Integer id;

    @Column(nullable = false, unique = true)
    private Integer numero;

    @Column(nullable = false, unique = true, length = 10)
    private String clave;

    @Column(nullable = false, length = 20)
    private String nombre;

    @Column(name = "nivel_mcer", nullable = false, length = 2)
    private String nivelMcer;

    @Column(nullable = false)
    private Integer horas;
}
