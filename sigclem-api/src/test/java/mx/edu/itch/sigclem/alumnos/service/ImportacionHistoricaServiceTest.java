package mx.edu.itch.sigclem.alumnos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.catalogos.domain.Aula;
import mx.edu.itch.sigclem.catalogos.domain.Carrera;
import mx.edu.itch.sigclem.catalogos.domain.Modalidad;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.domain.Periodo;
import mx.edu.itch.sigclem.facilitadores.domain.Facilitador;
import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.grupos.domain.Inscripcion;

class ImportacionHistoricaServiceTest {

    @Test
    void plantillaIncluyeLasHojasNormalizadasDelHistorico() throws IOException {
        ImportacionHistoricaService service = servicioVacio();

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.plantilla()))) {
            assertEquals(6, workbook.getNumberOfSheets());
            assertEquals("Instrucciones", workbook.getSheetAt(0).getSheetName());
            assertEquals("Alumnos", workbook.getSheetAt(1).getSheetName());
            assertEquals("Grupos", workbook.getSheetAt(2).getSheetName());
            assertEquals("Inscripciones", workbook.getSheetAt(3).getSheetName());
            assertEquals("Horarios", workbook.getSheetAt(4).getSheetName());
            assertEquals("Historial_Informativo", workbook.getSheetAt(5).getSheetName());
            assertEquals("Fecha de cierre", workbook.getSheet("Grupos").getRow(0).getCell(11).getStringCellValue());
            assertEquals("Curso Intensivo (Sí/No)",
                    workbook.getSheet("Grupos").getRow(0).getCell(15).getStringCellValue());
            assertEquals("Nivel acreditado (Intensivo)",
                    workbook.getSheet("Inscripciones").getRow(0).getCell(8).getStringCellValue());
        }
    }

    @Test
    void plantillaIncluyeLosGruposCerradosQueYaExistenEnElSistema() throws IOException {
        var grupoRepository = mock(mx.edu.itch.sigclem.grupos.repository.GrupoRepository.class);
        var grupo = mock(Grupo.class);
        var periodo = mock(Periodo.class);
        var nivel = mock(Nivel.class);
        var modalidad = mock(Modalidad.class);
        var facilitador = mock(Facilitador.class);
        var aula = mock(Aula.class);
        when(grupoRepository.findAllConDatosHistoricos()).thenReturn(List.of(grupo));
        when(grupo.getId()).thenReturn(7);
        when(grupo.getEstatusActa()).thenReturn("Cerrada");
        when(grupo.getPeriodo()).thenReturn(periodo);
        when(periodo.getNombre()).thenReturn("2024-1");
        when(periodo.getFechaInicio()).thenReturn(LocalDate.of(2024, 1, 15));
        when(periodo.getFechaFin()).thenReturn(LocalDate.of(2024, 6, 15));
        when(grupo.getNivel()).thenReturn(nivel);
        when(nivel.getNombre()).thenReturn("Básico 1");
        when(nivel.getNumero()).thenReturn(1);
        when(grupo.getModalidad()).thenReturn(modalidad);
        when(modalidad.getNombre()).thenReturn("Sabatino");
        when(grupo.getClave()).thenReturn("A");
        when(grupo.getFacilitador()).thenReturn(facilitador);
        when(facilitador.getCorreo()).thenReturn("facilitador@example.edu.mx");
        when(facilitador.nombreCompleto()).thenReturn("Facilitador Ejemplo");
        when(grupo.getAula()).thenReturn(aula);
        when(aula.getNombre()).thenReturn("Aula 1");
        when(grupo.getCupo()).thenReturn(25);
        when(grupo.getFechaCierreActa()).thenReturn(LocalDate.of(2024, 6, 20));
        when(grupo.getCerradaPor()).thenReturn("Facilitador");
        when(grupo.getObservacionCierre()).thenReturn(null);
        when(grupo.getReferenciaHistorica()).thenReturn("LEGACY-GRUPO-2024");
        when(grupo.getHorarios()).thenReturn(List.of());
        var service = servicioVacio(grupoRepository,
                mock(mx.edu.itch.sigclem.grupos.repository.InscripcionRepository.class),
                mock(mx.edu.itch.sigclem.alumnos.repository.HistorialAcademicoRepository.class));

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(service.plantilla()))) {
            var fila = workbook.getSheet("Grupos").getRow(1);
            assertEquals("2024-1", fila.getCell(0).getStringCellValue());
            assertEquals("Básico 1", fila.getCell(3).getStringCellValue());
            assertEquals("LEGACY-GRUPO-2024", fila.getCell(14).getStringCellValue());
            assertEquals("No", fila.getCell(15).getStringCellValue());
        }
    }

    @Test
    void rechazaLibrosQueNoTienenLasHojasRequeridas() throws IOException {
        ImportacionHistoricaService service = servicioVacio();
        try (var workbook = new XSSFWorkbook()) {
            workbook.createSheet("Alumnos");
            assertThrows(DatosInvalidosException.class, () -> service.importar(workbook));
        }
    }

    @Test
    void importaInscripcionesHistoricasSinCrearCargosNiComprobantes() throws IOException {
        var alumnoRepository = mock(mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository.class);
        var avanceRepository = mock(mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository.class);
        var historialRepository = mock(mx.edu.itch.sigclem.alumnos.repository.HistorialAcademicoRepository.class);
        var carreraRepository = mock(mx.edu.itch.sigclem.catalogos.repository.CarreraRepository.class);
        var grupoRepository = mock(mx.edu.itch.sigclem.grupos.repository.GrupoRepository.class);
        var inscripcionRepository = mock(mx.edu.itch.sigclem.grupos.repository.InscripcionRepository.class);
        var periodoRepository = mock(mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository.class);
        var nivelRepository = mock(mx.edu.itch.sigclem.catalogos.repository.NivelRepository.class);
        var modalidadRepository = mock(mx.edu.itch.sigclem.catalogos.repository.ModalidadRepository.class);
        var aulaRepository = mock(mx.edu.itch.sigclem.catalogos.repository.AulaRepository.class);
        var facilitadorRepository = mock(mx.edu.itch.sigclem.facilitadores.repository.FacilitadorRepository.class);

        var alumno = mock(mx.edu.itch.sigclem.alumnos.domain.Alumno.class);
        when(alumno.getNumeroControl()).thenReturn("A12345678");
        when(alumnoRepository.findConCarreraByNumeroControl("A12345678")).thenReturn(Optional.of(alumno));

        var periodo = mock(Periodo.class);
        when(periodo.getNombre()).thenReturn("2024-1");
        when(periodo.getFechaInicio()).thenReturn(LocalDate.of(2024, 1, 15));
        var nivel = mock(Nivel.class);
        when(nivel.getId()).thenReturn(1);
        when(nivel.getNombre()).thenReturn("Básico 1");
        when(nivel.getNumero()).thenReturn(1);
        var modalidad = mock(Modalidad.class);
        when(modalidad.getId()).thenReturn(1);
        when(modalidad.getNombre()).thenReturn("Sabatino");
        var grupo = mock(Grupo.class);
        when(grupo.getId()).thenReturn(7);
        when(grupo.getPeriodo()).thenReturn(periodo);
        when(grupo.getNivel()).thenReturn(nivel);
        when(grupo.getModalidad()).thenReturn(modalidad);
        when(grupo.getClave()).thenReturn("A");
        when(grupo.getHorarios()).thenReturn(List.of());
        when(grupoRepository.findAllConDatosHistoricos()).thenReturn(List.of(grupo));
        when(nivelRepository.findAllByOrderByNumeroAsc()).thenReturn(List.of(nivel));
        when(modalidadRepository.findAll()).thenReturn(List.of(modalidad));
        when(periodoRepository.findAll()).thenReturn(List.of(periodo));
        when(carreraRepository.findAll()).thenReturn(List.<Carrera>of());

        when(inscripcionRepository.findByAlumnoNumeroControlAndGrupoId("A12345678", 7))
                .thenReturn(Optional.empty());
        when(inscripcionRepository.save(any(Inscripcion.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        ImportacionHistoricaService service = new ImportacionHistoricaService(
                mock(AlumnoService.class), mock(jakarta.validation.Validator.class), alumnoRepository,
                avanceRepository, historialRepository, carreraRepository, grupoRepository,
                inscripcionRepository, periodoRepository, nivelRepository, modalidadRepository,
                aulaRepository, facilitadorRepository);

        try (var workbook = new XSSFWorkbook()) {
            agregarHoja(workbook, "Alumnos",
                    "Número de control", "Nombre(s)", "Apellido paterno", "Apellido materno", "Sexo (H/M)",
                    "Carrera (nombre o clave)", "Semestre (1-12)", "Correo", "Habla lengua materna (Sí/No)");
            var alumnoRow = workbook.getSheet("Alumnos").createRow(1);
            alumnoRow.createCell(0).setCellValue("A12345678");
            agregarHoja(workbook, "Grupos",
                    "Periodo", "Fecha inicio", "Fecha fin", "Nivel", "Modalidad", "Clave de grupo",
                    "Correo del facilitador", "Facilitador", "Aula", "Cupo", "Estatus del acta",
                    "Fecha de cierre", "Cerrada por", "Observación de cierre", "Grupo original (referencia)");
            var grupoRow = workbook.getSheet("Grupos").createRow(1);
            grupoRow.createCell(0).setCellValue("2024-1");
            grupoRow.createCell(3).setCellValue("Básico 1");
            grupoRow.createCell(4).setCellValue("Sabatino");
            grupoRow.createCell(5).setCellValue("A");
            grupoRow.createCell(9).setCellValue(25);
            agregarHoja(workbook, "Inscripciones",
                    "Número de control", "Periodo", "Nivel", "Modalidad", "Clave de grupo",
                    "Oportunidad", "Calificación", "Avance pendiente");
            var inscripcionRow = workbook.getSheet("Inscripciones").createRow(1);
            inscripcionRow.createCell(0).setCellValue("A12345678");
            inscripcionRow.createCell(1).setCellValue("2024-1");
            inscripcionRow.createCell(2).setCellValue("Básico 1");
            inscripcionRow.createCell(3).setCellValue("Sabatino");
            inscripcionRow.createCell(4).setCellValue("A");
            inscripcionRow.createCell(5).setCellValue("01");
            inscripcionRow.createCell(6).setCellValue(90);
            inscripcionRow.createCell(7).setCellValue("No");
            agregarHoja(workbook, "Horarios",
                    "Periodo", "Nivel", "Modalidad", "Clave de grupo", "Día (1-6)", "Hora inicio", "Hora fin");
            agregarHoja(workbook, "Historial_Informativo",
                    "Número de control", "Nivel", "Periodo", "Facilitador", "Grupo de referencia",
                    "Horario", "Aula", "Calificación", "Observación");

            var resultado = service.importar(workbook);

            assertEquals(1, resultado.inscripcionesImportadas());
            assertEquals(1, resultado.gruposImportados());
            var captor = ArgumentCaptor.forClass(Inscripcion.class);
            verify(inscripcionRepository).save(captor.capture());
            assertNull(captor.getValue().getIdAutorizacion());
            assertNull(captor.getValue().getIdComprobante());
        }
    }

    private static void agregarHoja(XSSFWorkbook workbook, String nombre, String... encabezados) {
        var hoja = workbook.createSheet(nombre);
        var fila = hoja.createRow(0);
        for (int i = 0; i < encabezados.length; i++) fila.createCell(i).setCellValue(encabezados[i]);
    }

    @Test
    void adaptaLaPlantillaHistoricaAnteriorAlNuevoFormato() throws IOException {
        ImportacionHistoricaService service = servicioVacio();
        try (var workbook = new XSSFWorkbook()) {
            workbook.createSheet("Alumnos");
            workbook.createSheet("Grupos");
            workbook.createSheet("Inscripciones");
            workbook.createSheet("Intensivo_Historico");
            assertThrows(DatosInvalidosException.class, () -> service.importar(workbook));
        }
    }

    private static ImportacionHistoricaService servicioVacio() {
        return servicioVacio(
                mock(mx.edu.itch.sigclem.grupos.repository.GrupoRepository.class),
                mock(mx.edu.itch.sigclem.grupos.repository.InscripcionRepository.class),
                mock(mx.edu.itch.sigclem.alumnos.repository.HistorialAcademicoRepository.class));
    }

    private static ImportacionHistoricaService servicioVacio(
            mx.edu.itch.sigclem.grupos.repository.GrupoRepository grupoRepository,
            mx.edu.itch.sigclem.grupos.repository.InscripcionRepository inscripcionRepository,
            mx.edu.itch.sigclem.alumnos.repository.HistorialAcademicoRepository historialRepository) {
        return new ImportacionHistoricaService(
                mock(AlumnoService.class),
                mock(jakarta.validation.Validator.class),
                mock(mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository.class),
                mock(mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository.class),
                historialRepository,
                mock(mx.edu.itch.sigclem.catalogos.repository.CarreraRepository.class),
                grupoRepository,
                inscripcionRepository,
                mock(mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository.class),
                mock(mx.edu.itch.sigclem.catalogos.repository.NivelRepository.class),
                mock(mx.edu.itch.sigclem.catalogos.repository.ModalidadRepository.class),
                mock(mx.edu.itch.sigclem.catalogos.repository.AulaRepository.class),
                mock(mx.edu.itch.sigclem.facilitadores.repository.FacilitadorRepository.class));
    }
}
