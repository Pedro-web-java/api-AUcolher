package com.aucolher.api.user;

import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.dto.PublicProfileDTO;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Perfil público (GET /api/users/{id}/public-profile), com o banco simulado (Mockito). */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User user(UserType type) {
        return User.builder()
                .id(7L).name("Conta").email("conta@email.com").userType(type)
                .cnpj(type == UserType.NGO ? "11222333000181" : null)
                .instagram("conta").verified(type == UserType.NGO)
                .cep("88900000").street("Rua A").number("10").district("Centro")
                .city("Araranguá").state("SC")
                .build();
    }

    @Test
    void ngoProfileShowsTheInstitutionalDataAndAddress() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(UserType.NGO)));

        PublicProfileDTO profile = userService.getPublicProfile(7L);

        assertThat(profile.userType()).isEqualTo(UserType.NGO);
        assertThat(profile.verified()).isTrue();
        assertThat(profile.cnpj()).isEqualTo("11222333000181");
        assertThat(profile.street()).isEqualTo("Rua A");
        assertThat(profile.city()).isEqualTo("Araranguá");
    }

    @Test
    void personProfileHidesTheStreetAddressAndNgoFields() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(UserType.PERSON)));

        PublicProfileDTO profile = userService.getPublicProfile(7L);

        assertThat(profile.cep()).isNull();
        assertThat(profile.street()).isNull();
        assertThat(profile.number()).isNull();
        assertThat(profile.district()).isNull();
        assertThat(profile.instagram()).isNull();
        assertThat(profile.verified()).isFalse();
        assertThat(profile.city()).isEqualTo("Araranguá");
        assertThat(profile.state()).isEqualTo("SC");
    }

    @Test
    void adminAndInactiveAccountsHaveNoPublicProfile() {
        User inactive = user(UserType.PERSON);
        inactive.setActive(false);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(UserType.ADMIN)));
        when(userRepository.findById(2L)).thenReturn(Optional.of(inactive));
        when(userRepository.findById(3L)).thenReturn(Optional.empty());

        for (long id = 1; id <= 3; id++) {
            long missing = id;
            assertThatThrownBy(() -> userService.getPublicProfile(missing))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
