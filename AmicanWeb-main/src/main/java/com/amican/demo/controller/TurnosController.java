    package com.amican.demo.controller;

    import com.amican.demo.dto.DueniosDto;
    import com.amican.demo.dto.MascotaDto;
    import com.amican.demo.dto.TurnoDto;
    import com.amican.demo.model.Turnos;
    import com.amican.demo.model.enums.Estados;
    import com.amican.demo.service.DueniosService;
    import com.amican.demo.service.MascotasService;
    import com.amican.demo.service.TurnosService;
    import lombok.RequiredArgsConstructor;
    import org.springframework.stereotype.Controller;
    import org.springframework.ui.Model;
    import org.springframework.web.bind.annotation.*;
    import org.springframework.web.servlet.mvc.support.RedirectAttributes;
    import java.time.format.DateTimeFormatter;
    import java.util.ArrayList;
    import java.util.List;
    import java.util.Optional;
    import java.util.Set;
    import java.util.stream.Collectors;

    @Controller
    @RequestMapping("/turnos")
    @RequiredArgsConstructor
    public class TurnosController {

        private final DueniosService dueniosService;
        private final TurnosService turnosService;
        private final MascotasService mascotasService;

        @GetMapping("/ver")
        public String mostrarTurnos(Model model) {
            try {
                List<Turnos> turnos = turnosService.findAll();
                model.addAttribute("turnos", turnos);
                return "verTurnos";
            } catch (Exception e) {
                model.addAttribute("error", "Error al cargar los turnos: " + e.getMessage());
                return "verTurnos";
            }
        }

        // 1. MOSTRAR FORMULARIO DE SELECCIÓN DE DUEÑO (VISTA ACTUAL)
        @GetMapping("/crear")
        public String mostrarFormularioDuenio(Model model) {
            List<DueniosDto> duenios = dueniosService.getAllDueniosDto();
            model.addAttribute("duenios", duenios);
            model.addAttribute("turnoDto", new TurnoDto());
            return "crearAgenda";
        }

        // 2. PROCESAR SELECCIÓN DE DUEÑO (REDIRIGIR A MASCOTAS)
        @PostMapping("/seleccionar-duenio")
        public String seleccionarDuenio(@RequestParam("duenioId") Long duenioId, RedirectAttributes redirectAttributes) {
            try {
                Optional<DueniosDto> duenio = dueniosService.getDuenioById(duenioId);

                if (duenio.isPresent()) {
                    return "redirect:/turnos/crear/" + duenioId;
                } else {
                    redirectAttributes.addFlashAttribute("error", "Dueño no encontrado");
                    return "redirect:/turnos/crear";
                }

            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Error al buscar el dueño: " + e.getMessage());
                return "redirect:/turnos/crear";
            }
        }

        // 3. MOSTRAR FORMULARIO DE MASCOTAS DEL DUEÑO (NUEVA VISTA)
        @GetMapping("/crear/{duenioId}")
        public String mostrarFormularioMascotas(
                @PathVariable Long duenioId,
                Model model,
                RedirectAttributes redirectAttributes) {

            try {
                Optional<DueniosDto> duenio = dueniosService.getDuenioById(duenioId);

                if (duenio.isEmpty()) {
                    redirectAttributes.addFlashAttribute("error", "Dueño no encontrado");
                    return "redirect:/turnos/crear";
                }

                // Obtener mascotas del dueño usando el servicio que ya tienes
                List<MascotaDto> mascotas = mascotasService.getMascotasByDuenio(duenioId);

                model.addAttribute("duenio", duenio.get());
                model.addAttribute("mascotas", mascotas);
                model.addAttribute("turnoDto", new TurnoDto());

                return "seleccionar-mascotas";

            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Error al cargar el formulario: " + e.getMessage());
                return "redirect:/turnos/crear";
            }
        }

        // 4. CREAR TURNO FINAL (CON MASCOTAS)
        @PostMapping("/crear")
        public String crearTurno(
                @ModelAttribute TurnoDto turnoDto,
                @RequestParam Long duenioId, // ← Añadir esto
                @RequestParam List<Long> mascotaIds,
                RedirectAttributes redirectAttributes) {

            try {
                // Asignar ambos IDs al DTO
                turnoDto.setDuenioId(duenioId);
                turnoDto.setMascotaIds(mascotaIds);
                turnoDto.setEstados(Estados.ANOTADO);

                turnosService.save(turnoDto);
                redirectAttributes.addFlashAttribute("success", "Turno creado exitosamente");
                return "redirect:/turnos/ver";

            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Error al crear el turno: " + e.getMessage());
                return "redirect:/turnos/crear/" + duenioId;
            }
        }

        // Redirigir a edición (solo para consistencia de URL)
        @GetMapping("/editar/{id}")
        public String redirigirAEdicion(@PathVariable Long id, RedirectAttributes redirectAttributes) {
            if (!turnosService.findById(id).isPresent()) {
                redirectAttributes.addFlashAttribute("error", "Turno no encontrado");
                return "redirect:/turnos/ver";
            }
            return "redirect:/turnos/editar/" + id + "/mascotas";
        }

        // Mostrar formulario de edición
        @GetMapping("/editar/{id}/mascotas")
        public String mostrarFormularioEdicion(
                @PathVariable Long id,
                Model model,
                RedirectAttributes redirectAttributes) {

            Optional<TurnoDto> turnoOpt = turnosService.getTurnoDtoById(id);
            if (turnoOpt.isEmpty() || turnoOpt.get().getDuenioId() == null) {
                redirectAttributes.addFlashAttribute("error", "Turno inválido o sin dueño");
                return "redirect:/turnos/ver";
            }

            TurnoDto turno = turnoOpt.get();
            Long duenioId = turno.getDuenioId();

            Optional<DueniosDto> duenioOpt = dueniosService.getDuenioById(duenioId);
            if (duenioOpt.isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Dueño del turno no encontrado");
                return "redirect:/turnos/ver";
            }
            String fechaFormateada = "";
            if (turno.getFecha() != null) {
                fechaFormateada = turno.getFecha().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            }
            model.addAttribute("fechaFormateada", fechaFormateada);

            List<MascotaDto> mascotas = mascotasService.getMascotasByDuenio(duenioId);

            model.addAttribute("duenio", duenioOpt.get());
            model.addAttribute("mascotas", mascotas);
            model.addAttribute("turnoDto", turno);
            model.addAttribute("modoEdicion", true); // ← clave para la vista

            return "seleccionar-mascotas-editversion";
        }

        // Guardar edición
        @PostMapping("/editar")
        public String editarTurno(
                @ModelAttribute TurnoDto turnoDto,
                @RequestParam Long id,
                @RequestParam(required = false) List<Long> mascotaIds,
                RedirectAttributes redirectAttributes) {

            try {
                // Normalizar mascotaIds (puede ser null si no se selecciona ninguna)
                if (mascotaIds == null) {
                    mascotaIds = new ArrayList<>();
                }

                // Validar que las mascotas pertenezcan al dueño
                Long duenioId = turnoDto.getDuenioId();
                List<MascotaDto> mascotasDelDuenio = mascotasService.getMascotasByDuenio(duenioId);
                Set<Long> idsPermitidos = mascotasDelDuenio.stream()
                        .map(MascotaDto::getIdMascotas)
                        .collect(Collectors.toSet());

                if (!idsPermitidos.containsAll(mascotaIds)) {
                    redirectAttributes.addFlashAttribute("error", "Alguna mascota no pertenece al dueño");
                    return "redirect:/turnos/editar/" + id + "/mascotas";
                }

                // Actualizar el DTO
                turnoDto.setIdTurnos(id);
                turnoDto.setMascotaIds(mascotaIds);

                // Ejecutar actualización
                turnosService.updateTurno(id, turnoDto);

                redirectAttributes.addFlashAttribute("success", "Turno actualizado exitosamente");
                return "redirect:/turnos/ver";

            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Error al actualizar: " + e.getMessage());
                return "redirect:/turnos/editar/" + id + "/mascotas";
            }
        }

        @GetMapping("/borrar/{id}")
        public String eliminarTurno(@PathVariable Long id, RedirectAttributes redirectAttributes) {
            try {
                turnosService.deleteById(id);
                redirectAttributes.addFlashAttribute("success", "Turno eliminado correctamente");
            } catch (Exception e) {
                redirectAttributes.addFlashAttribute("error", "Error al eliminar el turno: " + e.getMessage());
            }
            return "redirect:/turnos/ver";
        }

    }
