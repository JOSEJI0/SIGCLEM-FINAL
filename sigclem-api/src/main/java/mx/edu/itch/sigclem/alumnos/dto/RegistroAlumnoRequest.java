package mx.edu.itch.sigclem.alumnos.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos para que el coordinador cree la cuenta de un alumno (CU-02, cambio 15). El registro ya no es público: la
 * cuenta siempre tiene rol Alumno y su contraseña inicial es el número de control (el coordinador la cambia después
 * en «Cuentas y contraseñas»).
 */
public record RegistroAlumnoRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9]{1,10}", message = "Solo letras mayúsculas y dígitos, máximo 10")
        String numeroControl,
        @NotBlank @Size(max = 60) String nombre,
        @NotBlank @Size(max = 40) String apellidoPaterno,
        @Size(max = 40) String apellidoMaterno,
        @NotBlank @Pattern(regexp = "[HM]", message = "El sexo debe ser H (hombre) o M (mujer)") String sexo,
        @NotNull Integer idCarrera,
        @NotNull @Min(1) @Max(12) Integer semestre,
        @Email @Size(max = 80) String correo,
        /** ¿Habla alguna lengua materna (náhuatl, me'phaa, tu'un savi…)? Si se omite, se toma como no. */
        Boolean hablaLenguaMaterna) {
}
