package mx.edu.itch.sigclem.pagos.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Coteja la descripción del recibo oficial de cobro con el cargo. El CFDI del TecNM se emite a «público en
 * general» (RFC XAXX010101000), así que el único dato que liga el pago con el alumno es la descripción, p. ej.
 * «CURSO DE LENGUA EXTRANJERA MODALIDAD PRESENCIAL ISIC 22520187 TEPETITLAN MANCILLA JOSE DE JESUS».
 */
final class CotejoDescripcion {

    static final String COINCIDE = "Coincide";
    static final String PARCIAL = "Parcial";
    static final String NO_COINCIDE = "No coincide";
    static final String SIN_TEXTO = "Sin texto";

    /** Palabras que indican un pago de curso de idioma en la descripción del recibo. */
    private static final List<String> CLAVES_CURSO = List.of("LENGUA EXTRANJERA", "LENGUAS EXTRANJERAS", "INGLES",
            "IDIOMA", "LENGUA");
    private static final List<String> PALABRAS_VACIAS = List.of("CURSO", "NIVEL", "PAGO", "PARA", "DEL", "LOS", "LAS");

    record Resultado(String estatus, String detalle, String descripcion) {
    }

    private CotejoDescripcion() {
    }

    static Resultado cotejar(String texto, String numeroControl, String nombre, String apellidoPaterno,
            String apellidoMaterno, String concepto) {
        if (texto == null || texto.isBlank()) {
            return new Resultado(SIN_TEXTO,
                    "No se pudo leer el texto del recibo (escaneo o foto sin OCR). Coteja la descripción en la imagen.",
                    null);
        }
        String plano = sinAcentos(texto).toUpperCase(Locale.ROOT);
        String normal = (" " + plano.replaceAll("[^A-Z0-9]+", " ") + " ").replaceAll(" +", " ");
        String compacto = normal.replace(" ", "");

        String control = sinAcentos(numeroControl).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        boolean controlEncontrado = !control.isEmpty()
                && (compacto.contains(control) || confusiones(compacto).contains(confusiones(control)));

        List<String> partesNombre = new ArrayList<>();
        for (String parte : new String[] { apellidoPaterno, apellidoMaterno, nombre }) {
            if (parte != null) partesNombre.addAll(Arrays.asList(normalizar(parte).trim().split(" ")));
        }
        partesNombre.removeIf(p -> p.length() < 3);
        long nombresEncontrados = partesNombre.stream().filter(p -> normal.contains(" " + p + " ")).count();
        boolean nombreEncontrado = !partesNombre.isEmpty()
                && nombresEncontrados >= Math.min(2, partesNombre.size());

        String conceptoNormal = normalizar(concepto == null ? "" : concepto).trim();
        List<String> claves;
        if (conceptoNormal.contains("CURSO")) {
            claves = CLAVES_CURSO;
        } else {
            claves = Arrays.stream(conceptoNormal.split(" "))
                    .filter(p -> p.length() >= 5 && !PALABRAS_VACIAS.contains(p)).toList();
        }
        boolean conceptoEncontrado = claves.isEmpty()
                || claves.stream().anyMatch(c -> normal.contains(" " + c + " "));

        String estatus;
        if (controlEncontrado && conceptoEncontrado) {
            estatus = COINCIDE;
        } else if (controlEncontrado || (nombreEncontrado && conceptoEncontrado)) {
            estatus = PARCIAL;
        } else {
            estatus = NO_COINCIDE;
        }
        String detalle = "Número de control " + numeroControl + ": " + (controlEncontrado ? "sí aparece" : "no aparece")
                + " · Nombre del alumno: " + (nombreEncontrado ? "sí aparece" : "no aparece")
                + " · Concepto «" + concepto + "»: " + (conceptoEncontrado ? "sí corresponde" : "no corresponde");
        return new Resultado(estatus, detalle, extraerDescripcion(texto, plano));
    }

    /** Fragmento entre el encabezado «Descripción» y «Importe con letra», para mostrarlo a coordinación. */
    private static String extraerDescripcion(String original, String plano) {
        int inicio = plano.indexOf("DESCRIPCION");
        inicio = inicio < 0 ? 0 : inicio + "DESCRIPCION".length();
        int fin = plano.indexOf("IMPORTE CON LETRA", inicio);
        if (fin < 0) fin = Math.min(plano.length(), inicio + 400);
        String fragmento = original.substring(inicio, fin).replaceAll("\\s+", " ").trim();
        return fragmento.length() > 500 ? fragmento.substring(0, 500) : fragmento;
    }

    private static String normalizar(String valor) {
        return (" " + sinAcentos(valor).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ") + " ")
                .replaceAll(" +", " ");
    }

    /** Quita acentos conservando la longitud, para que los índices del texto normalizado sirvan en el original. */
    static String sinAcentos(String valor) {
        if (valor == null) return "";
        StringBuilder sb = new StringBuilder(valor.length());
        for (char c : valor.toCharArray()) {
            int i = "ÁÉÍÓÚÜÑáéíóúüñÀÈÌÒÙàèìòù".indexOf(c);
            sb.append(i < 0 ? c : "AEIOUUNaeiouunAEIOUaeiou".charAt(i));
        }
        return sb.toString();
    }

    /** Errores típicos del OCR entre letras y dígitos. */
    private static String confusiones(String valor) {
        return valor.replace('O', '0').replace('I', '1').replace('L', '1').replace('S', '5').replace('B', '8')
                .replace('Z', '2').replace('G', '6');
    }
}
