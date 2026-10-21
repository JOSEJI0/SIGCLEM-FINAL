package mx.edu.itch.sigclem.reportes.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.domain.Alumno;
import mx.edu.itch.sigclem.alumnos.domain.AvanceNivel;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.constancias.repository.ConstanciaRepository;
import mx.edu.itch.sigclem.diagnosticos.repository.EvaluacionDiagnosticaRepository;
import mx.edu.itch.sigclem.examenes.repository.ExamenRepository;
import mx.edu.itch.sigclem.facilitadores.repository.FacilitadorRepository;
import mx.edu.itch.sigclem.lenguasmaternas.repository.RegistroLenguaMaternaRepository;
import mx.edu.itch.sigclem.pagos.domain.AutorizacionPago;
import mx.edu.itch.sigclem.pagos.repository.AutorizacionPagoRepository;
import mx.edu.itch.sigclem.pagos.repository.ComprobantePagoRepository;
import mx.edu.itch.sigclem.reportes.dto.IndicadoresDTO;
import mx.edu.itch.sigclem.reportes.dto.IndicadoresDTO.Conteo;
import mx.edu.itch.sigclem.reportes.dto.ReporteAcademicoDTO;

/** Indicadores generales del sistema para los paneles de coordinación y de la jefa del DGTV (RF-17, RF-18). */
@Service
@RequiredArgsConstructor
public class IndicadoresService {

    private final ReporteAcademicoService reporteService;
    private final AlumnoRepository alumnoRepository;
    private final AvanceNivelRepository avanceRepository;
    private final NivelRepository nivelRepository;
    private final FacilitadorRepository facilitadorRepository;
    private final AutorizacionPagoRepository autorizacionRepository;
    private final ComprobantePagoRepository comprobanteRepository;
    private final ExamenRepository examenRepository;
    private final EvaluacionDiagnosticaRepository diagnosticoRepository;
    private final ConstanciaRepository constanciaRepository;
    private final RegistroLenguaMaternaRepository lenguaRepository;

    @Transactional(readOnly = true)
    public IndicadoresDTO obtener() {
        ReporteAcademicoDTO reporte;
        try {
            reporte = reporteService.obtenerReporte(null);
        } catch (RecursoNoEncontradoException sinPeriodos) {
            reporte = null;
        }

        List<Alumno> alumnos = alumnoRepository.findAll();
        List<Nivel> niveles = nivelRepository.findAllByOrderByNumeroAsc();
        Map<String, List<AvanceNivel>> avancePorAlumno = avanceRepository.findAll().stream()
                .collect(Collectors.groupingBy(a -> a.getId().getNumeroControl()));

        // Alumnos por nivel actual (primer nivel aún no cubierto), en el orden de los niveles.
        Map<String, Long> porNivel = new LinkedHashMap<>();
        niveles.forEach(n -> porNivel.put(n.getNombre(), 0L));
        porNivel.put("Idioma acreditado", 0L);
        for (Alumno a : alumnos) {
            Map<Integer, AvanceNivel> avance = avancePorAlumno.getOrDefault(a.getNumeroControl(), List.of()).stream()
                    .collect(Collectors.toMap(x -> x.getId().getIdNivel(), Function.identity()));
            String actual = niveles.stream()
                    .filter(n -> avance.get(n.getId()) == null || !avance.get(n.getId()).getEstatus().cubreNivel())
                    .map(Nivel::getNombre).findFirst().orElse("Idioma acreditado");
            porNivel.merge(actual, 1L, Long::sum);
        }
        Map<String, Long> porCarrera = new TreeMap<>(alumnos.stream()
                .collect(Collectors.groupingBy(a -> a.getCarrera().getNombre(), Collectors.counting())));

        List<AutorizacionPago> cargos = autorizacionRepository.findAll();
        BigDecimal porCobrar = cargos.stream().map(AutorizacionPago::getSaldoPendiente).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal cobrado = cargos.stream().map(c -> c.getMontoEsperado().subtract(c.getSaldoPendiente()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new IndicadoresDTO(
                reporte == null ? null : reporte.periodo().nombre(),
                alumnos.size(),
                facilitadorRepository.findAll().stream().filter(f -> f.getUsuario().isActivo()).count(),
                reporte == null ? 0 : reporte.grupos(),
                reporte == null ? 0 : reporte.alumnosInscritos(),
                reporte == null ? 0 : reporte.acreditados(),
                reporte == null ? 0 : reporte.noAcreditados(),
                conteos(porNivel, false),
                conteos(porCarrera, true),
                cargos.stream().filter(c -> "Pendiente".equals(c.getEstatus())).count(),
                cargos.stream().filter(c -> "Parcial".equals(c.getEstatus())).count(),
                cargos.stream().filter(c -> "Liquidada".equals(c.getEstatus())).count(),
                porCobrar,
                cobrado,
                comprobanteRepository.findByEstatusOrderByFechaCargaAsc("Pendiente").size(),
                examenRepository.findByNivelAsignadoIsNullAndOcultoFalseOrderByFechaHoraAplicacionAsc().size(),
                diagnosticoRepository.findAll().stream().filter(d -> "Solicitada".equals(d.getEstatus())).count(),
                constanciaRepository.findAll().stream().filter(c -> "Solicitada".equals(c.getEstatus())).count(),
                constanciaRepository.findAll().stream().filter(c -> "Emitida".equals(c.getEstatus())).count(),
                lenguaRepository.findAll().stream().filter(r -> "Pendiente".equals(r.getEstatus())).count());
    }

    private static List<Conteo> conteos(Map<String, Long> mapa, boolean ordenarPorValor) {
        List<Conteo> lista = new ArrayList<>(mapa.entrySet().stream().map(e -> new Conteo(e.getKey(), e.getValue())).toList());
        if (ordenarPorValor) {
            lista.sort(Comparator.comparingLong(Conteo::valor).reversed());
        }
        return lista;
    }
}
