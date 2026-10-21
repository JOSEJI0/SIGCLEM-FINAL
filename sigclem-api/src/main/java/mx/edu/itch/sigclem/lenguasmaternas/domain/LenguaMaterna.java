package mx.edu.itch.sigclem.lenguasmaternas.domain;

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
@Table(name = "lengua_materna")
@Getter
@Setter
@NoArgsConstructor
public class LenguaMaterna {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_lengua")
    private Integer id;

    @Column(nullable = false, unique = true, length = 40)
    private String nombre;
}
