package com.aucolher.api.admin;

import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.animal.dto.AnimalRequestDTO;
import com.aucolher.api.animal.entity.*;
import com.aucolher.api.event.EventAttendanceRepository;
import com.aucolher.api.event.EventRepository;
import com.aucolher.api.event.EventService;
import com.aucolher.api.event.dto.EventRequestDTO;
import com.aucolher.api.event.entity.Event;
import com.aucolher.api.event.entity.EventCategory;
import com.aucolher.api.event.entity.EventStatus;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ConflictException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.UserService;
import com.aucolher.api.user.dto.ProfileUpdateDTO;
import com.aucolher.api.user.entity.AuthProvider;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Edição pela moderação: o admin edita contas, anúncios e eventos de outras
 * pessoas. O conteúdo continua em nome do dono, com a marca da moderação.
 */
@ExtendWith(MockitoExtension.class)
class ModerationEditsTest {

    static final Long ADMIN_ID = 9L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventAttendanceRepository attendanceRepository;

    // ===================== Animais =====================

    @Test
    void adminEditsAListingThatStaysWithItsOwner() {
        Animal listing = animal(AnimalStatus.ADOPTED);
        when(animalRepository.findById(10L)).thenReturn(Optional.of(listing));
        when(animalRepository.saveAndFlush(listing)).thenReturn(listing);

        var edited = animalService().updateByModeration(10L, animalRequest("Thor corrigido"), ADMIN_ID);

        assertThat(edited.name()).isEqualTo("Thor corrigido");
        assertThat(edited.owner().id()).isEqualTo(2L);
        assertThat(edited.moderatedAt()).isNotNull();
        assertThat(listing.getModeratedBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    void aRemovedListingIsNotEdited() {
        when(animalRepository.findById(10L)).thenReturn(Optional.of(animal(AnimalStatus.REMOVED)));

        assertThatThrownBy(() -> animalService().updateByModeration(10L, animalRequest("X"), ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Este anúncio foi removido pela moderação e não pode mais ser alterado.");
    }

    // ===================== Eventos =====================

    @Test
    void adminEditsAnUpcomingEvent() {
        Event event = event(LocalDate.now().plusDays(3));
        when(eventRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(event));
        when(eventRepository.save(event)).thenReturn(event);
        when(attendanceRepository.countByEventIds(any())).thenReturn(List.of());

        var edited = eventService().updateByModeration(3L, eventRequest("Feira corrigida"), ADMIN_ID);

        assertThat(edited.title()).isEqualTo("Feira corrigida");
        assertThat(edited.moderatedAt()).isNotNull();
        assertThat(event.getNgoId()).isEqualTo(2L);
        assertThat(event.getModeratedBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    void aPastEventIsNotEdited() {
        when(eventRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(event(LocalDate.now().minusDays(1))));

        assertThatThrownBy(() -> eventService().updateByModeration(3L, eventRequest("X"), ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Eventos que já aconteceram não podem ser editados.");
    }

    // ===================== Contas =====================

    @Test
    void adminEditsTheProfileAndTheLoginEmail() {
        User person = person(AuthProvider.LOCAL);
        when(userRepository.findById(5L)).thenReturn(Optional.of(person));
        when(userRepository.existsByEmail("novo@email.com")).thenReturn(false);

        User edited = userService().updateByModeration(5L, "novo@email.com", profile("Maria Souza"), ADMIN_ID);

        assertThat(edited.getEmail()).isEqualTo("novo@email.com");
        assertThat(edited.getName()).isEqualTo("Maria Souza");
        assertThat(edited.getModeratedAt()).isNotNull();
        assertThat(edited.getModeratedBy()).isEqualTo(ADMIN_ID);
    }

    @Test
    void keepingTheSameEmailSkipsTheEmailRules() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(person(AuthProvider.GOOGLE)));

        userService().updateByModeration(5L, "maria@email.com", profile("Maria"), ADMIN_ID);

        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void emailRules() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(person(AuthProvider.GOOGLE)));
        assertThatThrownBy(() -> userService().updateByModeration(5L, "novo@email.com", profile("M"), ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Esta conta entra pelo Google: o e-mail vem do Google e não pode ser alterado.");

        when(userRepository.findById(5L)).thenReturn(Optional.of(person(AuthProvider.LOCAL)));
        when(userRepository.existsByEmail("outra@email.com")).thenReturn(true);
        assertThatThrownBy(() -> userService().updateByModeration(5L, "outra@email.com", profile("M"), ADMIN_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Este e-mail já está em uso por outra conta.");
    }

    @Test
    void adminAccountsAreNotEdited() {
        when(userRepository.findById(9L)).thenReturn(Optional.of(
                User.builder().id(9L).email("admin@email.com").userType(UserType.ADMIN).build()));

        assertThatThrownBy(() -> userService().findForModeration(9L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Contas de admin não são editadas pelo painel.");
    }

    @Test
    void deletedAccountsAreNotEdited() {
        User deleted = person(AuthProvider.LOCAL);
        deleted.anonymize(LocalDateTime.now());
        when(userRepository.findById(5L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> userService().updateByModeration(5L, "maria@email.com", profile("Maria"), ADMIN_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Contas excluídas não podem ser editadas.");
        assertThat(deleted.getName()).isEqualTo("Conta excluída");
    }

    // ===================== Fixtures =====================

    private AnimalService animalService() {
        return new AnimalService(animalRepository, userRepository, eventPublisher);
    }

    private EventService eventService() {
        return new EventService(eventRepository, attendanceRepository, userRepository);
    }

    private UserService userService() {
        return new UserService(userRepository);
    }

    private static User owner() {
        return User.builder().id(2L).email("ngo@email.com").name("ONG").userType(UserType.NGO).city("Tubarão").state("SC").build();
    }

    private static User person(AuthProvider provider) {
        return User.builder().id(5L).email("maria@email.com").name("Maria").userType(UserType.PERSON).provider(provider).build();
    }

    private static Animal animal(AnimalStatus status) {
        Animal animal = new Animal();
        animal.setId(10L);
        animal.setOwner(owner());
        animal.setStatus(status);
        return animal;
    }

    private static Event event(LocalDate date) {
        Event event = new Event();
        event.setId(3L);
        event.setNgoId(2L);
        event.setTitle("Feira");
        event.setCategory(EventCategory.ADOPTION_FAIR);
        event.setDate(date);
        event.setStartTime(LocalTime.of(10, 0));
        event.setStatus(EventStatus.ACTIVE);
        return event;
    }

    private static AnimalRequestDTO animalRequest(String name) {
        return new AnimalRequestDTO(
                name, Species.DOG, "SRD", Sex.MALE, 3, AgeUnit.YEARS, AnimalSize.LARGE,
                true, true, true, false,
                Level.HIGH, Temperament.PROTECTIVE, Level.MODERATE, Level.MODERATE,
                true, true, false, false,
                "Protetor", "História.", List.of("https://fotos/1.jpg"));
    }

    private static EventRequestDTO eventRequest(String title) {
        return new EventRequestDTO(title, "Descrição", EventCategory.ADOPTION_FAIR, null, LocalDate.now().plusDays(3),
                LocalTime.of(10, 0), null, "Praça", null, "Rua A", null, null, null, "Tubarão", "SC", null, null);
    }

    private static ProfileUpdateDTO profile(String name) {
        return new ProfileUpdateDTO(name, null, null, null, null, null, null, null, List.of(), List.of(),
                null, null, null, null, null, "Laguna", "SC");
    }
}
