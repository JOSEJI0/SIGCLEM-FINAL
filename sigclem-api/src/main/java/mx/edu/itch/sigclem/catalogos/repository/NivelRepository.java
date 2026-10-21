package mx.edu.itch.sigclem.catalogos.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.catalogos.domain.Nivel;

public interface NivelRepository extends JpaRepository<Nivel, Integer> {

    List<Nivel> findAllByOrderByNumeroAsc();
}
