package mx.edu.itch.sigclem.seguridad.domain;

import java.util.Arrays;

/** Roles del sistema. {@code nombreBd} coincide con la columna rol.nombre (migración V2). */
public enum RolSistema {
    ALUMNO("Alumno"),
    FACILITADOR("Facilitador"),
    COORDINADOR("Coordinador"),
    JEFA_DGTV("Jefa del DGTV"),
    /** Mayor privilegio: por la jerarquía de roles hace todo lo del coordinador y además ve la auditoría. */
    ADMIN("Administrador");

    private final String nombreBd;

    RolSistema(String nombreBd) {
        this.nombreBd = nombreBd;
    }

    public String nombreBd() {
        return nombreBd;
    }

    /** Autoridad de Spring Security, por ejemplo ROLE_COORDINADOR. */
    public String autoridad() {
        return "ROLE_" + name();
    }

    public static RolSistema desdeNombreBd(String nombre) {
        return Arrays.stream(values())
                .filter(r -> r.nombreBd.equalsIgnoreCase(nombre))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Rol desconocido: " + nombre));
    }
}
