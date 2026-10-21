package mx.edu.itch.sigclem.catalogos.dto;

import mx.edu.itch.sigclem.catalogos.domain.Modalidad;

public record ModalidadDTO(Integer id, String nombre) {

    public static ModalidadDTO desde(Modalidad modalidad) {
        return new ModalidadDTO(modalidad.getId(), modalidad.getNombre());
    }
}
