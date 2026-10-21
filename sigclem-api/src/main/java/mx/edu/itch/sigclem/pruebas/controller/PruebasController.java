package mx.edu.itch.sigclem.pruebas.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.comun.excepcion.RecursoNoEncontradoException;
import mx.edu.itch.sigclem.pruebas.dto.DatosDemoDTO;
import mx.edu.itch.sigclem.pruebas.dto.EstadoPruebasDTO;
import mx.edu.itch.sigclem.pruebas.dto.UsuarioPruebaDTO;
import mx.edu.itch.sigclem.pruebas.service.DatosDemoService;
import mx.edu.itch.sigclem.seguridad.config.ModoPruebasFilter;
import mx.edu.itch.sigclem.seguridad.dto.PerfilResponse;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;
import mx.edu.itch.sigclem.seguridad.service.PerfilService;
import mx.edu.itch.sigclem.seguridad.service.UsuarioAutenticado;

/** Herramientas del modo pruebas. Responden 404 cuando el modo pruebas está desactivado. */
@Tag(name = "00. Modo pruebas", description = "Consulta de cuentas para la cabecera X-Usuario-Prueba y carga de "
        + "datos de demostración. Solo disponible con MODO_PRUEBAS=true.")
@RestController
@RequestMapping("/api/v1/pruebas")
@RequiredArgsConstructor
public class PruebasController {

    private final UsuarioRepository usuarioRepository;
    private final PerfilService perfilService;
    private final DatosDemoService datosDemoService;

    @Value("${app.modo-pruebas.activo:false}")
    private boolean modoPruebas;

    @Value("${app.modo-pruebas.usuario-por-omision:}")
    private String usuarioPorOmision;

    @Operation(summary = "Estado del modo pruebas",
            description = "Público. Indica si la API acepta peticiones sin login y qué usuario se usa por omisión.")
    @GetMapping("/estado")
    public EstadoPruebasDTO estado() {
        return new EstadoPruebasDTO(modoPruebas, modoPruebas ? usuarioPorOmision : null,
                ModoPruebasFilter.CABECERA, "/api/v1/docs/ui");
    }

    @Operation(summary = "Listar cuentas disponibles para probar",
            description = "Usa el valor de 'usuario' en el botón Authorize (X-Usuario-Prueba) para actuar con ese rol: "
                    + "alumno = número de control, facilitador = correo, coordinador y jefa = su usuario.")
    @GetMapping("/usuarios")
    @Transactional(readOnly = true)
    public List<UsuarioPruebaDTO> usuarios() {
        verificarActivo();
        return usuarioRepository.findAll(Sort.by("id")).stream()
                .map(u -> {
                    PerfilResponse perfil = perfilService.perfil(UsuarioAutenticado.desde(u));
                    return new UsuarioPruebaDTO(u.getId(), u.getNombreUsuario(), perfil.nombre(), perfil.rol(),
                            u.isActivo());
                })
                .toList();
    }

    @Operation(summary = "Cargar datos de demostración",
            description = "Crea (si no existen) un periodo vigente, un aula, un facilitador, dos alumnos, tarifas y "
                    + "un grupo de Básico 1. Se puede ejecutar varias veces sin duplicar datos.")
    @PostMapping("/datos-demo")
    @PreAuthorize("hasRole('COORDINADOR')")
    public DatosDemoDTO datosDemo() {
        verificarActivo();
        return datosDemoService.cargar();
    }

    private void verificarActivo() {
        if (!modoPruebas) {
            throw new RecursoNoEncontradoException("PRU-000", "El modo pruebas está desactivado.");
        }
    }
}
