package mx.edu.itch.sigclem.alumnos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.catalogos.repository.AulaRepository;
import mx.edu.itch.sigclem.catalogos.repository.CarreraRepository;
import mx.edu.itch.sigclem.catalogos.repository.ModalidadRepository;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository;
import mx.edu.itch.sigclem.facilitadores.repository.FacilitadorRepository;
import mx.edu.itch.sigclem.facilitadores.service.FacilitadorService;
import mx.edu.itch.sigclem.grupos.repository.GrupoRepository;
import mx.edu.itch.sigclem.grupos.service.GrupoService;

class ImportacionOperativaServiceTest {

    @Test
    void plantillaIncluyeSoloHojasDeCargaOperativa() throws IOException {
        var servicio = new ImportacionOperativaService(
                mock(jakarta.validation.Validator.class),
                mock(AlumnoService.class),
                mock(AlumnoRepository.class),
                mock(CarreraRepository.class),
                mock(NivelRepository.class),
                mock(ModalidadRepository.class),
                mock(AulaRepository.class),
                mock(PeriodoRepository.class),
                mock(FacilitadorRepository.class),
                mock(FacilitadorService.class),
                mock(GrupoRepository.class),
                mock(GrupoService.class));

        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(servicio.plantilla()))) {
            assertEquals(5, libro.getNumberOfSheets());
            assertEquals("Alumnos", libro.getSheetAt(1).getSheetName());
            assertEquals("Facilitadores", libro.getSheetAt(2).getSheetName());
            assertEquals("Grupos", libro.getSheetAt(3).getSheetName());
            assertEquals("Horarios", libro.getSheetAt(4).getSheetName());
            assertEquals("Correo del facilitador",
                    libro.getSheet("Grupos").getRow(0).getCell(6).getStringCellValue());
            assertNull(libro.getSheet("Inscripciones"));
        }
    }
}
