package mx.edu.itch.sigclem.catalogos.dto;

import mx.edu.itch.sigclem.catalogos.domain.Carrera;

public record CarreraDTO(Integer id, String nombre, String clavePlan) {

    public static CarreraDTO desde(Carrera c) {
        return new CarreraDTO(c.getId(), c.getNombre(), c.getClavePlan());
    }
}
