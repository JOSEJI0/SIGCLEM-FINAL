package mx.edu.itch.sigclem.alumnos.domain;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "historial_academico")
@Data
public class HistorialAcademico {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alumno_id", nullable = false)
    private Alumno alumno;

    private String nivel;
    private String periodo;
    private String facilitador;
    private String grupo;
    private String horario;
    private String aula;
    private Double calificacion;
}