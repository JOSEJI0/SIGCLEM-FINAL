package mx.edu.itch.sigclem.pruebas.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.dto.RegistroAlumnoRequest;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.alumnos.service.AlumnoService;
import mx.edu.itch.sigclem.catalogos.domain.Aula;
import mx.edu.itch.sigclem.catalogos.domain.Carrera;
import mx.edu.itch.sigclem.catalogos.domain.Modalidad;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.domain.Periodo;
import mx.edu.itch.sigclem.catalogos.repository.AulaRepository;
import mx.edu.itch.sigclem.catalogos.repository.CarreraRepository;
import mx.edu.itch.sigclem.catalogos.repository.ModalidadRepository;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository;
import mx.edu.itch.sigclem.comun.excepcion.ReglaNegocioException;
import mx.edu.itch.sigclem.facilitadores.domain.Facilitador;
import mx.edu.itch.sigclem.facilitadores.dto.FacilitadorRequest;
import mx.edu.itch.sigclem.facilitadores.repository.FacilitadorRepository;
import mx.edu.itch.sigclem.facilitadores.service.FacilitadorService;
import mx.edu.itch.sigclem.grupos.dto.CrearGrupoRequest;
import mx.edu.itch.sigclem.grupos.dto.GrupoDTO;
import mx.edu.itch.sigclem.grupos.dto.HorarioRequest;
import mx.edu.itch.sigclem.grupos.repository.GrupoRepository;
import mx.edu.itch.sigclem.grupos.repository.HorarioRepository;
import mx.edu.itch.sigclem.grupos.service.GrupoService;
import mx.edu.itch.sigclem.pagos.domain.ConceptoPago;
import mx.edu.itch.sigclem.pagos.dto.TarifaRequest;
import mx.edu.itch.sigclem.pagos.repository.ConceptoPagoRepository;
import mx.edu.itch.sigclem.pagos.repository.TarifaPagoRepository;
import mx.edu.itch.sigclem.pagos.service.PagoService;
import mx.edu.itch.sigclem.pruebas.dto.DatosDemoDTO;
import mx.edu.itch.sigclem.pruebas.dto.DatosDemoDTO.CuentaDemo;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

/**
 * Carga un conjunto mínimo de datos para demostrar los flujos completos en el modo pruebas:
 * periodo vigente, aula, facilitador, dos alumnos, tarifas y un grupo de Básico 1.
 * Es idempotente: si un dato ya existe, se reutiliza.
 */
@Service
@RequiredArgsConstructor
public class DatosDemoService {

    public static final String CORREO_FACILITADOR = "facilitador.demo@itch.edu.mx";
    public static final String CONTRASENA_FACILITADOR = "Facilitador2026!";
    public static final String CONTRASENA_ALUMNO = "Alumno2026!";
    private static final String[][] ALUMNOS = {
            { "22520001", "Ana", "López", "Hernández", "ana.lopez@demo.itch.edu.mx" },
            { "22520002", "Luis", "Ramírez", "Torres", "luis.ramirez@demo.itch.edu.mx" },
    };
    private static final String AULA = "Aula Demo 1";
    private static final String CLAVE_GRUPO = "A";

    private final PeriodoRepository periodoRepository;
    private final AulaRepository aulaRepository;
    private final CarreraRepository carreraRepository;
    private final ModalidadRepository modalidadRepository;
    private final NivelRepository nivelRepository;
    private final AlumnoRepository alumnoRepository;
    private final AlumnoService alumnoService;
    private final FacilitadorRepository facilitadorRepository;
    private final FacilitadorService facilitadorService;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ConceptoPagoRepository conceptoRepository;
    private final TarifaPagoRepository tarifaRepository;
    private final PagoService pagoService;
    private final GrupoRepository grupoRepository;
    private final HorarioRepository horarioRepository;
    private final GrupoService grupoService;

