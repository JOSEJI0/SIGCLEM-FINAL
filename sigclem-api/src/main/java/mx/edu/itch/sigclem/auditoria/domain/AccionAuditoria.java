package mx.edu.itch.sigclem.auditoria.domain;

/** Códigos de las acciones que se registran en la bitácora de auditoría. */
public enum AccionAuditoria {
    INICIO_SESION("Inicio de sesión"),
    INICIO_SESION_FALLIDO("Intento de inicio de sesión fallido"),
    CUENTA_BLOQUEADA("Cuenta bloqueada por intentos fallidos"),
    CONTRASENA_PROPIA("Cambio de la contraseña propia"),
    CONTRASENA_ASIGNADA("Contraseña asignada a otra cuenta"),
    ACTA_CERRADA_COORDINACION("Acta cerrada por coordinación"),
    NIVEL_ASIGNADO("Nivel asignado de forma individual"),
    ALUMNO_CREADO("Cuenta de alumno creada"),
    ALUMNOS_IMPORTADOS("Alumnos importados desde Excel"),
    ALUMNO_BAJA("Alumno dado de baja"),
    ALUMNO_REACTIVADO("Alumno reactivado"),
    IDIOMA_LIBERADO("Lengua extranjera liberada");

    private final String descripcion;

    AccionAuditoria(String descripcion) {
        this.descripcion = descripcion;
    }

    public String descripcion() {
        return descripcion;
    }
}
