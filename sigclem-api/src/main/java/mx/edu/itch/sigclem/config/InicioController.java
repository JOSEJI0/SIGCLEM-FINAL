package mx.edu.itch.sigclem.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import io.swagger.v3.oas.annotations.Hidden;

/** Al abrir http://localhost:8080/ se muestra directamente la ventana con todos los endpoints (Swagger UI). */
@Hidden
@Controller
public class InicioController {

    @GetMapping("/")
    public String inicio() {
        return "redirect:/api/v1/docs/ui";
    }
}
