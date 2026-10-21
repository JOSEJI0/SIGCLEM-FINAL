package mx.edu.itch.sigclem.catalogos.dto;

import mx.edu.itch.sigclem.catalogos.domain.Nivel;

public record NivelDTO(Integer id, Integer numero, String clave, String nombre, String nivelMcer, Integer horas) {

    public static NivelDTO desde(Nivel n) {
        return new NivelDTO(n.getId(), n.getNumero(), n.getClave(), n.getNombre(), n.getNivelMcer(), n.getHoras());
    }
}
