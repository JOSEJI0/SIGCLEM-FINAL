package mx.edu.itch.sigclem.grupos.service;

import static mx.edu.itch.sigclem.constancias.service.PdfConstancia.NEGRITA;
import static mx.edu.itch.sigclem.constancias.service.PdfConstancia.NORMAL;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Service;

import mx.edu.itch.sigclem.constancias.dto.MembreteConstancia;
import mx.edu.itch.sigclem.constancias.service.PdfConstancia;
import mx.edu.itch.sigclem.grupos.dto.ActaDTO;
import mx.edu.itch.sigclem.grupos.dto.AlumnoActaDTO;
import mx.edu.itch.sigclem.grupos.dto.GrupoDTO;

/**
 * Acta de calificaciones finales en PDF: datos del grupo, lista de alumnos con calificación y resultado, resumen y
 * firmas del facilitador y de la coordinación. Si el acta no está cerrada, se marca como documento preliminar.
 */
@Service
public class ActaPdfService {

    private static final String[] DIAS = { "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom" };
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final float ANCHO = PDRectangle.LETTER.getWidth();
    private static final float MARGEN = 42;
    private static final float ALTO_FILA = 17;
    private static final int APROBATORIA = 70;
    /** Columnas: No., número de control, alumno, calificación, con letra, resultado. */
    private static final float[] X = { MARGEN, MARGEN + 26, MARGEN + 108, MARGEN + 328, MARGEN + 378, MARGEN + 470 };

