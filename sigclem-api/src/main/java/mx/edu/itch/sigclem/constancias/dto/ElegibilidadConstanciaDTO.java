package mx.edu.itch.sigclem.constancias.dto;

import java.util.List;

public record ElegibilidadConstanciaDTO(
        String idioma,
        Integer nivelesAcreditados,
        Integer nivelesRequeridos,
        Integer horasAcreditadas,
        String nivelMcer,
        boolean elegible,
        List<String> nivelesFaltantes) {
}
