package com.aucolher.api.event;

import com.aucolher.api.event.dto.AttendeeDTO;
import com.aucolher.api.event.dto.EventRequestDTO;
import com.aucolher.api.event.dto.EventResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService service;

    // ---- públicas
    @GetMapping
    public List<EventResponseDTO> list(@RequestParam(required = false) Long ngoId) {
        return service.listPublic(ngoId);
    }

    @GetMapping("/{id}")
    public EventResponseDTO get(@PathVariable Long id) {
        return service.getById(id);
    }

    // ---- rotas fixas (o Spring prefere o caminho literal a /{id})
    @GetMapping("/mine")
    public List<EventResponseDTO> mine(Authentication auth) {
        return service.listMine(auth);
    }

    @GetMapping("/attending")
    public List<EventResponseDTO> attending(Authentication auth) {
        return service.listAttending(auth);
    }

    // ---- ONG
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponseDTO create(@Valid @RequestBody EventRequestDTO body, Authentication auth) {
        return service.create(body, auth);
    }

    @PutMapping("/{id}")
    public EventResponseDTO update(@PathVariable Long id,
                                   @Valid @RequestBody EventRequestDTO body,
                                   Authentication auth) {
        return service.update(id, body, auth);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, Authentication auth) {
        service.delete(id, auth);
    }

    @GetMapping("/{id}/attendees")
    public List<AttendeeDTO> attendees(@PathVariable Long id, Authentication auth) {
        return service.listAttendees(id, auth);
    }

    // ---- presença (qualquer conta logada)
    @PostMapping("/{id}/attendance")
    public EventResponseDTO confirm(@PathVariable Long id, Authentication auth) {
        return service.confirmAttendance(id, auth);
    }

    @DeleteMapping("/{id}/attendance")
    public EventResponseDTO cancel(@PathVariable Long id, Authentication auth) {
        return service.cancelAttendance(id, auth);
    }
}
