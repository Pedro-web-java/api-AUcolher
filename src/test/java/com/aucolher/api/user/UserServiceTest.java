package com.aucolher.api.user;

import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ResourceNotFoundException;
import com.aucolher.api.user.dto.ProfileUpdateDTO;
import com.aucolher.api.user.dto.PublicProfileDTO;
import com.aucolher.api.user.dto.UserResponseDTO;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Perfil público (GET /api/users/{id}/public-profile) e banner da ONG, com o banco simulado (Mockito). */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private static final String BANNER = "data:image/jpeg;base64,AAAA";

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

        PublicProfileDTO profile = userService.getPublicProfile(7L, null);

        assertThat(profile.userType()).isEqualTo(UserType.NGO);
        assertThat(profile.verified()).isTrue();
        assertThat(profile.cnpj()).isEqualTo("11222333000181");
        assertThat(profile.email()).isEqualTo("conta@email.com");
        assertThat(profile.street()).isEqualTo("Rua A");
        assertThat(profile.city()).isEqualTo("Araranguá");
    }

    @Test
    void personProfileHidesTheStreetAddressAndNgoFields() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(UserType.PERSON)));

        PublicProfileDTO profile = userService.getPublicProfile(7L, null);

        assertThat(profile.email()).isNull();
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
            assertThatThrownBy(() -> userService.getPublicProfile(missing, null))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    void onlyTheAdminSeesABannedProfile() {
        User banned = user(UserType.PERSON);
        banned.setActive(false);
        when(userRepository.findById(2L)).thenReturn(Optional.of(banned));
        when(userRepository.findByEmail("admin@email.com")).thenReturn(Optional.of(
                User.builder().id(9L).email("admin@email.com").userType(UserType.ADMIN).build()));
        when(userRepository.findByEmail("person@email.com")).thenReturn(Optional.of(user(UserType.PERSON)));

        assertThat(userService.getPublicProfile(2L, "admin@email.com").active()).isFalse();
        assertThatThrownBy(() -> userService.getPublicProfile(2L, "person@email.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void ngoSavesTheBannerAndShowsItOnThePublicProfile() {
        User ngo = user(UserType.NGO);
        when(userRepository.findByEmail("conta@email.com")).thenReturn(Optional.of(ngo));
        when(userRepository.findById(7L)).thenReturn(Optional.of(ngo));

        UserResponseDTO saved = userService.updateProfile("conta@email.com", profile(BANNER));

        assertThat(saved.coverUrl()).isEqualTo(BANNER);
        assertThat(userService.getPublicProfile(7L, null).coverUrl()).isEqualTo(BANNER);
    }

    @Test
    void ngoRemovesTheBannerBySendingNoCover() {
        User ngo = user(UserType.NGO);
        ngo.setCoverUrl(BANNER);
        when(userRepository.findByEmail("conta@email.com")).thenReturn(Optional.of(ngo));

        assertThat(userService.updateProfile("conta@email.com", profile(null)).coverUrl()).isNull();
    }

    @Test
    void personCannotHaveABanner() {
        User person = user(UserType.PERSON);
        when(userRepository.findByEmail("conta@email.com")).thenReturn(Optional.of(person));

        assertThatThrownBy(() -> userService.updateProfile("conta@email.com", profile(BANNER)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Só ONGs podem ter banner no perfil.");
        assertThat(person.getCoverUrl()).isNull();
    }

    @Test
    void personPublicProfileNeverHasABanner() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user(UserType.PERSON)));

        assertThat(userService.getPublicProfile(7L, null).coverUrl()).isNull();
    }

    @Test
    void blankBannerBecomesNull() {
        assertThat(profile("   ").coverUrl()).isNull();
    }

    private static ProfileUpdateDTO profile(String coverUrl) {
        return new ProfileUpdateDTO("Conta", null, coverUrl, null, "conta", null, null, null, List.of(), List.of(),
                "88900000", "Rua A", "10", null, "Centro", "Araranguá", "SC");
    }
}
