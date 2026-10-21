package mx.edu.itch.sigclem.constancias.service;

import static mx.edu.itch.sigclem.constancias.service.PdfConstancia.NEGRITA;
import static mx.edu.itch.sigclem.constancias.service.PdfConstancia.NEGRITA_CURSIVA;
import static mx.edu.itch.sigclem.constancias.service.PdfConstancia.NORMAL;
import static mx.edu.itch.sigclem.constancias.service.PdfConstancia.Trozo.b;
import static mx.edu.itch.sigclem.constancias.service.PdfConstancia.Trozo.n;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.stereotype.Service;

import mx.edu.itch.sigclem.constancias.dto.ConstanciaDTO;
import mx.edu.itch.sigclem.constancias.dto.MembreteConstancia;

/**
 * Constancia de acreditación de lengua extranjera con el formato de oficio del Departamento de Gestión
 * Tecnológica y Vinculación: membrete del año (encabezado y pie), número de oficio, texto de acreditación,
 * firma de la jefatura del DGTV y visto bueno de la subdirección.
 */
@Service
public class ConstanciaPdfService {

    private static final float ANCHO = PDRectangle.LETTER.getWidth();
    private static final float MARGEN = 50;
    private static final float DERECHA = ANCHO - MARGEN;

    private final ConfiguracionConstanciaService configuracion;

    public ConstanciaPdfService(ConfiguracionConstanciaService configuracion) {
        this.configuracion = configuracion;
    }

    /** Genera la constancia con las firmas y el membrete vigentes. */
    public byte[] generar(ConstanciaDTO constancia) throws IOException {
        return generar(constancia, configuracion.membreteVigente());
    }

    public byte[] generar(ConstanciaDTO constancia, MembreteConstancia membrete) throws IOException {
        LocalDate fecha = constancia.fechaExpedicion() == null ? LocalDate.now() : constancia.fechaExpedicion();
        try (PDDocument documento = new PDDocument(); ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            PDPage pagina = new PDPage(PDRectangle.LETTER);
            documento.addPage(pagina);
            try (PDPageContentStream c = new PDPageContentStream(documento, pagina)) {
                // Encabezado del año; sin imagen cargada se usa el logo del TecNM incluido en el sistema.
                if (!PdfConstancia.imagen(documento, c, membrete.encabezado(), MARGEN - 10, 698, ANCHO - 2 * MARGEN + 20,
                        76, PdfConstancia.Alineacion.IZQUIERDA)) {
                    PdfConstancia.imagen(documento, c, PdfConstancia.recurso("constancias/tecnm-color.png"), MARGEN,
                            712, 200, 48, PdfConstancia.Alineacion.IZQUIERDA);
                }
                PdfConstancia.derecha(c, "Instituto Tecnológico de Chilpancingo", NEGRITA, 9.5f, DERECHA, 684);
                PdfConstancia.derecha(c, "Departamento de Gestión Tecnológica y Vinculación", NORMAL, 8.5f, DERECHA, 672);

                String fechaCorta = "Chilpancingo de los Bravo, Gro., " + fecha.getDayOfMonth() + "/"
                        + PdfConstancia.mes(fecha) + "/" + fecha.getYear();
                PdfConstancia.derecha(c, fechaCorta, NORMAL, 9.5f, DERECHA, 636);
                PdfConstancia.derecha(c, "Oficio No. " + numeroOficio(membrete, constancia.folio(), fecha), NEGRITA,
                        9.5f, DERECHA, 623);

                PdfConstancia.texto(c, "A QUIEN CORRESPONDA", NEGRITA, 10, MARGEN, 580);

                float anchoTexto = ANCHO - 2 * MARGEN;
                float y = PdfConstancia.parrafo(c, cuerpo(constancia, membrete), MARGEN, 548, anchoTexto, 10, 14.5f);
                y = PdfConstancia.parrafo(c, List.of(n("Por lo anterior, el estudiante ha cumplido con el requisito "
                        + "de acreditación de un programa de Lengua Extranjera para efectos de titulación de acuerdo "
                        + "con el lineamiento correspondiente.")), MARGEN, y - 12, anchoTexto, 10, 14.5f);
                y = PdfConstancia.parrafo(c, cierre(fecha), MARGEN, y - 12, anchoTexto, 10, 14.5f);

                firmas(c, membrete, Math.min(y - 46, 330));

                float yCopias = 150;
                for (String linea : membrete.lineas(membrete.copias())) {
                    PdfConstancia.texto(c, linea, NORMAL, 6.5f, MARGEN, yCopias);
                    yCopias -= 8;
                }
                PdfConstancia.imagen(documento, c, membrete.pie(), MARGEN - 20, 22, ANCHO - 2 * MARGEN + 40, 100,
                        PdfConstancia.Alineacion.CENTRO);
            }
            documento.save(salida);
            return salida.toByteArray();
        }
    }

