package mx.edu.itch.sigclem.catalogos.dto;

import mx.edu.itch.sigclem.catalogos.domain.Aula;

public record AulaDTO(Integer id, String nombre) {

    public static AulaDTO desde(Aula aula) {
        return new AulaDTO(aula.getId(), aula.getNombre());
    }
}
