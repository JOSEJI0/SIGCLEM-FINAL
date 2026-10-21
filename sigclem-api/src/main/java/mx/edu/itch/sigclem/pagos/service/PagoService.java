package mx.edu.itch.sigclem.pagos.service;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.domain.AvanceNivel;
import mx.edu.itch.sigclem.alumnos.domain.EstatusAvance;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.alumnos.repository.AvanceNivelRepository;
import mx.edu.itch.sigclem.catalogos.domain.Nivel;
import mx.edu.itch.sigclem.catalogos.domain.Periodo;
import mx.edu.itch.sigclem.catalogos.repository.NivelRepository;
import mx.edu.itch.sigclem.catalogos.repository.PeriodoRepository;
import mx.edu.itch.sigclem.comun.excepcion.ConflictoException;
import mx.edu.itch.sigclem.comun.excepcion.DatosInvalidosException;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.comun.excepcion.ReglaNegocioException;
import mx.edu.itch.sigclem.grupos.domain.Grupo;
import mx.edu.itch.sigclem.grupos.domain.Inscripcion;
import mx.edu.itch.sigclem.grupos.repository.GrupoRepository;
import mx.edu.itch.sigclem.grupos.repository.InscripcionRepository;
import mx.edu.itch.sigclem.pagos.domain.AutorizacionPago;
import mx.edu.itch.sigclem.pagos.domain.ComprobantePago;
import mx.edu.itch.sigclem.pagos.domain.ConceptoPago;
import mx.edu.itch.sigclem.pagos.domain.TarifaPago;
import mx.edu.itch.sigclem.pagos.dto.AutorizacionDTO;
import mx.edu.itch.sigclem.pagos.dto.AutorizacionRequest;
import mx.edu.itch.sigclem.pagos.dto.ComprobanteDTO;
import mx.edu.itch.sigclem.pagos.dto.ConceptoDTO;
import mx.edu.itch.sigclem.pagos.dto.ConceptoRequest;
import mx.edu.itch.sigclem.pagos.dto.DatosComprobanteRequest;
import mx.edu.itch.sigclem.pagos.dto.RechazoRequest;
import mx.edu.itch.sigclem.pagos.dto.TarifaDTO;
import mx.edu.itch.sigclem.pagos.dto.TarifaRequest;
import mx.edu.itch.sigclem.pagos.dto.ValidarComprobanteRequest;
import mx.edu.itch.sigclem.pagos.repository.AutorizacionPagoRepository;
import mx.edu.itch.sigclem.pagos.repository.ComprobantePagoRepository;
import mx.edu.itch.sigclem.pagos.repository.ConceptoPagoRepository;
import mx.edu.itch.sigclem.pagos.repository.TarifaPagoRepository;
import mx.edu.itch.sigclem.notificaciones.domain.Notificacion;
import mx.edu.itch.sigclem.notificaciones.repository.NotificacionRepository;
import mx.edu.itch.sigclem.seguridad.domain.Usuario;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;

@Service
@RequiredArgsConstructor
public class PagoService {

    private static final String CURSO = "Curso de nivel";
    private static final String RFC_INSTITUCIONAL = "TNM140723GFA";
    private static final Set<String> ESTATUS_CARGO_ABIERTO = Set.of("Pendiente", "Parcial");

    private final TarifaPagoRepository tarifaRepository;
    private final ConceptoPagoRepository conceptoRepository;
    private final AutorizacionPagoRepository autorizacionRepository;
    private final ComprobantePagoRepository comprobanteRepository;
    private final AlumnoRepository alumnoRepository;
    private final AvanceNivelRepository avanceRepository;
    private final NivelRepository nivelRepository;
    private final PeriodoRepository periodoRepository;
    private final GrupoRepository grupoRepository;
    private final InscripcionRepository inscripcionRepository;
    private final UsuarioRepository usuarioRepository;
    private final ArchivoComprobanteService archivoService;
    private final NotificacionRepository notificacionRepository;
    private final VerificacionComprobanteService verificacion;

    @Transactional(readOnly = true)
    public List<TarifaDTO> listarTarifas(boolean incluirInactivas) {
        return (incluirInactivas ? tarifaRepository.findAllByOrderByConceptoAscIdAsc()
                : tarifaRepository.findByActivaTrueOrderByConceptoAscIdAsc())
                .stream().filter(t -> incluirInactivas || t.getConceptoPago().isActivo())
                .map(TarifaDTO::desde).toList();
    }

    @Transactional(readOnly = true)
    public List<ConceptoDTO> listarConceptos(boolean incluirInactivos) {
        return (incluirInactivos ? conceptoRepository.findAllByOrderByNombreAsc()
                : conceptoRepository.findByActivoTrueOrderByNombreAsc())
                .stream().map(c -> ConceptoDTO.desde(c, tarifaGeneral(c).orElse(null))).toList();
    }

    @Transactional
    public ConceptoDTO crearConcepto(ConceptoRequest request) {
        String nombre = request.nombre().trim();
        if (conceptoRepository.existsByNombreIgnoreCase(nombre)) {
            throw new ConflictoException("PAG-044", "Ya existe un concepto de pago con ese nombre.");
        }
        validarMonto(request.monto());
        ConceptoPago concepto = new ConceptoPago();
        concepto.setNombre(nombre);
        concepto.setRequiereNivel(Boolean.TRUE.equals(request.requiereNivel()));
        concepto.setActivo(true);
        conceptoRepository.save(concepto);
        return ConceptoDTO.desde(concepto, guardarTarifaGeneral(concepto, request.monto(), true));
    }