    public byte[] generar(ActaDTO acta, MembreteConstancia membrete) throws IOException {
        GrupoDTO g = acta.grupo();
        List<AlumnoActaDTO> alumnos = acta.alumnos();
        boolean cerrada = "Cerrada".equals(g.estatusActa());
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            List<PDPage> paginas = new ArrayList<>();
            int indice = 0;
            do {
                PDPage pagina = new PDPage(PDRectangle.LETTER);
                documento.addPage(pagina);
                paginas.add(pagina);
                try (PDPageContentStream c = new PDPageContentStream(documento, pagina)) {
                    float y = encabezado(documento, c, g, cerrada, paginas.size() == 1);
                    y = encabezadoTabla(c, y, g);
                    // Con observación de coordinación se reserva espacio para el texto antes de las firmas.
                    float limite = g.observacionCierre() == null ? 150 : 210;
                    while (indice < alumnos.size() && y - ALTO_FILA > limite) {
                        fila(c, indice + 1, alumnos.get(indice), g, cerrada, y);
                        y -= ALTO_FILA;
                        indice++;
                    }
                    if (alumnos.isEmpty()) {
                        PdfConstancia.texto(c, "El grupo no tiene alumnos inscritos.", NORMAL, 9, MARGEN, y - 12);
                    }
                    if (indice >= alumnos.size()) {
                        cierre(c, alumnos, g, membrete, cerrada, y);
                    }
                }
            } while (indice < alumnos.size());

            String impresion = "Impreso el " + LocalDate.now().format(FECHA) + " · SIGCLEM";
            for (int i = 0; i < paginas.size(); i++) {
                try (PDPageContentStream c = new PDPageContentStream(documento, paginas.get(i),
                        PDPageContentStream.AppendMode.APPEND, true)) {
                    PdfConstancia.texto(c, impresion, NORMAL, 7, MARGEN, 28);
                    PdfConstancia.derecha(c, "Página " + (i + 1) + " de " + paginas.size(), NORMAL, 7, ANCHO - MARGEN, 28);
                }
            }
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    private float encabezado(PDDocument documento, PDPageContentStream c, GrupoDTO g, boolean cerrada,
            boolean primera) throws IOException {
        PdfConstancia.imagen(documento, c, PdfConstancia.recurso("constancias/tecnm-color.png"), MARGEN, 724, 110, 40,
                PdfConstancia.Alineacion.IZQUIERDA);
        PdfConstancia.imagen(documento, c, PdfConstancia.recurso("constancias/clem-color.png"), ANCHO - MARGEN - 120, 724,
                120, 40, PdfConstancia.Alineacion.DERECHA);
        float centro = ANCHO / 2;
        PdfConstancia.centrado(c, "INSTITUTO TECNOLÓGICO DE CHILPANCINGO", NEGRITA, 10.5f, centro, 752);
        PdfConstancia.centrado(c, "COORDINACIÓN DE LENGUAS EXTRANJERAS Y MATERNAS", NORMAL, 8.5f, centro, 739);
        PdfConstancia.centrado(c, "ACTA DE CALIFICACIONES FINALES", NEGRITA, 12, centro, 712);

        float y = 690;
        String horario = horarioCompacto(g);
        String curso = g.intensivo() ? "Intensivo (10 niveles)"
                : g.nivel() + (g.nivelSecundario() == null ? "" : " y " + g.nivelSecundario());
        dato(c, g.intensivo() ? "Curso:" : "Nivel:",
                curso + " · Grupo " + g.clave(),
                MARGEN, y);
        dato(c, "Periodo:", g.periodo(), 320, y);
        y -= 14;
        dato(c, "Facilitador(a):", g.facilitador(), MARGEN, y);
        dato(c, "Modalidad:", g.modalidad() + " · " + g.aula(), 320, y);
        y -= 14;
        dato(c, "Captura:", g.fechaInicioCaptura().format(FECHA) + " a " + g.fechaFinCaptura().format(FECHA), MARGEN, y);
        y -= 14;
        // El horario ocupa la columna izquierda; si no cabe junto al estado, el estado baja a su propia línea.
        float anchoHorario = PdfConstancia.ancho("Horario: " + horario, NORMAL, 8.5f) + 8;
        if (MARGEN + anchoHorario < 315) {
            dato(c, "Horario:", horario, MARGEN, y);
            dato(c, "Estado del acta:", g.estatusActa(), 320, y);
        } else {
            dato(c, "Horario:", recortar(horario, ANCHO - 2 * MARGEN - 45, 8.5f), MARGEN, y);
            y -= 14;
            dato(c, "Estado del acta:", g.estatusActa(), MARGEN, y);
        }
        if (!cerrada) {
            y -= 18;
            c.setNonStrokingColor(0.70f, 0.12f, 0.10f);
            PdfConstancia.centrado(c, "DOCUMENTO PRELIMINAR · el acta aún no está cerrada y las calificaciones pueden cambiar",
                    NEGRITA, 8.5f, centro, y);
            c.setNonStrokingColor(0f, 0f, 0f);
        }
        return y - 22;
    }

    /**
     * Agrupa los días con el mismo horario y une los días seguidos: «Lun a Vie 18:00–20:00» en lugar de repetir
     * la hora por cada día; con horarios distintos: «Lun y Mié 07:00–09:00; Sáb 09:00–13:00».
     */
    static String horarioCompacto(GrupoDTO g) {
        java.util.Map<String, java.util.TreeSet<Integer>> diasPorHora = new java.util.LinkedHashMap<>();
        g.horarios().stream()
                .sorted(java.util.Comparator.comparing((mx.edu.itch.sigclem.grupos.dto.HorarioDTO h) -> h.horaInicio())
                        .thenComparing(h -> h.diaSemana()))
                .forEach(h -> diasPorHora.computeIfAbsent(h.horaInicio().toString().substring(0, 5) + "–"
                        + h.horaFin().toString().substring(0, 5), k -> new java.util.TreeSet<>()).add(h.diaSemana()));
        List<String> partes = new ArrayList<>();
        diasPorHora.entrySet().stream()
                .sorted(java.util.Comparator.comparing((java.util.Map.Entry<String, java.util.TreeSet<Integer>> e) -> e.getValue().first())
                        .thenComparing(java.util.Map.Entry::getKey))
                .forEach(e -> partes.add(dias(new ArrayList<>(e.getValue())) + " " + e.getKey()));
        return partes.isEmpty() ? "—" : String.join("; ", partes);
    }

    /** [1,2,3,4,5] → «Lun a Vie»; [1,3] → «Lun y Mié»; [1,2,3,5] → «Lun a Mié y Vie». */
    private static String dias(List<Integer> dias) {
        List<String> tramos = new ArrayList<>();
        int i = 0;
        while (i < dias.size()) {
            int j = i;
            while (j + 1 < dias.size() && dias.get(j + 1) == dias.get(j) + 1) j++;
            String inicio = DIAS[dias.get(i) - 1];
            tramos.add(j - i >= 2 ? inicio + " a " + DIAS[dias.get(j) - 1]
                    : j == i ? inicio : inicio + ", " + DIAS[dias.get(j) - 1]);
            i = j + 1;
        }
        if (tramos.size() == 1) return tramos.get(0);
        return String.join(", ", tramos.subList(0, tramos.size() - 1)) + " y " + tramos.get(tramos.size() - 1);
    }

    private void dato(PDPageContentStream c, String etiqueta, String valor, float x, float y) throws IOException {
        PdfConstancia.texto(c, etiqueta, NEGRITA, 8.5f, x, y);
        PdfConstancia.texto(c, valor == null ? "—" : valor, NORMAL, 8.5f, x + PdfConstancia.ancho(etiqueta, NEGRITA, 8.5f) + 4, y);
    }

    private float encabezadoTabla(PDPageContentStream c, float y, GrupoDTO grupo) throws IOException {
        c.setNonStrokingColor(0.90f, 0.93f, 0.97f);
        c.addRect(MARGEN - 4, y - 5, ANCHO - 2 * MARGEN + 8, ALTO_FILA);
        c.fill();
        c.setNonStrokingColor(0f, 0f, 0f);
        boolean dosNiveles = grupo.idNivelSecundario() != null;
        float[] columnas = dosNiveles
                ? new float[] { MARGEN, MARGEN + 26, MARGEN + 108, MARGEN + 330, MARGEN + 380, MARGEN + 430 }
                : X;
        String[] titulos = dosNiveles
                ? new String[] { "No.", "No. de control", "Nombre del alumno", "Niv. " + grupo.nivelNumero(),
                        "Niv. " + grupo.nivelNumeroSecundario(), "Resultado" }
                : new String[] { "No.", "No. de control", "Nombre del alumno", "Calif.", "Con letra", "Resultado" };
        for (int i = 0; i < titulos.length; i++) {
            PdfConstancia.texto(c, titulos[i], NEGRITA, 8, columnas[i], y);
        }
        return y - ALTO_FILA;
    }

    private void fila(PDPageContentStream c, int numero, AlumnoActaDTO a, GrupoDTO grupo,
            boolean cerrada, float y) throws IOException {
        Integer cal = a.calificacionFinal();
        boolean dosNiveles = grupo.idNivelSecundario() != null;
        float[] columnas = dosNiveles
                ? new float[] { MARGEN, MARGEN + 26, MARGEN + 108, MARGEN + 330, MARGEN + 380, MARGEN + 430 }
                : X;
        PdfConstancia.texto(c, String.valueOf(numero), NORMAL, 8, columnas[0], y);
        PdfConstancia.texto(c, a.numeroControl(), NORMAL, 8, columnas[1], y);
        PdfConstancia.texto(c, recortar(a.nombreCompleto(), 215), NORMAL, 8, columnas[2], y);
        PdfConstancia.texto(c, cal == null ? "—" : String.valueOf(cal), NEGRITA, 8, columnas[3], y);
        if (dosNiveles) {
            Integer secundaria = a.calificacionSecundaria();
            PdfConstancia.texto(c, secundaria == null ? "—" : String.valueOf(secundaria), NEGRITA, 8, columnas[4], y);
        } else {
            PdfConstancia.texto(c, cal == null ? "" : PdfConstancia.numeroConLetra(cal), NORMAL, 7, columnas[4], y);
        }
        String resultado = cal == null ? "Sin captura"
                : a.avancePendiente() ? "Pendiente pago"
                : cal >= APROBATORIA ? "Acreditado" : "No acreditado";
        if (dosNiveles && a.calificacionSecundaria() != null) {
            String resultadoSecundario = a.avancePendienteSecundario() ? "Pendiente pago"
                    : a.calificacionSecundaria() >= APROBATORIA ? "Acreditado" : "No acreditado";
            resultado += " / " + resultadoSecundario;
        }
        String nivel = a.nivelAcreditado() == null ? "" : " · " + a.nivelAcreditado();
        PdfConstancia.texto(c, (cerrada || cal != null ? resultado : "Sin captura") + nivel, NORMAL,
                dosNiveles ? 6.5f : 8, columnas[5], y);
        c.setStrokingColor(0.85f, 0.87f, 0.90f);
        c.setLineWidth(0.4f);
        c.moveTo(MARGEN - 4, y - 5);
        c.lineTo(ANCHO - MARGEN + 4, y - 5);
        c.stroke();
        c.setStrokingColor(0f, 0f, 0f);
    }

    private void cierre(PDPageContentStream c, List<AlumnoActaDTO> alumnos, GrupoDTO g, MembreteConstancia membrete,
            boolean cerrada, float y) throws IOException {
        int nivelesPorAlumno = g.idNivelSecundario() == null ? 1 : 2;
        long capturadas = alumnos.stream().mapToLong(a -> (a.calificacionFinal() == null ? 0 : 1)
                + (a.calificacionSecundaria() == null ? 0 : 1)).sum();
        long acreditados = alumnos.stream().mapToLong(a -> (a.calificacionFinal() != null
                && a.calificacionFinal() >= APROBATORIA ? 1 : 0)
                + (a.calificacionSecundaria() != null && a.calificacionSecundaria() >= APROBATORIA ? 1 : 0)).sum();
        long totalCalificaciones = (long) alumnos.size() * nivelesPorAlumno;
        double suma = alumnos.stream().mapToInt(a -> (a.calificacionFinal() == null ? 0 : a.calificacionFinal())
                + (a.calificacionSecundaria() == null ? 0 : a.calificacionSecundaria())).sum();
        double promedio = capturadas == 0 ? 0 : suma / capturadas;
        String resumen = "Inscritos: " + alumnos.size() + " · Calificaciones acreditadas: " + acreditados
                + " · No acreditadas: " + (capturadas - acreditados)
                + (capturadas < totalCalificaciones ? " · Sin captura: " + (totalCalificaciones - capturadas) : "")
                + " · Promedio: " + (capturadas == 0 ? "—" : String.format(PdfConstancia.ES, "%.1f", promedio));
        PdfConstancia.texto(c, resumen, NEGRITA, 8.5f, MARGEN, y - 10);
        float yTexto = y - 10;
        if (g.observacionCierre() != null) {
            yTexto = PdfConstancia.parrafo(c, List.of(
                    new PdfConstancia.Trozo("Cerrada por coordinación el " + g.fechaCierreActa().format(FECHA) + ". "
                            + "Observación: ", NEGRITA),
                    new PdfConstancia.Trozo(g.observacionCierre(), NORMAL)),
                    MARGEN, y - 26, ANCHO - 2 * MARGEN, 8, 10);
            if (alumnos.stream().anyMatch(a -> a.avancePendiente() || a.avancePendienteSecundario())) {
                PdfConstancia.texto(c, "* Calificación registrada; el nivel se asigna cuando el alumno liquide su curso.",
                        NORMAL, 7.5f, MARGEN, yTexto - 4);
                yTexto -= 12;
            }
        }

        float yFirma = Math.min(yTexto - 60, 110);
        float[] columnas = { ANCHO / 4 + 10, ANCHO * 3 / 4 - 10 };
        String[][] firmas = {
                { PdfConstancia.mayusculas(g.facilitador()), "FACILITADOR(A) DEL GRUPO" },
                { PdfConstancia.mayusculas(membrete.coordinador().nombre()), String.join(" ", membrete.cargoCoordinador()) } };
        for (int i = 0; i < 2; i++) {
            c.setLineWidth(0.7f);
            c.moveTo(columnas[i] - 105, yFirma + 12);
            c.lineTo(columnas[i] + 105, yFirma + 12);
            c.stroke();
            PdfConstancia.centrado(c, firmas[i][0], NEGRITA, 8, columnas[i], yFirma);
            PdfConstancia.centrado(c, firmas[i][1], NORMAL, 7, columnas[i], yFirma - 10);
        }
        if (!cerrada) {
            PdfConstancia.centrado(c, "Sin validez oficial hasta el cierre del acta.", NORMAL, 7, ANCHO / 2, yFirma - 26);
        }
    }

    private String recortar(String texto, float anchoMaximo) throws IOException {
        return recortar(texto, anchoMaximo, 8);
    }

    private String recortar(String texto, float anchoMaximo, float tamano) throws IOException {
        String t = texto == null ? "" : texto;
        while (t.length() > 4 && PdfConstancia.ancho(t, NORMAL, tamano) > anchoMaximo) {
            t = t.substring(0, t.length() - 1);
        }
        return t.length() < (texto == null ? 0 : texto.length()) ? t.trim() + "." : t;
    }
}
