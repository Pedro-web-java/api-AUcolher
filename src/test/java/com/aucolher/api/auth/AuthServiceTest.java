package com.aucolher.api.auth;

import com.aucolher.api.auth.dto.AuthResponseDTO;
import com.aucolher.api.auth.dto.LoginDTO;
import com.aucolher.api.security.JwtService;
import com.aucolher.api.security.SuspendedAccount;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.shared.exception.InvalidCredentialsException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.AuthProvider;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Login de uma conta banida pela moderação e de uma conta do Google. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void suspendedAccountGets403WithTheSuspensionMessage() {
        givenSuspendedAccount();
        when(passwordEncoder.matches("senha123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginDTO("person@email.com", "senha123")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(SuspendedAccount.MESSAGE);
    }

    @Test
    void wrongPasswordDoesNotRevealTheSuspension() {
        givenSuspendedAccount();
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginDTO("person@email.com", "errada")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("E-mail ou senha inválidos");
    }

    @Test
    void googleAccountThatCreatedAPasswordLogsInWithIt() {
        givenGoogleAccount("hash");
        when(passwordEncoder.matches("senha123", "hash")).thenReturn(true);
        when(jwtService.generateToken("google@email.com")).thenReturn("token");

        AuthResponseDTO response = authService.login(new LoginDTO("google@email.com", "senha123"));

        assertThat(response.token()).isEqualTo("token");
        assertThat(response.user().provider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(response.user().hasPassword()).isTrue();
    }

    @Test
    void googleAccountWithoutPasswordIsToldToUseGoogle() {
        givenGoogleAccount(null);

        assertThatThrownBy(() -> authService.login(new LoginDTO("google@email.com", "senha123")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Esta conta utiliza login via Google. Use a autenticação OAuth2");
    }

    private void givenGoogleAccount(String password) {
        User google = User.builder().id(2L).email("google@email.com").password(password)
                .userType(UserType.PERSON).provider(AuthProvider.GOOGLE).build();
        when(userRepository.findByEmail("google@email.com")).thenReturn(Optional.of(google));
    }

    private void givenSuspendedAccount() {
        User suspended = User.builder().id(1L).email("person@email.com").password("hash").userType(UserType.PERSON).build();
        suspended.setActive(false);
        when(userRepository.findByEmail("person@email.com")).thenReturn(Optional.of(suspended));
    }
}
