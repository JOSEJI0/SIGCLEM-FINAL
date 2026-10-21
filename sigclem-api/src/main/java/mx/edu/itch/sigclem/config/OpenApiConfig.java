package mx.edu.itch.sigclem.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import mx.edu.itch.sigclem.seguridad.config.ModoPruebasFilter;

/** Especificación OpenAPI 3 de la API (decisión DA-10). Swagger UI: /api/v1/docs/ui */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearerAuth";
    private static final String ESQUEMA_PRUEBAS = "usuarioPrueba";

    @Value("${app.modo-pruebas.activo:false}")
    private boolean modoPruebas;

    @Value("${app.modo-pruebas.usuario-por-omision:}")
    private String usuarioPorOmision;

    @Bean
    public OpenAPI sigclemOpenApi() {
        String descripcion = "API REST de la Coordinación de Lenguas Extranjeras y Maternas del ITCH.";
        if (modoPruebas) {
            descripcion += """


                    **MODO PRUEBAS ACTIVO: no se requiere iniciar sesión.**

                    * Sin hacer nada, cada petición se atiende como **%s** (coordinador).
                    * Para actuar con otro rol pulsa **Authorize** y escribe en *usuarioPrueba* la cuenta deseada:
                      número de control para un alumno, correo para un facilitador o el usuario de la jefa del DGTV.
                    * `GET /api/v1/pruebas/usuarios` lista las cuentas disponibles y
                      `POST /api/v1/pruebas/datos-demo` crea un periodo, alumnos, facilitador, tarifas y un grupo de ejemplo.
                    * Los roles y las reglas de negocio se siguen validando igual que con login.
                    """.formatted(usuarioPorOmision);
        }

        Components componentes = new Components().addSecuritySchemes(ESQUEMA_JWT,
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Token obtenido en POST /api/v1/auth/login."));
        OpenAPI openApi = new OpenAPI()
                .info(new Info()
                        .title("SIGCLEM API")
                        .version("v1")
                        .description(descripcion))
                .components(componentes)
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));

        if (modoPruebas) {
            componentes.addSecuritySchemes(ESQUEMA_PRUEBAS, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name(ModoPruebasFilter.CABECERA)
                    .description("Cuenta con la que se ejecutan las peticiones en modo pruebas (por omisión: "
                            + usuarioPorOmision + ")."));
            // Requisitos alternativos: basta con uno de los dos (o ninguno en modo pruebas).
            openApi.addSecurityItem(new SecurityRequirement().addList(ESQUEMA_PRUEBAS));
        }
        return openApi;
    }
}
