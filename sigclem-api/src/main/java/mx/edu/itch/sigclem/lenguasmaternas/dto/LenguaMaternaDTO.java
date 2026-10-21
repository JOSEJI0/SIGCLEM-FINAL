package mx.edu.itch.sigclem.lenguasmaternas.dto;

import mx.edu.itch.sigclem.lenguasmaternas.domain.LenguaMaterna;

public record LenguaMaternaDTO(Integer id, String nombre) {

    public static LenguaMaternaDTO desde(LenguaMaterna lengua) {
        return new LenguaMaternaDTO(lengua.getId(), lengua.getNombre());
    }
}