    @Transactional
    public DatosDemoDTO cargar() {
        List<String> acciones = new ArrayList<>();
        List<CuentaDemo> cuentas = new ArrayList<>();

        Periodo periodo = periodoVigente(acciones);
        Aula aula = aula(acciones);

        // Facilitador
        if (!facilitadorRepository.existsByCorreoIgnoreCase(CORREO_FACILITADOR)) {
            facilitadorService.registrar(new FacilitadorRequest("María", "Gómez", "Ruiz", CORREO_FACILITADOR,
                    "7470000000", CONTRASENA_FACILITADOR));
            acciones.add("Facilitador creado: " + CORREO_FACILITADOR);
        } else {
            acciones.add("Facilitador existente: " + CORREO_FACILITADOR);
        }
        establecerContrasena(CORREO_FACILITADOR, CONTRASENA_FACILITADOR);
        cuentas.add(new CuentaDemo("FACILITADOR", CORREO_FACILITADOR, CONTRASENA_FACILITADOR));
        Facilitador facilitador = facilitadorRepository.findByCorreoIgnoreCase(CORREO_FACILITADOR)
                .orElseThrow(() -> new IllegalStateException("No se pudo crear el facilitador de demostración."));

        // Alumnos
        Carrera carrera = carreraRepository.findAll(Sort.by("id")).stream().findFirst()
                .orElseThrow(() -> new ReglaNegocioException("PRU-003", "No hay carreras en el catálogo."));
        for (String[] a : ALUMNOS) {
            if (!alumnoRepository.existsById(a[0]) && !usuarioRepository.existsByNombreUsuario(a[0])) {
                // La primera alumna de demostración declara hablar una lengua materna.
                // El servicio crea la cuenta con contraseña = número de control; luego establecerContrasena la ajusta.
                alumnoService.registrar(new RegistroAlumnoRequest(a[0], a[1], a[2], a[3],
                        a == ALUMNOS[0] ? "M" : "H", carrera.getId(), 3, a[4], a == ALUMNOS[0]));
                acciones.add("Alumno creado: " + a[0] + " (" + a[1] + " " + a[2] + ")");
            } else {
                acciones.add("Alumno existente: " + a[0]);
            }
            establecerContrasena(a[0], CONTRASENA_ALUMNO);
            cuentas.add(new CuentaDemo("ALUMNO", a[0], CONTRASENA_ALUMNO));
        }

        // Tarifas
        Nivel basico1 = nivelRepository.findAllByOrderByNumeroAsc().stream().findFirst()
                .orElseThrow(() -> new ReglaNegocioException("PRU-004", "No hay niveles en el catálogo."));
        // Monto general del curso: todos los niveles pagan lo mismo.
        tarifa("Curso de nivel", true, null, new BigDecimal("1200.00"), acciones);
        tarifa("Examen de colocación", false, null, new BigDecimal("500.00"), acciones);
        tarifa("Examen de cuatro habilidades", false, null, new BigDecimal("650.00"), acciones);

        // Grupo de Básico 1
        Integer idGrupo = grupo(periodo, basico1, facilitador, aula, acciones);

        return new DatosDemoDTO(acciones, periodo.getId(), idGrupo, cuentas);
    }

    private Periodo periodoVigente(List<String> acciones) {
        LocalDate hoy = LocalDate.now();
        var vigente = periodoRepository
                .findFirstByFechaInicioLessThanEqualAndFechaFinGreaterThanEqualOrderByFechaInicioDesc(hoy, hoy);
        if (vigente.isPresent()) {
            acciones.add("Periodo vigente existente: " + vigente.get().getNombre());
            return vigente.get();
        }
        String nombre = "DEMO " + hoy.format(DateTimeFormatter.BASIC_ISO_DATE);
        Periodo periodo = new Periodo();
        periodo.setNombre(nombre);
        periodo.setFechaInicio(hoy.minusDays(15));
        periodo.setFechaFin(hoy.plusDays(120));
        Periodo creado = periodoRepository.save(periodo);
        acciones.add("Periodo creado: " + nombre + " (" + creado.getFechaInicio() + " a " + creado.getFechaFin() + ")");
        return creado;
    }

    private Aula aula(List<String> acciones) {
        return aulaRepository.findAllByOrderByNombreAsc().stream()
                .filter(a -> AULA.equalsIgnoreCase(a.getNombre()))
                .findFirst()
                .map(a -> {
                    acciones.add("Aula existente: " + AULA);
                    return a;
                })
                .orElseGet(() -> {
                    Aula nueva = new Aula();
                    nueva.setNombre(AULA);
                    acciones.add("Aula creada: " + AULA);
                    return aulaRepository.save(nueva);
                });
    }

