package com.aucolher.api.auth;

import com.aucolher.api.auth.dto.LoginDTO;
import com.aucolher.api.security.JwtService;
import com.aucolher.api.security.SuspendedAccount;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.shared.exception.InvalidCredentialsException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** Login de uma conta banida pela moderação. */
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

    private void givenSuspendedAccount() {
        User suspended = User.builder().id(1L).email("person@email.com").password("hash").userType(UserType.PERSON).build();
        suspended.setActive(false);
        when(userRepository.findByEmail("person@email.com")).thenReturn(Optional.of(suspended));
    }
}
