package mx.edu.itch.sigclem.notificaciones.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.notificaciones.dto.MensajeriaDTOs.Destinatarios;
import mx.edu.itch.sigclem.notificaciones.dto.MensajeriaDTOs.Enviado;
import mx.edu.itch.sigclem.notificaciones.dto.MensajeriaDTOs.EnvioMensaje;
import mx.edu.itch.sigclem.notificaciones.dto.MensajeriaDTOs.Recibido;
import mx.edu.itch.sigclem.notificaciones.dto.MensajeriaDTOs.ResultadoEnvio;
import mx.edu.itch.sigclem.notificaciones.service.MensajeriaService;

@Tag(name = "14. Mensajes", description = "Mensajería alumno ↔ facilitador ↔ coordinación; coordinación escribe a todos "
        + "(también por difusión). La jefa del DGTV solo consulta.")
@RestController
@RequestMapping("/api/v1/mensajes")
@RequiredArgsConstructor
public class MensajeriaController {

    private final MensajeriaService mensajeriaService;

    @GetMapping("/destinatarios")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Personas y listas de difusión a las que puedo escribir", description = "Rol: [Todos]")
    public Destinatarios destinatarios(Authentication authentication) {
        return mensajeriaService.destinatarios(authentication.getName());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ALUMNO','FACILITADOR','COORDINADOR')")
    @Operation(summary = "Enviar mensaje", description = "Rol: [Alumno][Facilitador][Coordinador]")
    public ResponseEntity<ResultadoEnvio> enviar(@Valid @RequestBody EnvioMensaje solicitud, Authentication authentication) {
        return ResponseEntity.status(201).body(mensajeriaService.enviar(solicitud, authentication.getName()));
    }

    @GetMapping("/recibidos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Bandeja de entrada (mensajes y avisos del sistema)", description = "Rol: [Todos]")
    public List<Recibido> recibidos(Authentication authentication) {
        return mensajeriaService.recibidos(authentication.getName());
    }

    @GetMapping("/enviados")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mensajes enviados", description = "Rol: [Todos]")
    public List<Enviado> enviados(Authentication authentication) {
        return mensajeriaService.enviados(authentication.getName());
    }

    @GetMapping("/no-leidos")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cantidad de mensajes sin leer", description = "Rol: [Todos]")
    public Map<String, Long> noLeidos(Authentication authentication) {
        return Map.of("total", mensajeriaService.noLeidos(authentication.getName()));
    }

    @PatchMapping("/{id}/leido")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Marcar mensaje como leído", description = "Rol: [Todos]")
    public ResponseEntity<Void> marcarLeido(@PathVariable Integer id, Authentication authentication) {
        mensajeriaService.marcarLeido(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
