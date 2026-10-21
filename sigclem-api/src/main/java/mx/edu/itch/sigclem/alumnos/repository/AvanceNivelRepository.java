package mx.edu.itch.sigclem.alumnos.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import mx.edu.itch.sigclem.alumnos.domain.AvanceNivel;
import mx.edu.itch.sigclem.alumnos.domain.AvanceNivelId;

public interface AvanceNivelRepository extends JpaRepository<AvanceNivel, AvanceNivelId> {

    List<AvanceNivel> findByIdNumeroControl(String numeroControl);

    List<AvanceNivel> findByIdNumeroControlIn(java.util.Collection<String> numerosControl);

    java.util.Optional<AvanceNivel> findByIdNumeroControlAndIdIdNivel(String numeroControl, Integer idNivel);
}
