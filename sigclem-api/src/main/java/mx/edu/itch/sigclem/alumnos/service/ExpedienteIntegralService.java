package mx.edu.itch.sigclem.alumnos.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.dto.ExpedienteDTO;
import mx.edu.itch.sigclem.alumnos.dto.ExpedienteIntegralDTO;
import mx.edu.itch.sigclem.boletas.service.BoletaService;
import mx.edu.itch.sigclem.constancias.service.ConstanciaService;
import mx.edu.itch.sigclem.diagnosticos.service.EvaluacionDiagnosticaService;
import mx.edu.itch.sigclem.examenes.service.ExamenService;
import mx.edu.itch.sigclem.lenguasmaternas.service.LenguaMaternaService;
import mx.edu.itch.sigclem.pagos.service.PagoService;

/**
 * Reúne en una sola consulta el expediente del alumno: avance, historial de cursos, cargos y pagos, exámenes,
 * diagnósticos, constancias y lengua materna. La cuenta de un alumno es su número de control, por eso se reutilizan
 * las consultas "propias" de cada módulo.
 */
@Service
@RequiredArgsConstructor
public class ExpedienteIntegralService {

    private final AlumnoService alumnoService;
    private final BoletaService boletaService;
    private final PagoService pagoService;
    private final ExamenService examenService;
    private final EvaluacionDiagnosticaService diagnosticoService;
    private final ConstanciaService constanciaService;
    private final LenguaMaternaService lenguaService;

    @Transactional(readOnly = true)
    public ExpedienteIntegralDTO obtener(String numeroControl) {
        ExpedienteDTO expediente = alumnoService.expediente(numeroControl);
        String cuenta = expediente.numeroControl();
        return new ExpedienteIntegralDTO(
                expediente,
                boletaService.obtenerBoletas(cuenta),
                pagoService.listarCargos(cuenta, null, true),
                examenService.listar(cuenta, null, null),
                diagnosticoService.listarPropias(cuenta),
                constanciaService.listarPropias(cuenta),
                lenguaService.listarPropios(cuenta));
    }
}