    /** Tarifa general del concepto (un solo monto para todos los niveles). */
    private java.util.Optional<TarifaPago> tarifaGeneral(ConceptoPago concepto) {
        return tarifaRepository.findFirstByConceptoPagoIdAndNivelIsNullOrderByActivaDescIdDesc(concepto.getId());
    }

    private TarifaPago guardarTarifaGeneral(ConceptoPago concepto, BigDecimal monto, boolean activa) {
        TarifaPago tarifa = tarifaGeneral(concepto).orElseGet(TarifaPago::new);
        tarifa.setConcepto(concepto.getNombre());
        tarifa.setConceptoPago(concepto);
        tarifa.setNivel(null);
        tarifa.setMonto(monto.setScale(2));
        tarifa.setActiva(activa);
        return tarifaRepository.save(tarifa);
    }

    /** Reactiva un concepto y su monto general. */
    @Transactional
    public ConceptoDTO activarConcepto(Integer id) {
        ConceptoPago concepto = conceptoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-045", "No existe el concepto indicado."));
        concepto.setActivo(true);
        TarifaPago tarifa = tarifaGeneral(concepto)
                .map(t -> { t.setActiva(true); return tarifaRepository.save(t); })
                .orElse(null);
        return ConceptoDTO.desde(conceptoRepository.save(concepto), tarifa);
    }

    @Transactional
    public ConceptoDTO desactivarConcepto(Integer id) {
        ConceptoPago concepto = conceptoRepository.findByIdAndActivoTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-045", "No existe un concepto activo con ese identificador."));
        concepto.setActivo(false);
        tarifaRepository.findAllByConceptoPagoIdAndActivaTrue(id).forEach(tarifa -> {
            tarifa.setActiva(false);
            tarifaRepository.save(tarifa);
        });
        return ConceptoDTO.desde(concepto);
    }

    /**
     * Edita nombre y si requiere nivel. El concepto «Curso de nivel» es del sistema (genera los cargos de los grupos)
     * y no se renombra. Al renombrar, las tarifas se actualizan; los cargos ya emitidos conservan su nombre.
     */
    @Transactional
    public ConceptoDTO editarConcepto(Integer id, ConceptoRequest request) {
        ConceptoPago concepto = conceptoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-045", "No existe el concepto indicado."));
        String nombre = request.nombre().trim();
        boolean renombra = !concepto.getNombre().equals(nombre);
        if (CURSO.equals(concepto.getNombre()) && renombra) {
            throw new ReglaNegocioException("PAG-049",
                    "«" + CURSO + "» es un concepto del sistema (genera las papeletas de los grupos): puedes cambiar "
                            + "su monto, pero no su nombre.");
        }
        validarMonto(request.monto());
        if (renombra && conceptoRepository.existsByNombreIgnoreCase(nombre)
                && !concepto.getNombre().equalsIgnoreCase(nombre)) {
            throw new ConflictoException("PAG-044", "Ya existe un concepto de pago con ese nombre.");
        }
        concepto.setNombre(nombre);
        if (request.requiereNivel() != null && !CURSO.equals(nombre)) {
            concepto.setRequiereNivel(request.requiereNivel());
        }
        if (renombra) {
            tarifaRepository.findAllByConceptoPagoId(id).forEach(t -> t.setConcepto(nombre));
        }
        conceptoRepository.save(concepto);
        // Los cargos ya emitidos conservan su monto: el monto esperado se copia al emitir cada cargo.
        return ConceptoDTO.desde(concepto, guardarTarifaGeneral(concepto, request.monto(), concepto.isActivo()));
    }

    /** Elimina un concepto que nunca se ha cobrado (junto con sus tarifas); si ya tiene cargos, solo se desactiva. */
    @Transactional
    public void eliminarConcepto(Integer id) {
        ConceptoPago concepto = conceptoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-045", "No existe el concepto indicado."));
        if (CURSO.equals(concepto.getNombre())) {
            throw new ReglaNegocioException("PAG-049", "«" + CURSO + "» es un concepto del sistema y no se elimina.");
        }
        if (autorizacionRepository.existsByConcepto(concepto.getNombre())) {
            throw new ConflictoException("PAG-051",
                    "El concepto ya tiene cargos emitidos a alumnos; desactívalo en lugar de eliminarlo.");
        }
        tarifaRepository.deleteAll(tarifaRepository.findAllByConceptoPagoId(id));
        conceptoRepository.delete(concepto);
    }

    @Transactional
    public TarifaDTO crearTarifa(TarifaRequest req) {
        ConceptoPago concepto = conceptoRepository.findByNombreAndActivoTrue(req.concepto().trim())
                .orElseThrow(() -> new DatosInvalidosException("PAG-034", "El concepto de pago seleccionado no es válido o está inactivo."));
        Nivel nivel = resolverNivel(concepto, req.idNivel());
        validarMonto(req.monto());
        String nombre = concepto.getNombre();
        boolean repetida = nivel == null
                ? tarifaRepository.existsByConceptoAndNivelIsNullAndActivaTrue(nombre)
                : tarifaRepository.existsByConceptoAndNivelIdAndActivaTrue(nombre, nivel.getId());
        if (repetida) {
            throw new ConflictoException("PAG-004", "Ya existe una tarifa activa para ese concepto y nivel.");
        }
        TarifaPago tarifa = new TarifaPago();
        tarifa.setConcepto(nombre);
        tarifa.setConceptoPago(concepto);
        tarifa.setNivel(nivel);
        tarifa.setMonto(req.monto().setScale(2));
        tarifa.setActiva(true);
        return TarifaDTO.desde(tarifaRepository.save(tarifa));
    }

