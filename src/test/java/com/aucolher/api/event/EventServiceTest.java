package com.aucolher.api.event;

import com.aucolher.api.event.dto.EventResponseDTO;
import com.aucolher.api.event.entity.Event;
import com.aucolher.api.event.entity.EventCategory;
import com.aucolher.api.event.entity.EventStatus;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Exclusão da conta da ONG: o que acontece com os eventos dela, com o banco simulado (Mockito). */
@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository events;
    @Mock
    private EventAttendanceRepository attendances;
    @Mock
    private UserRepository users;

    @InjectMocks
    private EventService service;

    /** A consulta só traz os eventos ACTIVE de hoje em diante: os passados ficam como estão. */
    @Test
    void deletingTheNgoAccountCancelsUpcomingEventsWithAttendeesAndDeletesTheOthers() {
        Event withAttendees = event(4L, LocalDate.now().plusDays(3));
        Event empty = event(5L, LocalDate.now());
        when(events.findByNgoIdAndStatusAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(
                1L, EventStatus.ACTIVE, LocalDate.now())).thenReturn(List.of(empty, withAttendees));
        when(attendances.countByEventId(4L)).thenReturn(2L);
        when(attendances.countByEventId(5L)).thenReturn(0L);

        service.cancelUpcomingOf(1L);

        // Quem confirmou vê "Evento cancelado" em Minha conta
        assertThat(withAttendees.getStatus()).isEqualTo(EventStatus.CANCELLED);
        assertThat(withAttendees.isRemovedByModeration()).isFalse();
        verify(events).delete(empty);
        verify(events, never()).delete(withAttendees);
    }

    @Test
    void attendeeStillSeesTheCancelledEventOfADeletedNgoButNotTheEventsOfABannedOne() {
        User attendee = User.builder().id(9L).email("person@email.com").userType(UserType.PERSON).build();
        User deletedNgo = ngo(1L);
        deletedNgo.anonymize(LocalDateTime.now());
        User bannedNgo = ngo(2L);
        bannedNgo.setActive(false);
        User activeNgo = ngo(3L);
        Event cancelled = event(4L, LocalDate.now().plusDays(3));
        cancelled.setStatus(EventStatus.CANCELLED);
        Event ofBanned = event(5L, LocalDate.now().plusDays(3));
        ofBanned.setNgoId(2L);
        Event ofActive = event(6L, LocalDate.now().plusDays(3));
        ofActive.setNgoId(3L);
        when(users.findByEmail("person@email.com")).thenReturn(Optional.of(attendee));
        when(events.findAttendedBy(9L)).thenReturn(List.of(cancelled, ofBanned, ofActive));
        when(users.findAllById(any())).thenReturn(List.of(deletedNgo, bannedNgo, activeNgo));

        List<EventResponseDTO> attending = service.listAttending(
                new UsernamePasswordAuthenticationToken("person@email.com", null, List.of()));

        assertThat(attending).extracting(EventResponseDTO::id).containsExactly(4L, 6L);
        assertThat(attending.get(0).status()).isEqualTo("CANCELLED");
        assertThat(attending.get(0).ngo().name()).isEqualTo("Conta excluída");
    }

    private static User ngo(Long id) {
        return User.builder().id(id).name("ONG " + id).email("ngo" + id + "@email.com").userType(UserType.NGO)
                .cnpj("1122233300018" + id).active(true).build();
    }

    private static Event event(Long id, LocalDate date) {
        Event event = new Event();
        event.setId(id);
        event.setNgoId(1L);
        event.setTitle("Feira de adoção");
        event.setCategory(EventCategory.ADOPTION_FAIR);
        event.setDate(date);
        event.setStatus(EventStatus.ACTIVE);
        return event;
    }
}
