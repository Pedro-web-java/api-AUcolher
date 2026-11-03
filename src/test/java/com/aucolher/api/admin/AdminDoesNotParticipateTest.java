package com.aucolher.api.admin;

import com.aucolher.api.animal.AnimalRepository;
import com.aucolher.api.animal.AnimalService;
import com.aucolher.api.animal.dto.AnimalRequestDTO;
import com.aucolher.api.event.EventAttendanceRepository;
import com.aucolher.api.event.EventRepository;
import com.aucolher.api.event.EventService;
import com.aucolher.api.favorite.FavoriteRepository;
import com.aucolher.api.favorite.FavoriteService;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * O admin modera o site, mas não participa dele: não confirma presença em
 * eventos, não favorita e não anuncia animais (403 com a frase pronta).
 */
@ExtendWith(MockitoExtension.class)
class AdminDoesNotParticipateTest {

    static final String ADMIN_EMAIL = "admin@email.com";

    @Mock
    private UserRepository userRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventAttendanceRepository attendanceRepository;
    @Mock
    private FavoriteRepository favoriteRepository;
    @Mock
    private AnimalRepository animalRepository;
    @Mock
    private AnimalService animalServiceForFavorites;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void givenAdmin() {
        User admin = User.builder().id(9L).email(ADMIN_EMAIL).name("Admin").userType(UserType.ADMIN).build();
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(admin));
    }

    @Test
    void adminDoesNotConfirmOrCancelAttendance() {
        EventService eventService = new EventService(eventRepository, attendanceRepository, userRepository);
        Authentication auth = new UsernamePasswordAuthenticationToken(ADMIN_EMAIL, null, List.of());

        assertThatThrownBy(() -> eventService.confirmAttendance(3L, auth))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Contas de admin não confirmam presença em eventos.");
        assertThatThrownBy(() -> eventService.cancelAttendance(3L, auth))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(attendanceRepository);
    }

    @Test
    void adminDoesNotFavorite() {
        FavoriteService favoriteService = new FavoriteService(favoriteRepository, animalRepository,
                animalServiceForFavorites, userRepository);

        assertThatThrownBy(() -> favoriteService.add(ADMIN_EMAIL, 10L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Contas de admin não favoritam animais.");
        verify(favoriteRepository, never()).add(anyLong(), anyLong());
    }

    @Test
    void adminDoesNotListAnimals() {
        AnimalService animalService = new AnimalService(animalRepository, userRepository, eventPublisher);

        assertThatThrownBy(() -> animalService.create(ADMIN_EMAIL, mock(AnimalRequestDTO.class)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Contas de admin não anunciam animais.");
        verify(animalRepository, never()).save(any());
    }
}
