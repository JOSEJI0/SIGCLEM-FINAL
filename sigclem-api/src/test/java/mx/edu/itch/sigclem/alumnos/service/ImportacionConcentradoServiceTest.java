package mx.edu.itch.sigclem.alumnos.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Interpretación de los valores tal como vienen en el concentrado histórico y en la matrícula. */
class ImportacionConcentradoServiceTest {

    @Test
    void interpretaCalificacionesEscritasConLetra() {
        assertThat(ImportacionConcentradoService.calificacion("100 (Cien)")).isEqualTo(100.0);
        assertThat(ImportacionConcentradoService.calificacion("93(Noventa)")).isEqualTo(93.0);
        assertThat(ImportacionConcentradoService.calificacion("85")).isEqualTo(85.0);
        assertThat(ImportacionConcentradoService.calificacion("87.5")).isEqualTo(87.5);
        assertThat(ImportacionConcentradoService.calificacion("N/A")).isNull();
        assertThat(ImportacionConcentradoService.calificacion("NA BAJA")).isNull();
        assertThat(ImportacionConcentradoService.calificacion("150")).isNull();
        assertThat(ImportacionConcentradoService.calificacion("")).isNull();
    }

    @Test
    void reconoceCalificacionesNoAcreditadas() {
        assertThat(ImportacionConcentradoService.esNoAcreditado("NA")).isTrue();
        assertThat(ImportacionConcentradoService.esNoAcreditado("N/A")).isTrue();
        assertThat(ImportacionConcentradoService.esNoAcreditado("NA BAJA")).isTrue();
        assertThat(ImportacionConcentradoService.esNoAcreditado("NA PENDIENTE POR EXPONER")).isTrue();
        assertThat(ImportacionConcentradoService.esNoAcreditado("Excelente")).isFalse();
    }

    @Test
    void convierteElGeneroDeLaMatricula() {
        // En la matrícula M = masculino y F = femenino; en el sistema H = hombre y M = mujer.
        assertThat(ImportacionConcentradoService.sexo("M")).isEqualTo("H");
        assertThat(ImportacionConcentradoService.sexo("F")).isEqualTo("M");
        assertThat(ImportacionConcentradoService.sexo("")).isNull();
    }

    @Test
    void detectaSiHablaLenguaIndigena() {
        for (String no : new String[] { "No", "NO", "no", "N", "Ninguna", "NINGUNA", "Ninguno", "No.", "", "No hablo" }) {
            assertThat(ImportacionConcentradoService.hablaLengua(no)).as(no).isFalse();
        }
        for (String si : new String[] { "Tlapaneco", "Náhuatl", "Mixteco", "S", "Si, tlapaneco", "Amuzgo" }) {
            assertThat(ImportacionConcentradoService.hablaLengua(si)).as(si).isTrue();
        }
    }

    @Test
    void normalizaNombresDeCarrera() {
        String catalogo = ImportacionConcentradoService.claveCarrera("Ingeniería en Gestión Empresarial");
        assertThat(ImportacionConcentradoService.claveCarrera("Ingeniería en gestión Empresarial ")).isEqualTo(catalogo);
        assertThat(ImportacionConcentradoService.claveCarrera("INGENIERIA EN GESTION EMPRESARIAL virtual")).isEqualTo(catalogo);
        assertThat(ImportacionConcentradoService.claveCarrera("Contador público"))
                .isEqualTo(ImportacionConcentradoService.claveCarrera("CONTADOR PUBLICO"));
    }
}
