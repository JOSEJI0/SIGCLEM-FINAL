package mx.edu.itch.sigclem.seguridad.config;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.edu.itch.sigclem.seguridad.repository.UsuarioRepository;
import mx.edu.itch.sigclem.seguridad.service.CustomUserDetailsService;
import mx.edu.itch.sigclem.seguridad.service.JwtService;

/**
 * Configuración de seguridad (apartado 4.2.4 y Tabla 59).
 * Regla clave del cambio 01: la jefa del DGTV solo puede leer; cualquier método que no sea GET
 * (salvo marcar un mensaje como leído) exige un rol operativo.
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final RespuestasSeguridad respuestas;
    private final UsuarioRepository usuarioRepository;

    @Value("${app.cors.origenes}")
    private String origenes;

    /** Modo pruebas: consumo de la API sin login (ver {@link ModoPruebasFilter}). */
    @Value("${app.modo-pruebas.activo:false}")
    private boolean modoPruebas;

    @Value("${app.modo-pruebas.usuario-por-omision:}")
    private String usuarioPruebaPorOmision;

    @Bean
    public SecurityFilterChain cadenaSeguridad(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e -> e.authenticationEntryPoint(respuestas).accessDeniedHandler(respuestas))
            .authorizeHttpRequests(auth -> auth
                // Público: solo el inicio de sesión. Desde el cambio 15, la creación de cuentas de alumno la hace el
                // coordinador (POST /alumnos/registro exige rol, validado con @PreAuthorize); ya no hay registro público.
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/salud", "/api/v1/catalogos/**", "/api/v1/pruebas/estado").permitAll()
                .requestMatchers("/api/v1/docs/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/").permitAll()
                .requestMatchers("/error").permitAll()
                // Lectura: cualquier usuario autenticado (la propiedad se valida en cada controlador)
                .requestMatchers(HttpMethod.GET, "/api/v1/**").authenticated()
                // Única escritura permitida a la jefa del DGTV
                .requestMatchers(HttpMethod.PATCH, "/api/v1/notificaciones/*/leida").authenticated()
                .requestMatchers(HttpMethod.PATCH, "/api/v1/mensajes/*/leido").authenticated()
                // Escritura: solo roles operativos; la jefa del DGTV recibe 403. ADMIN hereda COORDINADOR por la
                // jerarquía de roles, pero se nombra aquí para que la regla sea explícita.
                .requestMatchers("/api/v1/**").hasAnyRole("ALUMNO", "FACILITADOR", "COORDINADOR", "ADMIN")
                .anyRequest().denyAll())
            .addFilterBefore(new JwtAuthFilter(jwtService, userDetailsService), UsernamePasswordAuthenticationFilter.class);
        if (modoPruebas) {
            // Se ejecuta después del filtro JWT: un token válido siempre tiene prioridad.
            http.addFilterBefore(new ModoPruebasFilter(userDetailsService, usuarioRepository, respuestas,
                    usuarioPruebaPorOmision),
                    AnonymousAuthenticationFilter.class);
            log.warn("=== MODO PRUEBAS ACTIVO: la API acepta peticiones sin login (usuario por omisión: '{}'). "
                    + "Desactívalo con MODO_PRUEBAS=false antes de producción. ===", usuarioPruebaPorOmision);
        }
        return http.build();
    }

    /**
     * Jerarquía de roles: el administrador implica al coordinador, así hace todo lo del coordinador con las mismas
     * reglas {@code @PreAuthorize} existentes, sin accesos ocultos. Las acciones propias del administrador (la
     * auditoría) usan {@code hasRole('ADMIN')}.
     */
    @Bean
    static RoleHierarchy roleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role("ADMIN").implies("COORDINADOR")
                .build();
    }

    /** Aplica la jerarquía de roles también a la seguridad por método ({@code @PreAuthorize}). */
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(RoleHierarchy roleHierarchy) {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setRoleHierarchy(roleHierarchy);
        return handler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> lista = Arrays.stream(origenes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(lista);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", ModoPruebasFilter.CABECERA));
        cfg.setExposedHeaders(List.of("Location", "Content-Disposition"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
