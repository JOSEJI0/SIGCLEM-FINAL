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
import java.time.LocalTime;
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
        var alumnoConInscripcion = mock(mx.edu.itch.sigclem.alumnos.domain.Alumno.class);
        when(alumnoConInscripcion.getNumeroControl()).thenReturn("B12345678");
        when(alumnoRepository.findConCarreraByNumeroControl("B12345678"))
                .thenReturn(Optional.of(alumnoConInscripcion));

        var periodo = mock(Periodo.class);
        when(periodo.getNombre()).thenReturn("2024-1");
        when(periodo.getFechaInicio()).thenReturn(LocalDate.of(2024, 1, 15));
        when(periodo.getId()).thenReturn(1);
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
        var horariosExistentes = new java.util.ArrayList<mx.edu.itch.sigclem.grupos.domain.Horario>();
        var horarioExistente = new mx.edu.itch.sigclem.grupos.domain.Horario();
        horarioExistente.setDiaSemana(1);
        horarioExistente.setHoraInicio(LocalTime.of(10, 0));
        horarioExistente.setHoraFin(LocalTime.of(11, 0));
        horariosExistentes.add(horarioExistente);
        when(grupo.getHorarios()).thenReturn(horariosExistentes);
        when(grupo.getCupo()).thenReturn(25);
        when(grupo.getReferenciaHistorica()).thenReturn(null);
        when(grupoRepository.findAllConDatosHistoricos()).thenReturn(List.of(grupo));
        when(nivelRepository.findAllByOrderByNumeroAsc()).thenReturn(List.of(nivel));
        when(modalidadRepository.findAll()).thenReturn(List.of(modalidad));
        when(periodoRepository.findAll()).thenReturn(List.of(periodo));
        when(carreraRepository.findAll()).thenReturn(List.<Carrera>of());

        when(inscripcionRepository.findByAlumnoNumeroControlAndGrupoId("A12345678", 7))
                .thenReturn(Optional.empty());
        var inscripcionExistente = new Inscripcion();
        inscripcionExistente.setId(8);
        inscripcionExistente.setOportunidad("R1");
        inscripcionExistente.setCalificacionFinal(78);
        inscripcionExistente.setAvancePendiente(true);
        when(inscripcionRepository.findByAlumnoNumeroControlAndGrupoId("B12345678", 7))
                .thenReturn(Optional.of(inscripcionExistente));
        when(inscripcionRepository.save(any(Inscripcion.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        ImportacionHistoricaService service = new ImportacionHistoricaService(
                mock(AlumnoService.class), mock(jakarta.validation.Validator.class), alumnoRepository,
                avanceRepository, historialRepository, carreraRepository, grupoRepository,
                mock(mx.edu.itch.sigclem.grupos.repository.HorarioRepository.class),
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
            grupoRow.createCell(14).setCellValue("REF-HISTORICO-01");
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
            var inscripcionExistenteRow = workbook.getSheet("Inscripciones").createRow(2);
            inscripcionExistenteRow.createCell(0).setCellValue("B12345678");
            inscripcionExistenteRow.createCell(1).setCellValue("2024-1");
            inscripcionExistenteRow.createCell(2).setCellValue("Básico 1");
            inscripcionExistenteRow.createCell(3).setCellValue("Sabatino");
            inscripcionExistenteRow.createCell(4).setCellValue("A");
            agregarHoja(workbook, "Horarios",
                    "Periodo", "Nivel", "Modalidad", "Clave de grupo", "Día (1-6)", "Hora inicio", "Hora fin");
            var horarioRow = workbook.getSheet("Horarios").createRow(1);
            horarioRow.createCell(0).setCellValue("2024-1");
            horarioRow.createCell(1).setCellValue("Básico 1");
            horarioRow.createCell(2).setCellValue("Sabatino");
            horarioRow.createCell(3).setCellValue("A");
            horarioRow.createCell(4).setCellValue(2);
            horarioRow.createCell(5).setCellValue("17:00");
            horarioRow.createCell(6).setCellValue("18:00");
            agregarHoja(workbook, "Historial_Informativo",
                    "Número de control", "Nivel", "Periodo", "Facilitador", "Grupo de referencia",
                    "Horario", "Aula", "Calificación", "Observación");
            var historialRow = workbook.getSheet("Historial_Informativo").createRow(1);
            historialRow.createCell(0).setCellValue("A12345678");
            historialRow.createCell(1).setCellValue("Básico 1");
            historialRow.createCell(3).setCellValue("Facilitador Ejemplo");

            var resultado = service.importar(workbook);

            assertEquals(2, resultado.inscripcionesImportadas());
            assertEquals(1, resultado.gruposImportados());
            assertEquals(1, resultado.horariosImportados());
            assertEquals(6, resultado.filas());
            assertEquals(0, resultado.errores().size());
            verify(grupoRepository, org.mockito.Mockito.times(2)).save(any(Grupo.class));
            verify(grupo).setReferenciaHistorica("REF-HISTORICO-01");
            var captor = ArgumentCaptor.forClass(Inscripcion.class);
            verify(inscripcionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
            assertNull(captor.getAllValues().getFirst().getIdAutorizacion());
            assertNull(captor.getAllValues().getFirst().getIdComprobante());
            assertEquals("R1", inscripcionExistente.getOportunidad());
            assertEquals(78, inscripcionExistente.getCalificacionFinal());
            assertEquals(true, inscripcionExistente.isAvancePendiente());
            var historialCaptor = ArgumentCaptor.forClass(
                    mx.edu.itch.sigclem.alumnos.domain.HistorialAcademico.class);
            verify(historialRepository).save(historialCaptor.capture());
            assertEquals("", historialCaptor.getValue().getPeriodo());
        }
    }

    @Test
    void importaGrupoHistoricoAunqueElCupoYLosDatosDeCierreEstanVacios() throws IOException {
        var grupoRepository = mock(mx.edu.itch.sigclem.grupos.repository.GrupoRepository.class);
        var periodoRepository = mock(mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository.class);
        var nivelRepository = mock(mx.edu.itch.sigclem.catalogos.repository.NivelRepository.class);
        var modalidadRepository = mock(mx.edu.itch.sigclem.catalogos.repository.ModalidadRepository.class);
        var aulaRepository = mock(mx.edu.itch.sigclem.catalogos.repository.AulaRepository.class);
        var facilitadorRepository = mock(mx.edu.itch.sigclem.facilitadores.repository.FacilitadorRepository.class);

        var periodo = new Periodo();
        periodo.setId(1);
        periodo.setNombre("2024-1");
        periodo.setFechaInicio(LocalDate.of(2024, 1, 15));
        periodo.setFechaFin(LocalDate.of(2024, 6, 15));
        var nivel = mock(Nivel.class);
        when(nivel.getId()).thenReturn(1);
        when(nivel.getNombre()).thenReturn("Básico 1");
        when(nivel.getClave()).thenReturn("B1");
        when(nivel.getNumero()).thenReturn(1);
        var modalidad = new Modalidad();
        modalidad.setId(1);
        modalidad.setNombre("Sabatino");
        var aula = new Aula();
        aula.setId(1);
        aula.setNombre("Aula 1");
        var facilitador = new Facilitador();
        facilitador.setId(1);
        facilitador.setNombre("Facilitador");
        facilitador.setApellidoPaterno("Ejemplo");
        facilitador.setCorreo("facilitador@example.edu.mx");

        when(grupoRepository.findAllConDatosHistoricos()).thenReturn(List.of());
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(periodoRepository.findAll()).thenReturn(List.of(periodo));
        when(nivelRepository.findAllByOrderByNumeroAsc()).thenReturn(List.of(nivel));
        when(modalidadRepository.findAll()).thenReturn(List.of(modalidad));
        when(aulaRepository.findAll()).thenReturn(List.of(aula));
        when(facilitadorRepository.findAll()).thenReturn(List.of(facilitador));

        var service = new ImportacionHistoricaService(
                mock(AlumnoService.class), mock(jakarta.validation.Validator.class),
                mock(mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository.class),
                mock(mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository.class),
                mock(mx.edu.itch.sigclem.alumnos.repository.HistorialAcademicoRepository.class),
                mock(mx.edu.itch.sigclem.catalogos.repository.CarreraRepository.class),
                grupoRepository,
                mock(mx.edu.itch.sigclem.grupos.repository.HorarioRepository.class),
                mock(mx.edu.itch.sigclem.grupos.repository.InscripcionRepository.class),
                periodoRepository, nivelRepository, modalidadRepository, aulaRepository, facilitadorRepository);

        try (var workbook = new XSSFWorkbook()) {
            agregarHoja(workbook, "Alumnos",
                    "Número de control", "Nombre(s)", "Apellido paterno", "Apellido materno", "Sexo (H/M)",
                    "Carrera (nombre o clave)", "Semestre (1-12)", "Correo", "Habla lengua materna (Sí/No)");
            var grupos = workbook.createSheet("Grupos");
            var encabezados = grupos.createRow(0);
            String[] nombres = {
                    "Periodo", "Fecha inicio", "Fecha fin", "Nivel", "Modalidad", "Clave de grupo",
                    "Correo del facilitador", "Facilitador", "Aula", "Cupo", "Estatus del acta",
                    "Fecha de cierre", "Cerrada por", "Observación de cierre", "Grupo original (referencia)",
                    "Curso Intensivo (Sí/No)"
            };
            for (int i = 0; i < nombres.length; i++) encabezados.createCell(i).setCellValue(nombres[i]);
            var fila = grupos.createRow(1);
            fila.createCell(0).setCellValue("2024-1");
            fila.createCell(3).setCellValue("Básico 1");
            fila.createCell(4).setCellValue("Sabatino");
            fila.createCell(5).setCellValue("A");
            fila.createCell(7).setCellValue("Facilitador Ejemplo");
            fila.createCell(8).setCellValue("Aula 1");

            agregarHoja(workbook, "Inscripciones",
                    "Número de control", "Periodo", "Nivel", "Modalidad", "Clave de grupo",
                    "Oportunidad", "Calificación", "Avance pendiente", "Nivel acreditado (Intensivo)");
            agregarHoja(workbook, "Horarios",
                    "Periodo", "Nivel", "Modalidad", "Clave de grupo", "Día (1-6)", "Hora inicio", "Hora fin");
            agregarHoja(workbook, "Historial_Informativo",
                    "Número de control", "Nivel", "Periodo", "Facilitador", "Grupo de referencia",
                    "Horario", "Aula", "Calificación", "Observación");

            var resultado = service.importar(workbook);

            assertEquals(1, resultado.gruposImportados());
            assertEquals(0, resultado.errores().size());
            var grupoCaptor = ArgumentCaptor.forClass(Grupo.class);
            verify(grupoRepository).save(grupoCaptor.capture());
            assertNull(grupoCaptor.getValue().getCupo());
            assertNull(grupoCaptor.getValue().getCerradaPor());
            assertNull(grupoCaptor.getValue().getObservacionCierre());
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

    @Test
    void completaSoloCamposVaciosYAgregaHorariosSinModificarDatosExistentes() throws IOException {
        var alumnoRepository = mock(mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository.class);
        var grupoRepository = mock(mx.edu.itch.sigclem.grupos.repository.GrupoRepository.class);
        var horarioRepository = mock(mx.edu.itch.sigclem.grupos.repository.HorarioRepository.class);
        var nivelRepository = mock(mx.edu.itch.sigclem.catalogos.repository.NivelRepository.class);
        var modalidadRepository = mock(mx.edu.itch.sigclem.catalogos.repository.ModalidadRepository.class);
        var aulaRepository = mock(mx.edu.itch.sigclem.catalogos.repository.AulaRepository.class);
        var facilitadorRepository = mock(mx.edu.itch.sigclem.facilitadores.repository.FacilitadorRepository.class);

        var alumnoCompletable = new mx.edu.itch.sigclem.alumnos.domain.Alumno();
        alumnoCompletable.setNumeroControl("A22222222");
        var alumnoConDatos = new mx.edu.itch.sigclem.alumnos.domain.Alumno();
        alumnoConDatos.setNumeroControl("A11111111");
        alumnoConDatos.setApellidoMaterno("Apellido existente");
        alumnoConDatos.setSexo("H");
        alumnoConDatos.setCorreo("existente@example.edu.mx");
        when(alumnoRepository.findConCarreraByNumeroControl("A22222222")).thenReturn(Optional.of(alumnoCompletable));
        when(alumnoRepository.findConCarreraByNumeroControl("A11111111")).thenReturn(Optional.of(alumnoConDatos));
        when(alumnoRepository.save(any(mx.edu.itch.sigclem.alumnos.domain.Alumno.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));

        var periodo = new Periodo();
        periodo.setId(1);
        periodo.setNombre("2024-1");
        var nivel = mock(Nivel.class);
        when(nivel.getId()).thenReturn(1);
        when(nivel.getNombre()).thenReturn("Básico 1");
        when(nivel.getClave()).thenReturn("B1");
        when(nivel.getNumero()).thenReturn(1);
        when(nivelRepository.findAllByOrderByNumeroAsc()).thenReturn(List.of(nivel));
        var modalidad = new Modalidad();
        modalidad.setId(1);
        modalidad.setNombre("Sabatino");
        when(modalidadRepository.findAll()).thenReturn(List.of(modalidad));
        var aula = new Aula();
        aula.setId(3);
        aula.setNombre("Aula 1");
        when(aulaRepository.findAll()).thenReturn(List.of(aula));
        var facilitador = new Facilitador();
        facilitador.setNombre("Facilitador");
        facilitador.setApellidoPaterno("Prueba");
        facilitador.setId(2);
        facilitador.setCorreo("");
        when(facilitadorRepository.findAll()).thenReturn(List.of(facilitador));
        when(facilitadorRepository.findByCorreoIgnoreCase("facilitador@example.edu.mx")).thenReturn(Optional.empty());

        var grupo = new Grupo();
        grupo.setId(7);
        grupo.setClave("A");
        grupo.setPeriodo(periodo);
        grupo.setNivel(nivel);
        grupo.setModalidad(modalidad);
        grupo.setFacilitador(facilitador);
        grupo.setAula(aula);
        grupo.setCupo(20);
        grupo.setReferenciaHistorica("Referencia existente");
        grupo.setCerradaPor("Coordinación");
        var horarioExistente = new mx.edu.itch.sigclem.grupos.domain.Horario();
        horarioExistente.setDiaSemana(1);
        horarioExistente.setHoraInicio(LocalTime.of(10, 0));
        horarioExistente.setHoraFin(LocalTime.of(11, 0));
        grupo.agregarHorario(horarioExistente);
        when(grupoRepository.findAllConDatosHistoricos()).thenReturn(List.of(grupo));
        when(horarioRepository.buscarTraslape(2, LocalTime.of(11, 0), LocalTime.of(12, 0), 1, 2, 3, true, 7))
                .thenReturn(List.of());
        when(facilitadorRepository.save(any(Facilitador.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(grupoRepository.save(any(Grupo.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        var service = new ImportacionHistoricaService(
                mock(AlumnoService.class), mock(jakarta.validation.Validator.class), alumnoRepository,
                mock(mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository.class),
                mock(mx.edu.itch.sigclem.alumnos.repository.HistorialAcademicoRepository.class),
                mock(mx.edu.itch.sigclem.catalogos.repository.CarreraRepository.class),
                grupoRepository,
                horarioRepository,
                mock(mx.edu.itch.sigclem.grupos.repository.InscripcionRepository.class),
                mock(mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository.class),
                nivelRepository, modalidadRepository, aulaRepository, facilitadorRepository);

        try (var workbook = new XSSFWorkbook()) {
            agregarHoja(workbook, "Alumnos",
                    "Número de control", "Nombre(s)", "Apellido paterno", "Apellido materno", "Sexo (H/M)",
                    "Carrera (nombre o clave)", "Semestre (1-12)", "Correo", "Habla lengua materna (Sí/No)");
            var alumnos = workbook.getSheet("Alumnos");
            var alumnoVacio = alumnos.createRow(1);
            alumnoVacio.createCell(0).setCellValue("A22222222");
            alumnoVacio.createCell(3).setCellValue("Materno nuevo");
            alumnoVacio.createCell(4).setCellValue("M");
            alumnoVacio.createCell(7).setCellValue("nuevo@example.edu.mx");
            var alumnoConDatosRow = alumnos.createRow(2);
            alumnoConDatosRow.createCell(0).setCellValue("A11111111");

            var grupos = workbook.createSheet("Grupos");
            var encabezadosGrupos = grupos.createRow(0);
            String[] columnasGrupos = {
                    "Periodo", "Fecha inicio", "Fecha fin", "Nivel", "Modalidad", "Clave de grupo",
                    "Correo del facilitador", "Facilitador", "Aula", "Cupo", "Estatus del acta",
                    "Fecha de cierre", "Cerrada por", "Observación de cierre", "Grupo original (referencia)"
            };
            for (int i = 0; i < columnasGrupos.length; i++) encabezadosGrupos.createCell(i).setCellValue(columnasGrupos[i]);
            var grupoRow = grupos.createRow(1);
            grupoRow.createCell(0).setCellValue("2024-1");
            grupoRow.createCell(3).setCellValue("Básico 1");
            grupoRow.createCell(4).setCellValue("Sabatino");
            grupoRow.createCell(5).setCellValue("A");
            grupoRow.createCell(6).setCellValue("facilitador@example.edu.mx");
            grupoRow.createCell(7).setCellValue("Facilitador Prueba");
            grupoRow.createCell(8).setCellValue("Aula 1");
            grupoRow.createCell(9).setCellValue(15);
            grupoRow.createCell(11).setCellValue("2024-06-20");
            grupoRow.createCell(13).setCellValue("Observación nueva");
            grupoRow.createCell(14).setCellValue("Otra referencia");

            var horarios = workbook.createSheet("Horarios");
            var encabezadosHorarios = horarios.createRow(0);
            String[] columnasHorarios = {
                    "Periodo", "Nivel", "Modalidad", "Clave de grupo", "Día (1-6)", "Hora inicio", "Hora fin"
            };
            for (int i = 0; i < columnasHorarios.length; i++) {
                encabezadosHorarios.createCell(i).setCellValue(columnasHorarios[i]);
            }
            var horarioNuevo = horarios.createRow(1);
            horarioNuevo.createCell(0).setCellValue("2024-1");
            horarioNuevo.createCell(1).setCellValue("Básico 1");
            horarioNuevo.createCell(2).setCellValue("Sabatino");
            horarioNuevo.createCell(3).setCellValue("A");
            horarioNuevo.createCell(4).setCellValue(2);
            horarioNuevo.createCell(5).setCellValue("11:00");
            horarioNuevo.createCell(6).setCellValue("12:00");
            var horarioDuplicado = horarios.createRow(2);
            horarioDuplicado.createCell(0).setCellValue("2024-1");
            horarioDuplicado.createCell(1).setCellValue("Básico 1");
            horarioDuplicado.createCell(2).setCellValue("Sabatino");
            horarioDuplicado.createCell(3).setCellValue("A");
            horarioDuplicado.createCell(4).setCellValue(1);
            horarioDuplicado.createCell(5).setCellValue("10:00");
            horarioDuplicado.createCell(6).setCellValue("11:00");

            var resultado = service.completarExistentes(workbook);

            assertEquals(5, resultado.filasProcesadas());
            assertEquals(7, resultado.camposCompletados());
            assertEquals(1, resultado.alumnosActualizados());
            assertEquals(1, resultado.facilitadoresActualizados());
            assertEquals(1, resultado.gruposActualizados());
            assertEquals(1, resultado.horariosAgregados());
            assertEquals(0, resultado.errores().size());
            assertEquals("Materno nuevo", alumnoCompletable.getApellidoMaterno());
            assertEquals("M", alumnoCompletable.getSexo());
            assertEquals("nuevo@example.edu.mx", alumnoCompletable.getCorreo());
            assertEquals("Apellido existente", alumnoConDatos.getApellidoMaterno());
            assertEquals("existente@example.edu.mx", alumnoConDatos.getCorreo());
            assertEquals("facilitador@example.edu.mx", facilitador.getCorreo());
            assertEquals(20, grupo.getCupo());
            assertEquals("Referencia existente", grupo.getReferenciaHistorica());
            assertEquals(LocalDate.of(2024, 6, 20), grupo.getFechaCierreActa());
            assertEquals("Observación nueva", grupo.getObservacionCierre());
            assertEquals(2, grupo.getHorarios().size());
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
                mock(mx.edu.itch.sigclem.grupos.repository.HorarioRepository.class),
                inscripcionRepository,
                mock(mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository.class),
                mock(mx.edu.itch.sigclem.catalogos.repository.NivelRepository.class),
                mock(mx.edu.itch.sigclem.catalogos.repository.ModalidadRepository.class),
                mock(mx.edu.itch.sigclem.catalogos.repository.AulaRepository.class),
                mock(mx.edu.itch.sigclem.facilitadores.repository.FacilitadorRepository.class));
    }
}
