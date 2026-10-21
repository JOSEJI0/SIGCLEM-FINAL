package mx.edu.itch.sigclem.grupos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import mx.edu.itch.sigclem.constancias.dto.MembreteConstancia;
import mx.edu.itch.sigclem.grupos.dto.ActaDTO;
import mx.edu.itch.sigclem.grupos.dto.AlumnoActaDTO;
import mx.edu.itch.sigclem.grupos.dto.GrupoDTO;
import mx.edu.itch.sigclem.grupos.dto.HorarioDTO;

class ActaPdfServiceTest {

    private static GrupoDTO grupo(String estatus) {
        return new GrupoDTO(1, "A", 2, 2, "Básico 2", "AGO-DIC 2026", 1, LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 12, 18), 1, "Entre semana", 3, "Aula 5", 4, "Samuel Isaí Villaseñor Díaz", 30, 2, 0,
                estatus, null, null, null, List.of(new HorarioDTO(1, LocalTime.of(7, 0), LocalTime.of(9, 0)),
                        new HorarioDTO(3, LocalTime.of(7, 0), LocalTime.of(9, 0))));
    }

    @Test
    void generaElActaCerradaConCalificacionesResumenYFirmas() throws Exception {
        var acta = new ActaDTO(grupo("Cerrada"), List.of(
                new AlumnoActaDTO("22520187", "José de Jesús Tepetitlán Mancilla", 100, "Liquidada", null, false),
                new AlumnoActaDTO("22520188", "Ana López Ruiz", 65, "Liquidada", null, false)));

        String texto = texto(new ActaPdfService().generar(acta, MembreteConstancia.porOmision()));

        assertThat(texto).contains("ACTA DE CALIFICACIONES FINALES", "Básico 2 · Grupo A", "AGO-DIC 2026",
                "Lun y Mié 07:00–09:00", "22520187", "CIEN", "Acreditado", "No acreditado", "SESENTA Y CINCO",
                "Inscritos: 2", "Promedio: 82.5", "SAMUEL ISAÍ VILLASEÑOR DÍAZ", "JOSÉ ANTONIO CHÁVEZ ROMÁN",
                "Página 1 de 1");
        assertThat(texto).doesNotContain("PRELIMINAR");
    }

    @Test
    void marcaComoPreliminarYPaginaUnGrupoGrande() throws Exception {
        List<AlumnoActaDTO> alumnos = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            alumnos.add(new AlumnoActaDTO("2252" + String.format("%04d", i), "Alumno " + i, i % 3 == 0 ? null : 80, "Pendiente", null, false));
        }

        byte[] pdf = new ActaPdfService().generar(new ActaDTO(grupo("En captura"), alumnos), MembreteConstancia.porOmision());

        try (PDDocument documento = PDDocument.load(pdf)) {
            assertThat(documento.getNumberOfPages()).isEqualTo(2);
        }
        assertThat(texto(pdf)).contains("DOCUMENTO PRELIMINAR", "Sin captura: 15", "22520044", "Página 2 de 2");
    }

    @Test
    void compactaElHorarioAgrupandoDiasConLaMismaHora() {
        assertThat(ActaPdfService.horarioCompacto(conHorarios(1, 2, 3, 4, 5))).isEqualTo("Lun a Vie 18:00–20:00");
        assertThat(ActaPdfService.horarioCompacto(conHorarios(1, 3))).isEqualTo("Lun y Mié 18:00–20:00");
        assertThat(ActaPdfService.horarioCompacto(conHorarios(1, 2, 3, 5))).isEqualTo("Lun a Mié y Vie 18:00–20:00");
        assertThat(ActaPdfService.horarioCompacto(conHorarios(6))).isEqualTo("Sáb 18:00–20:00");
        var mixto = new ArrayList<HorarioDTO>(List.of(new HorarioDTO(6, LocalTime.of(9, 0), LocalTime.of(13, 0)),
                new HorarioDTO(1, LocalTime.of(7, 0), LocalTime.of(9, 0)), new HorarioDTO(3, LocalTime.of(7, 0), LocalTime.of(9, 0))));
        assertThat(ActaPdfService.horarioCompacto(grupo("Abierta", mixto))).isEqualTo("Lun y Mié 07:00–09:00; Sáb 09:00–13:00");
    }

    @Test
    void elHorarioLargoNoSeEncimaConElEstadoDelActa() throws Exception {
        var acta = new ActaDTO(grupo("En captura", conHorarios(1, 2, 3, 4, 5).horarios()), List.of(
                new AlumnoActaDTO("22520001", "Ana López Hernández", 100, "Liquidada", null, false)));

        String texto = texto(new ActaPdfService().generar(acta, MembreteConstancia.porOmision()));

        assertThat(texto).contains("Horario: Lun a Vie 18:00–20:00", "Estado del acta: En captura");
    }

    private static GrupoDTO conHorarios(int... dias) {
        List<HorarioDTO> horarios = new ArrayList<>();
        for (int d : dias) horarios.add(new HorarioDTO(d, LocalTime.of(18, 0), LocalTime.of(20, 0)));
        return grupo("Abierta", horarios);
    }

    private static GrupoDTO grupo(String estatus, List<HorarioDTO> horarios) {
        return new GrupoDTO(1, "C", 2, 2, "Básico 2", "DEMO 20261014", 1, LocalDate.of(2026, 8, 10),
                LocalDate.of(2026, 12, 18), 1, "Entre semana", 3, "Aula Demo 1", 4, "María Gómez Ruiz", 30, 1, 0,
                estatus, null, null, null, horarios);
    }

    private static String texto(byte[] pdf) throws Exception {
        try (PDDocument documento = PDDocument.load(pdf)) {
            return new PDFTextStripper().getText(documento);
        }
    }
}
