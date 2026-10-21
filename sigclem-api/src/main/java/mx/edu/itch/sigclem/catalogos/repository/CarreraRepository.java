package mx.edu.itch.sigclem.catalogos.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.catalogos.domain.Carrera;

public interface CarreraRepository extends JpaRepository<Carrera, Integer> {
}
