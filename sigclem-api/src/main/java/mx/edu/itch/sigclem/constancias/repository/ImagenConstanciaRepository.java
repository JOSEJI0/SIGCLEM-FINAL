package mx.edu.itch.sigclem.constancias.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.constancias.domain.ImagenConstancia;

public interface ImagenConstanciaRepository extends JpaRepository<ImagenConstancia, ImagenConstancia.Tipo> {
}
