package mx.edu.itch.sigclem.constancias.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "folio_constancia_usado")
@Getter
@Setter
@NoArgsConstructor
public class FolioConstanciaUsado {

    @Id
    @Column(length = 20)
    private String folio;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro = LocalDateTime.now();

    public FolioConstanciaUsado(String folio) {
        this.folio = folio;
        this.fechaRegistro = LocalDateTime.now();
    }
}