    private void tarifa(String concepto, boolean requiereNivel, Nivel nivel, BigDecimal monto, List<String> acciones) {
        if (conceptoRepository.findByNombreAndActivoTrue(concepto).isEmpty()) {
            if (conceptoRepository.existsByNombreIgnoreCase(concepto)) {
                acciones.add("Concepto inactivo, no se crea tarifa: " + concepto);
                return;
            }
            ConceptoPago nuevo = new ConceptoPago();
            nuevo.setNombre(concepto);
            nuevo.setRequiereNivel(requiereNivel);
            nuevo.setActivo(true);
            conceptoRepository.save(nuevo);
            acciones.add("Concepto de pago creado: " + concepto);
        }
        boolean existe = nivel == null
                ? tarifaRepository.existsByConceptoAndNivelIsNullAndActivaTrue(concepto)
                : tarifaRepository.existsByConceptoAndNivelIdAndActivaTrue(concepto, nivel.getId());
        if (existe) {
            acciones.add("Tarifa existente: " + concepto + (nivel == null ? "" : " - " + nivel.getNombre()));
            return;
        }
        pagoService.crearTarifa(new TarifaRequest(concepto, nivel == null ? null : nivel.getId(), monto));
        acciones.add("Tarifa creada: " + concepto + (nivel == null ? "" : " - " + nivel.getNombre()) + " $" + monto);
    }

    private Integer grupo(Periodo periodo, Nivel nivel, Facilitador facilitador, Aula aula, List<String> acciones) {
        Modalidad modalidad = modalidadRepository.findAllByOrderByNombreAsc().stream()
                .filter(m -> "Entre semana".equalsIgnoreCase(m.getNombre()))
                .findFirst()
                .or(() -> modalidadRepository.findAllByOrderByNombreAsc().stream().findFirst())
                .orElseThrow(() -> new ReglaNegocioException("PRU-005", "No hay modalidades en el catálogo."));
        var existente = grupoRepository.findByPeriodoId(periodo.getId()).stream()
                .filter(g -> g.getNivel().getId().equals(nivel.getId())
                        && g.getModalidad().getId().equals(modalidad.getId())
                        && CLAVE_GRUPO.equals(g.getClave()))
                .findFirst();
        if (existente.isPresent()) {
            acciones.add("Grupo existente: " + nivel.getNombre() + " " + CLAVE_GRUPO + " (id " + existente.get().getId() + ")");
            return existente.get().getId();
        }
        // Busca un horario libre (lunes y miércoles, 2 horas) para el facilitador y el aula.
        for (int hora = 10; hora <= 15; hora++) {
            LocalTime inicio = LocalTime.of(hora, 0);
            LocalTime fin = inicio.plusHours(2);
            boolean libre = horarioRepository.buscarTraslape(1, inicio, fin, periodo.getId(), facilitador.getId(),
                    aula.getId(), -1).isEmpty()
                    && horarioRepository.buscarTraslape(3, inicio, fin, periodo.getId(), facilitador.getId(),
                            aula.getId(), -1).isEmpty();
            if (libre) {
                GrupoDTO creado = grupoService.abrir(new CrearGrupoRequest(CLAVE_GRUPO, nivel.getId(), periodo.getId(),
                        modalidad.getId(), facilitador.getId(), aula.getId(), 30,
                        List.of(new HorarioRequest(1, inicio, fin), new HorarioRequest(3, inicio, fin))));
                acciones.add("Grupo creado: " + nivel.getNombre() + " " + CLAVE_GRUPO + " (id " + creado.id()
                        + "), lunes y miércoles " + inicio + "-" + fin);
                return creado.id();
            }
        }
        acciones.add("No se creó el grupo: no hay horario libre para el facilitador o el aula de demostración.");
        return null;
    }

    private void establecerContrasena(String usuario, String contrasena) {
        usuarioRepository.findByNombreUsuario(usuario).ifPresent(u -> {
            u.setContrasenaHash(passwordEncoder.encode(contrasena));
            usuarioRepository.save(u);
        });
    }
}
