package mx.edu.itch.sigclem.seguridad.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import mx.edu.itch.sigclem.alumnos.repository.AlumnoRepository;
import mx.edu.itch.sigclem.facilitadores.repository.FacilitadorRepository;
import mx.edu.itch.sigclem.seguridad.domain.RolSistema;
import mx.edu.itch.sigclem.seguridad.dto.PerfilResponse;

/** Obtiene el nombre para mostrar según el rol del usuario autenticado. */
@Service
@RequiredArgsConstructor
public class PerfilService {

    private final AlumnoRepository alumnoRepository;
    private final FacilitadorRepository facilitadorRepository;

    @Transactional(readOnly = true)
    public PerfilResponse perfil(UsuarioAutenticado u) {
        String nombre = u.getUsername();
        Boolean hablaLenguaMaterna = null;
        if (u.rol() == RolSistema.ALUMNO) {
            var alumno = alumnoRepository.findByUsuarioId(u.id());
            nombre = alumno.map(a -> a.getNombre() + " " + a.getApellidoPaterno()).orElse(nombre);
            hablaLenguaMaterna = alumno.map(a -> a.isHablaLenguaMaterna()).orElse(false);
        } else if (u.rol() == RolSistema.FACILITADOR) {
            nombre = facilitadorRepository.findByUsuarioId(u.id())
                    .map(f -> f.getNombre() + " " + f.getApellidoPaterno())
                    .orElse(nombre);
        }
        return new PerfilResponse(u.id(), u.getUsername(), nombre, u.rol().name(), hablaLenguaMaterna);
    }
}
