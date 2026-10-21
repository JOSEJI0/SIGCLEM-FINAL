package mx.edu.itch.sigclem.constancias.dto;

import java.util.List;

/**
 * Datos vigentes del membrete y las firmas con los que se genera una constancia. Las imágenes pueden ser null
 * (el PDF usa entonces el logo incluido en el sistema o deja el espacio libre).
 */
public record MembreteConstancia(
        Firmante subdirector,
        Firmante jefeDgtv,
        Firmante coordinador,
        String lema,
        String registroCoordinacion,
        String prefijoOficio,
        String copias,
        byte[] encabezado,
        byte[] pie,
        byte[] logoCoordinacion) {

    /** Persona que firma; {@code mujer} define el cargo en femenino o masculino. */
    public record Firmante(String nombre, boolean mujer) {
    }

    /** Valores iniciales (los mismos que carga la migración V14), útiles en pruebas y vistas previas. */
    public static MembreteConstancia porOmision() {
        return new MembreteConstancia(
                new Firmante("Yanet Evangelista Alcocer", true),
                new Firmante("María Esther Durán Figueroa", true),
                new Firmante("José Antonio Chávez Román", false),
                "Excelencia en Educación Tecnológica®\nCrear Tecnología es Forjar Libertad",
                "TecNM-SeyV-DVIA-CNLE-07/24-ITChilpancingo-142", "12DIT0002E/D/SPYV/DGTYV",
                "c.c.p. Archivo\nYEA/MEDF", null, null, null);
    }

    /** «SUBDIRECTORA DE PLANEACIÓN Y VINCULACIÓN» en dos renglones. */
    public List<String> cargoSubdirector() {
        return List.of((subdirector.mujer() ? "SUBDIRECTORA" : "SUBDIRECTOR") + " DE PLANEACIÓN", "Y VINCULACIÓN");
    }

    /** «JEFA DEL DEPARTAMENTO DE GESTIÓN TECNOLÓGICA Y VINCULACIÓN» en dos renglones. */
    public List<String> cargoJefeDgtv() {
        return List.of((jefeDgtv.mujer() ? "JEFA" : "JEFE") + " DEL DEPARTAMENTO DE GESTIÓN",
                "TECNOLÓGICA Y VINCULACIÓN");
    }

    /** «COORDINADOR DE LENGUAS EXTRANJERAS Y MATERNAS» en dos renglones. */
    public List<String> cargoCoordinador() {
        return List.of((coordinador.mujer() ? "COORDINADORA" : "COORDINADOR") + " DE LENGUAS",
                "EXTRANJERAS Y MATERNAS");
    }

    public List<String> lineas(String texto) {
        return texto == null || texto.isBlank() ? List.of() : List.of(texto.split("\\R"));
    }
}
