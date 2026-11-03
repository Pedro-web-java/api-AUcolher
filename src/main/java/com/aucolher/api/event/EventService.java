package com.aucolher.api.event;

import com.aucolher.api.event.dto.*;
import com.aucolher.api.event.entity.Event;
import com.aucolher.api.event.entity.EventAttendance;
import com.aucolher.api.event.entity.EventCategory;
import com.aucolher.api.event.entity.EventStatus;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.shared.validation.PhotoUrl;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EventService {

    private static final Pattern COVER_PATTERN = Pattern.compile(
            "^(https?://\\S+|data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+)$");

    private final EventRepository events;
    private final EventAttendanceRepository attendances;
    private final UserRepository users;

    // ---------------------------------------------------------------- leitura

    @Transactional(readOnly = true)
    public List<EventResponseDTO> listPublic(Long ngoId) {
        LocalDate today = LocalDate.now();
        List<Event> list = ngoId == null
                ? events.findByStatusAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(
                EventStatus.ACTIVE, today)
                : events.findByNgoIdAndStatusAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(
                ngoId, EventStatus.ACTIVE, today);
        return toPublicDtos(list);
    }

    @Transactional(readOnly = true)
    public EventResponseDTO getById(Long id) {
        return toDto(findVisible(id));
    }

    /** Painel do admin: todos os eventos, em qualquer status, com a situação da ONG. */
    @Transactional(readOnly = true)
    public List<EventResponseDTO> listForAdmin() {
        return toDtos(events.findAllByOrderByDateDescStartTimeDesc());
    }

    @Transactional(readOnly = true)
    public List<EventResponseDTO> listMine(Authentication auth) {
        User user = currentUser(auth);
        requireNgo(user, "Apenas contas de ONG possuem eventos próprios.");
        return toDtos(events.findByNgoIdAndStatusOrderByDateDescStartTimeDesc(
                user.getId(), EventStatus.ACTIVE));
    }

    /**
     * "Eventos que vou": o de ONG banida some, como nas rotas públicas; o de
     * ONG que excluiu a conta fica, para quem confirmou ver "Evento cancelado".
     */
    @Transactional(readOnly = true)
    public List<EventResponseDTO> listAttending(Authentication auth) {
        User user = currentUser(auth);
        List<Event> attended = events.findAttendedBy(user.getId());
        Map<Long, User> ngos = usersById(attended.stream().map(Event::getNgoId).toList());
        return toDtos(attended.stream()
                .filter(e -> ngos.containsKey(e.getNgoId())
                        && (Boolean.TRUE.equals(ngos.get(e.getNgoId()).getActive()) || ngos.get(e.getNgoId()).isDeleted()))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<AttendeeDTO> listAttendees(Long id, Authentication auth) {
        User user = currentUser(auth);
        Event event = findActive(id);                 // 404
        requireOwner(event, user);                    // 403
        List<EventAttendance> list = attendances.findByEventIdOrderByConfirmedAtAsc(id);
        Map<Long, User> byId = usersById(list.stream().map(EventAttendance::getUserId).toList());
        return list.stream().map(a -> {
            User u = byId.get(a.getUserId());
            return new AttendeeDTO(
                    new AttendeeUserDTO(a.getUserId(),
                            u != null ? u.getName() : null,
                            u != null ? u.getPhotoUrl() : null),
                    a.getConfirmedAt());
        }).toList();
    }

    // --------------------------------------------------------------- escrita

    @Transactional
    public EventResponseDTO create(EventRequestDTO req, Authentication auth) {
        User user = currentUser(auth);
        requireNgo(user, "Apenas contas de ONG podem criar eventos.");
        validate(req);
        Event event = new Event();
        apply(event, req);
        event.setNgoId(user.getId());
        event.setStatus(EventStatus.ACTIVE);
        return toDto(events.save(event));
    }

    @Transactional
    public EventResponseDTO update(Long id, EventRequestDTO req, Authentication auth) {
        User user = currentUser(auth);
        Event event = lockActive(id);                 // 404
        requireOwner(event, user);                    // 403
        return save(event, req);
    }

    /**
     * Moderação: o admin edita o evento de uma ONG, com as mesmas regras dela
     * (só ativo e que ainda não aconteceu). O evento continua em nome da ONG,
     * e fica registrado que a moderação o editou.
     */
    @Transactional
    public EventResponseDTO updateByModeration(Long id, EventRequestDTO req, Long adminId) {
        Event event = lockActive(id);                 // 404
        event.markModeratedBy(adminId);
        return save(event, req);
    }

    /** Validação e gravação comuns à ONG dona e ao admin. */
    private EventResponseDTO save(Event event, EventRequestDTO req) {
        if (isPast(event)) {                          // 400
            throw new BusinessException("Eventos que já aconteceram não podem ser editados.");
        }
        validate(req);
        long confirmed = attendances.countByEventId(event.getId());
        if (req.capacity() != null && req.capacity() < confirmed) {
            throw new BusinessException(
                    "A capacidade não pode ser menor que o número de confirmados (" + confirmed + ").");
        }
        apply(event, req);
        return toDto(events.save(event));
    }

    @Transactional
    public void delete(Long id, Authentication auth) {
        User user = currentUser(auth);
        Event event = lockActive(id);                 // 404
        requireOwner(event, user);                    // 403
        if (isPast(event)) {                          // 400
            throw new BusinessException("Eventos que já aconteceram não podem ser excluídos.");
        }
        if (attendances.countByEventId(id) == 0) {
            events.delete(event);                     // exclusão real
        } else {
            event.setStatus(EventStatus.CANCELLED);   // exclusão lógica; presenças ficam gravadas
            events.save(event);
        }
    }

    /**
     * Moderação: o admin remove o evento. Ele vira CANCELLED com
     * removed_by_moderation, mesmo sem confirmados (a linha fica, para o admin
     * ver o que removeu). Já cancelado ou inexistente: 404.
     */
    @Transactional
    public void removeByModeration(Long id) {
        Event event = lockActive(id);
        event.setStatus(EventStatus.CANCELLED);
        event.setRemovedByModeration(true);
    }

    /**
     * Exclusão da conta da ONG: os eventos futuros saem como no "Excluir" de
     * sempre. Com confirmados, viram CANCELLED (quem confirmou vê "Evento
     * cancelado"); sem confirmados, são apagados de verdade. Os passados ficam.
     */
    @Transactional
    public void cancelUpcomingOf(Long ngoId) {
        List<Event> upcoming = events.findByNgoIdAndStatusAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(
                ngoId, EventStatus.ACTIVE, LocalDate.now());
        for (Event event : upcoming) {
            if (attendances.countByEventId(event.getId()) == 0) {
                events.delete(event);
            } else {
                event.setStatus(EventStatus.CANCELLED);
            }
        }
    }

    // -------------------------------------------------------------- presença

    @Transactional
    public EventResponseDTO confirmAttendance(Long id, Authentication auth) {
        User user = currentUser(auth);
        requireNotAdmin(user);
        Event event = lockExisting(id);               // trava a linha: vagas sem corrida
        requireActiveNgo(event);
        checkAttendanceRules(event, user);
        if (!attendances.existsByEventIdAndUserId(id, user.getId())) {   // idempotente
            if (event.getCapacity() != null
                    && attendances.countByEventId(id) >= event.getCapacity()) {
                throw new BusinessException("Vagas esgotadas para este evento.");
            }
            attendances.save(new EventAttendance(id, user.getId()));
            attendances.flush();
        }
        return toDto(event);
    }

    @Transactional
    public EventResponseDTO cancelAttendance(Long id, Authentication auth) {
        User user = currentUser(auth);
        requireNotAdmin(user);
        Event event = lockExisting(id);
        requireActiveNgo(event);
        checkAttendanceRules(event, user);
        attendances.deleteAttendance(id, user.getId());                  // idempotente
        return toDto(event);
    }

    // ---------------------------------------------------------------- regras

    /** Recusas (400) de confirmar/cancelar presença, na ordem do contrato. */
    private void checkAttendanceRules(Event event, User user) {
        if (isPast(event)) {
            throw new BusinessException("Este evento já aconteceu.");
        }
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new BusinessException("Este evento foi cancelado.");
        }
        if (event.getNgoId().equals(user.getId())) {
            throw new BusinessException("Você não pode confirmar presença no seu próprio evento.");
        }
    }

    private boolean isPast(Event event) {
        return event.getDate().isBefore(LocalDate.now());   // evento de hoje segue aberto
    }

    private void validate(EventRequestDTO req) {
        if (req.category() == EventCategory.OTHER && req.customCategory() == null) {
            throw new BusinessException("Dê um nome para a categoria.");
        }
        if (req.endTime() != null && !req.endTime().isAfter(req.startTime())) {
            throw new BusinessException("O horário de término deve ser depois do início.");
        }
        String cover = req.coverUrl();
        if (cover != null) {
            if (cover.length() > PhotoUrl.COVER_MAX_LENGTH) {
                throw new BusinessException("A imagem de capa é grande demais.");
            }
            if (!COVER_PATTERN.matcher(cover).matches()) {
                throw new BusinessException(
                        "A capa deve ser um link http(s) ou uma imagem JPEG, PNG ou WebP.");
            }
        }
    }

    private void apply(Event e, EventRequestDTO r) {
        e.setTitle(r.title());
        e.setDescription(r.description());
        e.setCategory(r.category());
        e.setCustomCategory(r.category() == EventCategory.OTHER ? r.customCategory() : null);
        e.setDate(r.date());
        e.setStartTime(r.startTime());
        e.setEndTime(r.endTime());
        e.setVenueName(r.venueName());
        e.setCep(r.cep());
        e.setStreet(r.street());
        e.setNumber(r.number());
        e.setComplement(r.complement());
        e.setDistrict(r.district());
        e.setCity(r.city());
        e.setState(r.state());
        e.setCapacity(r.capacity());
        e.setCoverUrl(r.coverUrl());
    }

    // --------------------------------------------------------------- acessos

    private Event findActive(Long id) {
        return events.findById(id)
                .filter(e -> e.getStatus() == EventStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
    }

    /** O detalhe público: evento ativo de uma ONG que não foi banida pela moderação. */
    private Event findVisible(Long id) {
        Event event = findActive(id);
        requireActiveNgo(event);
        return event;
    }

    /** ONG banida: os eventos dela somem do site (404), e voltam se ela for reativada. */
    private void requireActiveNgo(Event event) {
        boolean active = users.findById(event.getNgoId()).map(ngo -> Boolean.TRUE.equals(ngo.getActive())).orElse(false);
        if (!active) {
            throw new ResourceNotFoundException("Evento não encontrado.");
        }
    }

    private Event lockActive(Long id) {
        return events.findByIdForUpdate(id)
                .filter(e -> e.getStatus() == EventStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
    }

    /** Rotas de presença: só 404 se não existe; CANCELLED vira o 400 do contrato. */
    private Event lockExisting(Long id) {
        return events.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evento não encontrado."));
    }

    private void requireNgo(User user, String message) {
        if (!isNgo(user)) {
            throw new ForbiddenException(message);
        }
    }

    /** O admin modera o site, mas não participa dele. */
    private void requireNotAdmin(User user) {
        if (user.getUserType() == UserType.ADMIN) {
            throw new ForbiddenException("Contas de admin não confirmam presença em eventos.");
        }
    }

    private void requireOwner(Event event, User user) {
        if (!isNgo(user) || !event.getNgoId().equals(user.getId())) {
            throw new ForbiddenException("Apenas a ONG dona do evento pode fazer isso.");
        }
    }

    /** Usuário logado. Assume que o subject do JWT / Authentication#getName() é o e-mail. */
    private User currentUser(Authentication auth) {
        return users.findByEmail(auth.getName())
                .orElseThrow(() -> new ForbiddenException("Usuário não encontrado."));
    }

    private boolean isNgo(User user) {
        return user.getUserType() == UserType.NGO;
    }

    private NgoSummaryDTO toNgo(User ngo) {
        if (ngo == null) {
            return null;
        }
        return new NgoSummaryDTO(ngo.getId(), ngo.getName(), Boolean.TRUE.equals(ngo.getVerified()), ngo.getPhotoUrl(),
                Boolean.TRUE.equals(ngo.getActive()));
    }

    private Map<Long, User> usersById(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private EventResponseDTO toDto(Event event) {
        return toDtos(List.of(event)).get(0);
    }

    /** Rotas públicas: os eventos de ONG banida pela moderação somem. */
    private List<EventResponseDTO> toPublicDtos(List<Event> list) {
        Map<Long, User> ngos = usersById(list.stream().map(Event::getNgoId).toList());
        return toDtos(list.stream()
                .filter(e -> ngos.containsKey(e.getNgoId()) && Boolean.TRUE.equals(ngos.get(e.getNgoId()).getActive()))
                .toList());
    }

    /** Monta os DTOs com 2 consultas extras (contagens e ONGs), sem N+1. */
    private List<EventResponseDTO> toDtos(List<Event> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> eventIds = list.stream().map(Event::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : attendances.countByEventIds(eventIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        Map<Long, User> ngos = usersById(list.stream().map(Event::getNgoId).toList());
        return list.stream()
                .map(e -> EventResponseDTO.from(e, counts.getOrDefault(e.getId(), 0L),
                        toNgo(ngos.get(e.getNgoId()))))
                .toList();
    }
}