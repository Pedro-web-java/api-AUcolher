package com.aucolher.api.auth;

import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.AuthProvider;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Criação da primeira conta ADMIN na subida da API, com o banco simulado (Mockito). */
@ExtendWith(MockitoExtension.class)
class AdminAccountInitializerTest {

    private static final String ADMIN_EMAIL = "admin@aucolher.com";
    private static final String ADMIN_PASSWORD = "senha-forte-123";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminAccountInitializer initializer(String email, String password, String name) {
        return new AdminAccountInitializer(userRepository, passwordEncoder, email, password, name);
    }

    private void run(AdminAccountInitializer initializer) {
        initializer.run(new DefaultApplicationArguments());
    }

    @Test
    void createsAdminWhenEmailIsNew() {
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(ADMIN_PASSWORD)).thenReturn("hash");

        // Espaços nas pontas caem, como no login
        run(initializer("  " + ADMIN_EMAIL + " ", ADMIN_PASSWORD, ""));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo(ADMIN_EMAIL);
        assertThat(saved.getValue().getName()).isEqualTo("Administrador");
        assertThat(saved.getValue().getPassword()).isEqualTo("hash");
        assertThat(saved.getValue().getUserType()).isEqualTo(UserType.ADMIN);
        assertThat(saved.getValue().getProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(saved.getValue().getActive()).isTrue();
    }

    @Test
    void doesNotTouchAnExistingAccount() {
        User existing = User.builder().email(ADMIN_EMAIL).userType(UserType.PERSON).build();
        when(userRepository.findByEmail(ADMIN_EMAIL)).thenReturn(Optional.of(existing));

        run(initializer(ADMIN_EMAIL, ADMIN_PASSWORD, "Admin"));

        verify(userRepository, never()).save(any());
        assertThat(existing.getUserType()).isEqualTo(UserType.PERSON);
    }

    @Test
    void skipsWhenNotConfigured() {
        run(initializer("", "", ""));
        run(initializer(ADMIN_EMAIL, null, null));

        verifyNoInteractions(userRepository, passwordEncoder);
    }

    @Test
    void skipsShortPassword() {
        run(initializer(ADMIN_EMAIL, "1234567", "Admin"));

        verifyNoInteractions(userRepository, passwordEncoder);
    }
}