    private List<PdfConstancia.Trozo> cuerpo(ConstanciaDTO constancia, MembreteConstancia membrete) {
        MembreteConstancia.Firmante jefe = membrete.jefeDgtv();
        List<PdfConstancia.Trozo> t = new ArrayList<>();
        t.add(b("Por este conducto " + (jefe.mujer() ? "la que suscribe, " : "el que suscribe, ") + jefe.nombre()
                + " " + (jefe.mujer() ? "Jefa" : "Jefe") + " del Departamento de Gestión Tecnológica y Vinculación, "
                + "hace constar que el (la) estudiante C. " + constancia.nombreAlumno() + " con número de control "
                + constancia.numeroControl()));
        t.add(n(" de la carrera "));
        t.add(b(constancia.carrera()));
        t.add(n(" con clave del plan de estudios "));
        t.add(b(constancia.clavePlan()));
        t.add(n(" acreditó el programa de Lengua Extranjera: "));
        t.add(b(constancia.idioma()));
        t.add(n(" en la "));
        t.add(b("COORDINACIÓN DE LENGUAS EXTRANJERAS Y MATERNAS"));
        t.add(n(" con registro "));
        t.add(b(membrete.registroCoordinacion() + " DEL INSTITUTO TECNOLÓGICO DE CHILPANCINGO"));
        t.add(n(", acreditando " + constancia.nivelesAcreditados() + " niveles de acuerdo al mapa modular y "
                + "cubriendo un total de " + constancia.totalHoras() + " horas, alcanzando las competencias y "
                + "habilidades de comprensión auditiva, de lectura, expresión oral y escrita. Obteniendo el nivel "));
        t.add(b(constancia.nivelMcer()));
        t.add(n(" basado en el Marco Común Europeo de Referencia ("));
        t.add(b("MCER"));
        t.add(n(")."));
        return t;
    }

    private List<PdfConstancia.Trozo> cierre(LocalDate fecha) {
        int dia = fecha.getDayOfMonth();
        List<PdfConstancia.Trozo> t = new ArrayList<>();
        t.add(n("Para los fines legales que convengan al interesado, se extiende la presente en la ciudad de "));
        t.add(b("Chilpancingo Guerrero"));
        if (dia == 1) {
            t.add(n(", al "));
            t.add(b("primer"));
            t.add(n(" día del mes de "));
        } else {
            t.add(n(", a los "));
            t.add(b(PdfConstancia.numeroConLetra(dia).toLowerCase(PdfConstancia.ES)));
            t.add(n(" días del mes de "));
        }
        t.add(b(PdfConstancia.mes(fecha)));
        t.add(n(" del año "));
        t.add(b(PdfConstancia.anioConLetra(fecha.getYear())));
        t.add(n("."));
        return t;
    }

    private void firmas(PDPageContentStream c, MembreteConstancia membrete, float y) throws IOException {
        float izquierda = MARGEN + 20;
        PdfConstancia.texto(c, "A T E N T A M E N T E", NEGRITA, 9.5f, izquierda, y);
        float yLema = y - 12;
        for (String linea : membrete.lineas(membrete.lema())) {
            PdfConstancia.texto(c, linea, NEGRITA_CURSIVA, 9, izquierda, yLema);
            yLema -= 11;
        }
        float columnaDerecha = 360;
        PdfConstancia.centrado(c, "Vo. Bo.", NEGRITA, 9.5f, columnaDerecha + 60, y);

        // Espacio para firma autógrafa y sello.
        float yNombre = y - 105;
        PdfConstancia.texto(c, PdfConstancia.mayusculas(membrete.jefeDgtv().nombre()), NEGRITA, 9.5f, izquierda, yNombre);
        PdfConstancia.texto(c, PdfConstancia.mayusculas(membrete.subdirector().nombre()), NEGRITA, 9.5f,
                columnaDerecha, yNombre);
        float yCargo = yNombre - 12;
        for (String linea : membrete.cargoJefeDgtv()) {
            PdfConstancia.texto(c, linea, NEGRITA, 9, izquierda, yCargo);
            yCargo -= 11;
        }
        yCargo = yNombre - 12;
        for (String linea : membrete.cargoSubdirector()) {
            PdfConstancia.texto(c, linea, NEGRITA, 9, columnaDerecha, yCargo);
            yCargo -= 11;
        }
    }

    /** PREFIJO/FOLIO/AÑO, p. ej. 12DIT0002E/D/SPYV/DGTYV/1620/2025. */
    static String numeroOficio(MembreteConstancia membrete, String folio, LocalDate fecha) {
        String prefijo = membrete.prefijoOficio() == null ? "" : membrete.prefijoOficio().trim();
        String numero = folio == null || folio.isBlank() ? "0000" : folio.trim();
        return (prefijo.isEmpty() ? "" : prefijo + "/") + numero + "/" + fecha.getYear();
    }
}