    @Transactional
    public TarifaDTO desactivarTarifa(Integer id) {
        TarifaPago tarifa = tarifaRepository.findByIdAndActivaTrue(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-005", "No existe una tarifa activa con ese identificador."));
        tarifa.setActiva(false);
        return TarifaDTO.desde(tarifaRepository.save(tarifa));
    }

    @Transactional
    public AutorizacionDTO crearAutorizacion(AutorizacionRequest req, Authentication auth) {
        String numeroControl = req.numeroControl().trim().toUpperCase(Locale.ROOT);
        var alumno = alumnoRepository.bloquearPorNumeroControl(numeroControl)
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-006", "No existe el expediente del alumno " + numeroControl + "."));
        TarifaPago tarifa = tarifaRepository.findByIdAndActivaTrue(req.idTarifa())
                .orElseThrow(() -> new DatosInvalidosException("PAG-007", "La tarifa seleccionada no existe o está inactiva."));
        ConceptoPago concepto = tarifa.getConceptoPago();
        if (!concepto.isActivo()) {
            throw new DatosInvalidosException("PAG-007", "La tarifa seleccionada pertenece a un concepto inactivo.");
        }
        Periodo periodo = periodoRepository.findById(req.idPeriodo())
                .orElseThrow(() -> new DatosInvalidosException("PAG-008", "El periodo seleccionado no existe."));
        if (periodo.getFechaFin().isBefore(LocalDate.now())) {
            throw new ReglaNegocioException("PAG-009", "No se puede solicitar autorización para un periodo concluido.");
        }
        // Con tarifa general, el cargo de un concepto con nivel se liga al siguiente nivel que el alumno puede cursar.
        Nivel nivelCargo = null;
        if (concepto.isRequiereNivel()) {
            nivelCargo = tarifa.getNivel() != null ? tarifa.getNivel() : siguienteNivelHabilitado(numeroControl);
            AvanceNivel avance = avanceRepository.findByIdNumeroControlAndIdIdNivel(numeroControl, nivelCargo.getId())
                    .orElseThrow(() -> new ReglaNegocioException("PAG-010",
                            "El expediente no contiene el nivel seleccionado."));
            if (avance.getEstatus() != EstatusAvance.HABILITADO && avance.getEstatus() != EstatusAvance.REPROBADO) {
                throw new ReglaNegocioException("PAG-011",
                        "Solo puedes solicitar autorización para el siguiente nivel habilitado en tu avance.");
            }
        }
        var autorizacionExistente = concepto.isRequiereNivel()
                ? autorizacionRepository.findFirstByAlumnoNumeroControlAndConceptoAndNivelIdAndPeriodoIdAndEstatusInOrderByFechaEmisionDescIdDesc(
                        numeroControl, concepto.getNombre(), nivelCargo.getId(), periodo.getId(), ESTATUS_CARGO_ABIERTO)
                : autorizacionRepository.findFirstByAlumnoNumeroControlAndConceptoAndNivelIsNullAndPeriodoIdAndEstatusInOrderByFechaEmisionDescIdDesc(
                        numeroControl, concepto.getNombre(), periodo.getId(), ESTATUS_CARGO_ABIERTO);
        if (autorizacionExistente.isPresent()) {
            return dtoCargo(autorizacionExistente.get());
        }
        Usuario usuarioEmite = usuarioRepository.findByNombreUsuario(auth.getName())
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-012", "No se encontró la cuenta autenticada."));
        AutorizacionPago autorizacion = new AutorizacionPago();
        autorizacion.setAlumno(alumno);
        autorizacion.setConcepto(tarifa.getConcepto());
        autorizacion.setNivel(nivelCargo);
        autorizacion.setPeriodo(periodo);
        autorizacion.setMontoEsperado(tarifa.getMonto());
        autorizacion.setSaldoPendiente(tarifa.getMonto());
        autorizacion.setEstatus("Pendiente");
        autorizacion.setFechaEmision(LocalDate.now());
        autorizacion.setUsuarioEmite(usuarioEmite);
        AutorizacionPago guardada = autorizacionRepository.save(autorizacion);
        notificar(alumno.getUsuario(), usuarioEmite, "Nueva autorización de pago",
                "Se emitió el cargo de " + concepto.getNombre() + " por "
                        + guardada.getMontoEsperado() + " MXN. Saldo pendiente: "
                        + guardada.getSaldoPendiente() + " MXN.");
        return dtoCargo(guardada);
    }

    @Transactional(readOnly = true)
    public List<AutorizacionDTO> listarAutorizaciones(Authentication auth) {
        return autorizacionRepository.findByAlumnoNumeroControlOrderByFechaEmisionDescIdDesc(auth.getName())
                .stream().map(this::dtoCargo).toList();
    }

    /** Listado general de cargos para coordinación y supervisión, opcionalmente filtrado por estatus. */
    @Transactional(readOnly = true)
    public List<mx.edu.itch.sigclem.pagos.dto.CargoResumenDTO> listarTodosLosCargos(String estatus) {
        // Conteo de comprobantes de todos los cargos en una sola consulta (antes: una consulta por cargo).
        java.util.Map<Integer, long[]> conteos = new java.util.HashMap<>();
        for (Object[] fila : comprobanteRepository.contarPorAutorizacion()) {
            conteos.put((Integer) fila[0], new long[] { ((Number) fila[1]).longValue(),
                    fila[2] == null ? 0 : ((Number) fila[2]).longValue() });
        }
        return autorizacionRepository.findAllByOrderByFechaEmisionDescIdDesc().stream()
                .filter(a -> estatus == null || estatus.isBlank() || estatus.equals(a.getEstatus()))
                .map(a -> {
                    long[] conteo = conteos.getOrDefault(a.getId(), new long[2]);
                    return new mx.edu.itch.sigclem.pagos.dto.CargoResumenDTO(a.getId(), a.getAlumno().getNumeroControl(),
                            a.getAlumno().nombreCompleto(), a.getConcepto(),
                            a.getNivel() == null ? null : a.getNivel().getNombre(), a.getPeriodo().getNombre(),
                            a.getMontoEsperado(), a.getSaldoPendiente(), a.getEstatus(), a.getFechaEmision(),
                            (int) conteo[0], (int) conteo[1]);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AutorizacionDTO> listarCargos(String numeroControl, Authentication auth, boolean personalAutorizado) {
        if (!personalAutorizado && !numeroControl.equalsIgnoreCase(auth.getName())) {
            throw new ReglaNegocioException("PAG-025", "Solo puedes consultar tus propios cargos.");
        }
        if (!alumnoRepository.existsById(numeroControl)) {
            throw new RecursoNoEncontradoException("PAG-006", "No existe el expediente solicitado.");
        }
        return autorizacionRepository.findByAlumnoNumeroControlOrderByFechaEmisionDescIdDesc(numeroControl)
                .stream().map(this::dtoCargo).toList();
    }

    private AutorizacionDTO dtoCargo(AutorizacionPago autorizacion) {
        List<ComprobantePago> pagos = comprobanteRepository
                .findByAutorizacionIdOrderByFechaCargaAscIdAsc(autorizacion.getId());
        ComprobantePago ultimo = pagos.isEmpty() ? null : pagos.get(pagos.size() - 1);
        return AutorizacionDTO.desde(autorizacion,
                ultimo == null ? null : ultimo.getEstatus(),
                ultimo == null ? null : ultimo.getMotivoRechazo(),
                pagos.stream().map(ComprobanteDTO::desde).toList());
    }

    @Transactional
    public ComprobanteDTO cargarComprobante(
            Integer idAutorizacion, String uuid, String rfc, String totalTexto, String contenidoQr,
            MultipartFile archivo, Authentication auth) {
        AutorizacionPago autorizacion = autorizacionRepository
                .bloquearPorId(idAutorizacion)
                .filter(a -> a.getAlumno().getNumeroControl().equals(auth.getName()))
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-013",
                        "No existe esa autorización para el alumno autenticado."));
        if (!ESTATUS_CARGO_ABIERTO.contains(autorizacion.getEstatus())) {
            throw new ReglaNegocioException("PAG-014", "El cargo ya está liquidado y no acepta más comprobantes.");
        }
        if (comprobanteRepository.existsByAutorizacionIdAndEstatus(idAutorizacion, "Pendiente")) {
            throw new ConflictoException("PAG-014",
                    "Esta autorización ya tiene un comprobante pendiente o validado.");
        }
        byte[] contenido = archivoService.leer(archivo);
        // El QR se lee en el servidor (fuente confiable); el contenido que envía el navegador solo se usa si el
        // servidor no logra leerlo, y en ambos casos el SAT confirma que el CFDI existe con esos datos.
        LectorComprobanteService.Lectura lectura = verificacion.leer(contenido);
        String qrAlumno = contenidoQr == null ? "" : contenidoQr.trim();
        if (qrAlumno.length() > 255) {
            throw new DatosInvalidosException("PAG-017", "El contenido del QR no puede superar 255 caracteres.");
        }
        CfdiQr cfdiServidor = CfdiQr.interpretar(lectura.contenidoQr());
        String qr;
        String origen;
        if (cfdiServidor != null && lectura.contenidoQr().length() <= 255) {
            qr = lectura.contenidoQr();
            origen = "QR servidor";
        } else {
            qr = qrAlumno;
            origen = qr.isBlank() ? null : "QR alumno";
        }
        CfdiQr cfdi = CfdiQr.interpretar(qr);
        boolean sinDatosCapturados = (uuid == null || uuid.isBlank()) && (rfc == null || rfc.isBlank())
                && (totalTexto == null || totalTexto.isBlank());
        if (cfdi != null && sinDatosCapturados) {
            uuid = cfdi.uuid();
            rfc = cfdi.rfcEmisor();
            totalTexto = cfdi.total().toPlainString();
        } else if (!sinDatosCapturados && origen == null) {
            origen = "Alumno";
        }
        String uuidNormalizado = uuid == null || uuid.isBlank() ? null : normalizarUuid(uuid);
        if (uuidNormalizado != null && comprobanteRepository.existsByUuidCfdiIgnoreCase(uuidNormalizado)) {
            throw new ConflictoException("PAG-015", "Ese UUID de CFDI ya fue registrado.");
        }
        String rfcNormalizado = rfc == null ? "" : rfc.trim().toUpperCase(Locale.ROOT);
        if (!rfcNormalizado.isBlank() && !RFC_INSTITUCIONAL.equals(rfcNormalizado)) {
            throw new DatosInvalidosException("PAG-016", "El RFC del emisor no corresponde al Instituto Tecnológico Nacional de México.");
        }
        BigDecimal total = totalTexto == null || totalTexto.isBlank() ? null : parsearTotal(totalTexto);
        if (total != null && uuidNormalizado != null && !rfcNormalizado.isBlank()) {
            validarDatosComprobante(uuidNormalizado, rfcNormalizado, total, qr);
        } else if (total != null || uuidNormalizado != null || !rfcNormalizado.isBlank()) {
            throw new DatosInvalidosException("PAG-046",
                    "Captura UUID, RFC y total juntos, o envía el comprobante para captura manual.");
        }
        if (total != null) {
            validarMontoAbono(autorizacion, total);
        }
        ComprobantePago comprobante = new ComprobantePago();
        comprobante.setAutorizacion(autorizacion);
        comprobante.setUuidCfdi(uuidNormalizado);
        // Los campos no capturados se guardan como NULL: la BD solo admite NULL o el RFC institucional,
        // y la captura manual de coordinación detecta los comprobantes pendientes por estos NULL.
        comprobante.setRfcEmisor(rfcNormalizado.isBlank() ? null : rfcNormalizado);
        comprobante.setTotal(total);
        comprobante.setContenidoQr(qr.isBlank() ? null : qr);
        comprobante.setOrigenDatos(uuidNormalizado == null ? null : origen);
        comprobante.setEstatus("Pendiente");
        if (uuidNormalizado != null) {
            ConsultaSatService.ResultadoSat sat = verificacion.consultarSat(comprobante);
            if (ConsultaSatService.CANCELADO.equals(sat.estado())) {
                throw new ReglaNegocioException("PAG-052",
                        "El SAT reporta este CFDI como cancelado; no puede usarse como comprobante de pago.");
            }
        } else {
            comprobante.setEstadoSat(ConsultaSatService.SIN_VERIFICAR);
            comprobante.setDetalleSat("No se pudo leer el QR; coordinación capturará los datos del CFDI.");
        }
        verificacion.cotejar(comprobante, lectura.texto());
        comprobante.setArchivo(archivoService.guardar(contenido));
        return ComprobanteDTO.desde(comprobanteRepository.save(comprobante));
    }

    @Transactional(readOnly = true)
    public List<ComprobanteDTO> listarPendientes(String estatus) {
        String estado = estatus == null || estatus.isBlank() ? "Pendiente" : estatus.trim();
        if (!Set.of("Pendiente", "Validado", "Rechazado").contains(estado)) {
            throw new DatosInvalidosException("PAG-047", "El estatus de comprobante solicitado no es válido.");
        }
        List<ComprobantePago> comprobantes = comprobanteRepository.findByEstatusOrderByFechaCargaAsc(estado);
        // Grupo asignado de todos los cargos en una sola consulta (antes: una por comprobante).
        java.util.Map<Integer, String> grupoPorCargo = new java.util.HashMap<>();
        if (!comprobantes.isEmpty()) {
            inscripcionRepository.findByIdAutorizacionIn(comprobantes.stream()
                            .map(c -> c.getAutorizacion().getId()).collect(java.util.stream.Collectors.toSet()))
                    .forEach(i -> grupoPorCargo.put(i.getIdAutorizacion(),
                            i.getGrupo().getNivel().getNombre() + " " + i.getGrupo().getClave()));
        }
        return comprobantes.stream()
                .map(c -> ComprobanteDTO.desde(c, grupoPorCargo.get(c.getAutorizacion().getId())))
                .toList();
    }

    @Transactional
    public ComprobanteDTO capturarDatosManuales(
            Integer id, DatosComprobanteRequest request, Authentication auth) {
        ComprobantePago comprobante = comprobanteRepository.bloquearPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-018", "No existe el comprobante solicitado."));
        if (!"Pendiente".equals(comprobante.getEstatus())
                || comprobante.getUuidCfdi() != null
                || comprobante.getRfcEmisor() != null
                || comprobante.getTotal() != null) {
            throw new ReglaNegocioException("PAG-048", "El comprobante no requiere captura manual.");
        }
        String uuid = normalizarUuid(request.uuidCfdi());
        String rfc = request.rfcEmisor().trim().toUpperCase(Locale.ROOT);
        BigDecimal total = request.total();
        // Un QR guardado que no es de un CFDI (o que el alumno pegó mal) no debe impedir la captura manual.
        String qrGuardado = CfdiQr.interpretar(comprobante.getContenidoQr()) == null ? null : comprobante.getContenidoQr();
        validarDatosComprobante(uuid, rfc, total, qrGuardado);
        if (comprobanteRepository.existsByUuidCfdiIgnoreCase(uuid)) {
            throw new ConflictoException("PAG-015", "Ese UUID de CFDI ya fue registrado.");
        }
        AutorizacionPago autorizacion = autorizacionRepository.bloquearPorId(
                        comprobante.getAutorizacion().getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-013", "No existe el cargo asociado."));
        validarMontoAbono(autorizacion, total);
        comprobante.setUuidCfdi(uuid);
        comprobante.setRfcEmisor(rfc);
        comprobante.setTotal(total);
        comprobante.setOrigenDatos("Coordinación");
        comprobante.setSelloFinal(vacioANull(request.selloFinal()));
        verificacion.consultarSat(comprobante);
        return ComprobanteDTO.desde(comprobanteRepository.save(comprobante));
    }

    /** Repite la lectura del archivo, la consulta al SAT y el cotejo (p. ej. si el SAT no respondió al subirlo). */
    @Transactional
    public ComprobanteDTO reverificarComprobante(Integer id) {
        ComprobantePago comprobante = comprobanteRepository.bloquearPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-018", "No existe el comprobante solicitado."));
        if (!"Pendiente".equals(comprobante.getEstatus())) {
            throw new ReglaNegocioException("PAG-019", "Solo se pueden reverificar comprobantes pendientes.");
        }
        LectorComprobanteService.Lectura lectura = verificacion.releer(comprobante);
        CfdiQr cfdi = CfdiQr.interpretar(lectura.contenidoQr());
        if (comprobante.getUuidCfdi() == null && cfdi != null && lectura.contenidoQr().length() <= 255
                && !comprobanteRepository.existsByUuidCfdiIgnoreCase(cfdi.uuid())) {
            validarDatosComprobante(cfdi.uuid(), cfdi.rfcEmisor(), cfdi.total(), lectura.contenidoQr());
            comprobante.setUuidCfdi(cfdi.uuid());
            comprobante.setRfcEmisor(cfdi.rfcEmisor());
            comprobante.setTotal(cfdi.total());
            comprobante.setContenidoQr(lectura.contenidoQr());
            comprobante.setOrigenDatos("QR servidor");
        }
        if (comprobante.getUuidCfdi() != null) {
            verificacion.consultarSat(comprobante);
        }
        verificacion.cotejar(comprobante, lectura.texto());
        return ComprobanteDTO.desde(comprobanteRepository.save(comprobante));
    }

    @Transactional
    public ComprobanteDTO validarComprobante(
            Integer id, ValidarComprobanteRequest req, Authentication auth) {
        ComprobantePago comprobante = comprobanteRepository.bloquearPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-018", "No existe el comprobante solicitado."));
        if (!"Pendiente".equals(comprobante.getEstatus())) {
            throw new ReglaNegocioException("PAG-019", "Solo se pueden validar comprobantes pendientes.");
        }
        if (comprobante.getUuidCfdi() == null || comprobante.getRfcEmisor() == null
                || comprobante.getTotal() == null) {
            throw new ReglaNegocioException("PAG-048", "Captura los datos del comprobante antes de validarlo.");
        }
        AutorizacionPago autorizacion = autorizacionRepository.bloquearPorId(
                        comprobante.getAutorizacion().getId())
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-013", "No existe el cargo asociado."));
        validarDatosComprobante(comprobante.getUuidCfdi(), comprobante.getRfcEmisor(),
                comprobante.getTotal(),
                CfdiQr.interpretar(comprobante.getContenidoQr()) == null ? null : comprobante.getContenidoQr());
        if (ConsultaSatService.CANCELADO.equals(comprobante.getEstadoSat())) {
            throw new ReglaNegocioException("PAG-053",
                    "El SAT reporta este CFDI como cancelado; recházalo en lugar de validarlo.");
        }
        if ("Liquidada".equals(autorizacion.getEstatus())) {
            throw new ReglaNegocioException("PAG-014", "El cargo ya fue liquidado.");
        }
        if (comprobante.getTotal().compareTo(autorizacion.getSaldoPendiente()) > 0) {
            throw new ReglaNegocioException("PAG-021", "El total supera el saldo pendiente del cargo.");
        }
        boolean liquidaCargo = comprobante.getTotal().compareTo(autorizacion.getSaldoPendiente()) == 0;
        var inscripcionAsignada = inscripcionRepository.findByIdAutorizacion(autorizacion.getId());
        if (inscripcionAsignada.isPresent()) {
            // El alumno ya fue asignado a su grupo por coordinación: el pago solo se liga a esa inscripción.
            if (liquidaCargo) {
                inscripcionAsignada.get().setIdComprobante(comprobante.getId());
                inscripcionRepository.save(inscripcionAsignada.get());
            }
        } else if (CURSO.equals(autorizacion.getConcepto()) && liquidaCargo) {
            if (req == null || req.idGrupo() == null) {
                throw new DatosInvalidosException("PAG-022", "Selecciona el grupo al que se inscribirá el alumno.");
            }
            inscribirEnGrupo(autorizacion, comprobante, req.idGrupo());
        } else if (req != null && req.idGrupo() != null) {
            throw new DatosInvalidosException("PAG-023", "Los pagos de examen no deben incluir un grupo.");
        }
        Usuario validador = usuarioRepository.findByNombreUsuario(auth.getName())
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-024", "No se encontró la cuenta de coordinación."));
        comprobante.setEstatus("Validado");
        comprobante.setMotivoRechazo(null);
        comprobante.setUsuarioValida(validador);
        autorizacion.setSaldoPendiente(autorizacion.getSaldoPendiente().subtract(comprobante.getTotal()));
        autorizacion.setEstatus(autorizacion.getSaldoPendiente().signum() == 0 ? "Liquidada" : "Parcial");
        autorizacionRepository.save(autorizacion);
        notificar(autorizacion.getAlumno().getUsuario(), validador,
                liquidaCargo ? "Pago validado" : "Pago parcial validado",
                liquidaCargo
                        ? "Tu cargo de " + autorizacion.getConcepto() + " quedó liquidado."
                        : "Tu abono fue aplicado. Saldo pendiente: "
                                + autorizacion.getSaldoPendiente() + " MXN.");
        return ComprobanteDTO.desde(comprobanteRepository.save(comprobante));
    }

    @Transactional
    public ComprobanteDTO rechazarComprobante(Integer id, RechazoRequest req, Authentication auth) {
        ComprobantePago comprobante = comprobanteRepository.bloquearPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-018", "No existe el comprobante solicitado."));
        if (!"Pendiente".equals(comprobante.getEstatus())) {
            throw new ReglaNegocioException("PAG-019", "Solo se pueden rechazar comprobantes pendientes.");
        }
        Usuario validador = usuarioRepository.findByNombreUsuario(auth.getName())
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-024", "No se encontró la cuenta de coordinación."));
        comprobante.setEstatus("Rechazado");
        comprobante.setMotivoRechazo(req.motivo().trim());
        comprobante.setUsuarioValida(validador);
        notificar(comprobante.getAutorizacion().getAlumno().getUsuario(), validador,
                "Comprobante rechazado",
                "El comprobante de " + comprobante.getAutorizacion().getConcepto()
                        + " fue rechazado. Motivo: " + comprobante.getMotivoRechazo());
        return ComprobanteDTO.desde(comprobanteRepository.save(comprobante));
    }

    @Transactional(readOnly = true)
    public Resource obtenerArchivo(Integer id, Authentication auth, boolean coordinacion) {
        ComprobantePago comprobante = comprobanteRepository.findConRelacionesById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("PAG-018", "No existe el comprobante solicitado."));
        if (!coordinacion && !comprobante.getAutorizacion().getAlumno().getNumeroControl().equals(auth.getName())) {
            throw new ReglaNegocioException("PAG-025", "Solo puedes consultar tus propios comprobantes.");
        }
        return archivoService.cargar(comprobante.getArchivo());
    }

    private void inscribirEnGrupo(AutorizacionPago autorizacion, ComprobantePago comprobante, Integer idGrupo) {
        Grupo grupo = grupoRepository.bloquearPorId(idGrupo)
                .orElseThrow(() -> new DatosInvalidosException("PAG-026", "El grupo seleccionado no existe."));
        if (!grupo.getNivel().getId().equals(autorizacion.getNivel().getId())
                || !grupo.getPeriodo().getId().equals(autorizacion.getPeriodo().getId())) {
            throw new ReglaNegocioException("PAG-027",
                    "El grupo debe corresponder al nivel y periodo de la autorización.");
        }
        if (grupo.getPeriodo().getFechaFin().isBefore(LocalDate.now())
                || "Cerrada".equals(grupo.getEstatusActa())) {
            throw new ReglaNegocioException("PAG-028", "No se puede inscribir en un grupo vencido o cerrado.");
        }
        if (inscripcionRepository.existsByAlumnoNumeroControlAndGrupoId(
                autorizacion.getAlumno().getNumeroControl(), grupo.getId())) {
            throw new ConflictoException("PAG-029", "El alumno ya está inscrito en ese grupo.");
        }
        if (inscripcionRepository.countByGrupoId(grupo.getId()) >= grupo.getCupo()) {
            throw new ReglaNegocioException("PAG-030", "El grupo ya alcanzó su cupo máximo.");
        }
        AvanceNivel avance = avanceRepository.findByIdNumeroControlAndIdIdNivel(
                autorizacion.getAlumno().getNumeroControl(), grupo.getNivel().getId())
                .orElseThrow(() -> new ReglaNegocioException("PAG-010", "El expediente no contiene el nivel autorizado."));
        if (avance.getEstatus() != EstatusAvance.HABILITADO) {
            throw new ReglaNegocioException("PAG-011", "El nivel autorizado ya no está habilitado para este alumno.");
        }
        Inscripcion inscripcion = new Inscripcion();
        inscripcion.setAlumno(autorizacion.getAlumno());
        inscripcion.setGrupo(grupo);
        inscripcion.setIdComprobante(comprobante.getId());
        inscripcion.setIdAutorizacion(autorizacion.getId());
        inscripcionRepository.save(inscripcion);
        avance.setEstatus(EstatusAvance.CURSANDO);
        // ck_avance_origen solo admite 'Curso', 'Examen de colocación' o 'Examen de cuatro habilidades'.
        avance.setOrigen("Curso");
        avanceRepository.save(avance);
    }

    private Nivel resolverNivel(ConceptoPago concepto, Integer idNivel) {
        if (!concepto.isRequiereNivel() && idNivel != null) {
            throw new DatosInvalidosException("PAG-032", "Este concepto de pago no debe asociarse a un nivel.");
        }
        if (idNivel == null) {
            return null;
        }
        return nivelRepository.findById(idNivel)
                .orElseThrow(() -> new DatosInvalidosException("PAG-033", "El nivel seleccionado no existe."));
    }

    /** Primer nivel que el alumno puede cursar (habilitado o reprobado). */
    private Nivel siguienteNivelHabilitado(String numeroControl) {
        java.util.Map<Integer, AvanceNivel> avance = avanceRepository.findByIdNumeroControl(numeroControl).stream()
                .collect(java.util.stream.Collectors.toMap(a -> a.getId().getIdNivel(), a -> a));
        return nivelRepository.findAllByOrderByNumeroAsc().stream()
                .filter(n -> avance.get(n.getId()) != null
                        && (avance.get(n.getId()).getEstatus() == EstatusAvance.HABILITADO
                            || avance.get(n.getId()).getEstatus() == EstatusAvance.REPROBADO))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException("PAG-011",
                        "El alumno no tiene un nivel habilitado para cursar (puede estar cursando uno o haber acreditado el idioma)."));
    }

    private void validarMonto(BigDecimal monto) {
        if (monto == null || monto.signum() <= 0 || monto.scale() > 2 || monto.precision() > 10) {
            throw new DatosInvalidosException("PAG-035", "El monto debe ser mayor a cero y tener máximo dos decimales.");
        }
    }

    private String normalizarUuid(String uuid) {
        if (uuid == null) {
            throw new DatosInvalidosException("PAG-036", "El UUID del CFDI es obligatorio.");
        }

        try {
            String normalizado = UUID.fromString(uuid.trim()).toString().toUpperCase(Locale.ROOT);
            if (!normalizado.equalsIgnoreCase(uuid.trim())) {
                throw new IllegalArgumentException("Formato no canónico");
            }
            return normalizado;
        } catch (IllegalArgumentException e) {
            throw new DatosInvalidosException("PAG-037", "El UUID del CFDI debe tener formato válido.");
        }
    }

    private String vacioANull(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private void validarDatosComprobante(String uuid, String rfc, BigDecimal total, String qr) {
        normalizarUuid(uuid);
        if (!RFC_INSTITUCIONAL.equals(rfc.trim().toUpperCase(Locale.ROOT))) {
            throw new DatosInvalidosException("PAG-016",
                    "El RFC del comprobante no corresponde al Instituto Tecnológico Nacional de México.");
        }
        if (total == null || total.signum() <= 0 || total.scale() > 2 || total.precision() > 10) {
            throw new DatosInvalidosException("PAG-038",
                    "El total del CFDI debe ser un importe positivo con máximo dos decimales.");
        }
        if (qr != null && !qr.isBlank()) {
            validarQrCfdi(qr, uuid, rfc, total);
        }
    }

    private void validarMontoAbono(AutorizacionPago autorizacion, BigDecimal total) {
        if (total == null || total.signum() <= 0
                || total.compareTo(autorizacion.getSaldoPendiente()) > 0) {
            throw new ReglaNegocioException("PAG-021",
                    "El total del comprobante debe ser positivo y no superar el saldo pendiente del cargo.");
        }
    }

    private void notificar(Usuario destino, Usuario origen, String asunto, String mensaje) {
        Notificacion notificacion = new Notificacion();
        notificacion.setDestino(destino);
        notificacion.setOrigen(origen);
        notificacion.setAsunto(asunto);
        notificacion.setMensaje(mensaje);
        notificacion.setFechaEnvio(java.time.LocalDateTime.now());
        notificacion.setLeida(false);
        notificacionRepository.save(notificacion);
    }

    private BigDecimal parsearTotal(String totalTexto) {
        try {
            BigDecimal total = new BigDecimal(totalTexto.trim());
            if (total.signum() <= 0 || total.scale() > 2 || total.precision() > 10) {
                throw new NumberFormatException("Fuera de rango");
            }
            return total.setScale(2);
        } catch (NullPointerException | NumberFormatException e) {
            throw new DatosInvalidosException("PAG-038", "El total del CFDI debe ser un importe positivo con máximo dos decimales.");
        }
    }

    private void validarQrCfdi(String contenido, String uuid, String rfc, BigDecimal total) {
        if (contenido.isBlank()) {
            throw new DatosInvalidosException("PAG-039", "Pega el contenido leído del QR del CFDI para validarlo.");
        }
        String uuidQr = parametroQr(contenido, "id");
        String rfcQr = parametroQr(contenido, "re");
        String totalQr = parametroQr(contenido, "tt");
        if (uuidQr == null || rfcQr == null || totalQr == null) {
            throw new DatosInvalidosException("PAG-040",
                    "El QR debe incluir los parámetros id, re y tt del verificador de CFDI.");
        }
        BigDecimal totalLeido;
        try {
            totalLeido = new BigDecimal(totalQr);
        } catch (NumberFormatException e) {
            throw new DatosInvalidosException("PAG-041", "El total contenido en el QR no tiene un formato válido.");
        }
        if (!uuid.equalsIgnoreCase(uuidQr) || !rfc.equalsIgnoreCase(rfcQr)
                || total.compareTo(totalLeido) != 0) {
            throw new DatosInvalidosException("PAG-042",
                    "Los datos de UUID, RFC y total deben coincidir con el contenido del QR del CFDI.");
        }
    }

    private String parametroQr(String contenido, String nombre) {
        Pattern parametro = Pattern.compile("(?:^|[?&])" + nombre + "=([^&]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = parametro.matcher(contenido);
        if (!matcher.find()) {
            return null;
        }
        try {
            return URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new DatosInvalidosException("PAG-043", "El contenido del QR tiene codificación inválida.");
        }
    }
}
