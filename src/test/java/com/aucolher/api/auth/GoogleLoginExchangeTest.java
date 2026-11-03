package com.aucolher.api.auth;

import com.aucolher.api.auth.dto.AuthResponseDTO;
import com.aucolher.api.auth.dto.GoogleExchangeDTO;
import com.aucolher.api.security.GoogleLoginCodeStore;
import com.aucolher.api.security.JwtService;
import com.aucolher.api.security.SuspendedAccount;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ForbiddenException;
import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.AuthProvider;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GoogleLoginExchangeTest {

    private static final String EMAIL = "pessoa@gmail.com";

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final GoogleLoginCodeStore codeStore = new GoogleLoginCodeStore();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, mock(PasswordEncoder.class), jwtService, codeStore);
        when(jwtService.generateToken(EMAIL)).thenReturn("jwt");
    }

    private User user(boolean active) {
        return User.builder().id(1L).name("Pessoa").email(EMAIL).userType(UserType.PERSON)
                .provider(AuthProvider.GOOGLE).active(active).build();
    }

    @Test
    void trocaOCodigoPeloMesmoPayloadDoLogin() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(true)));
        String code = codeStore.issue(EMAIL);

        AuthResponseDTO response = authService.exchangeGoogleCode(new GoogleExchangeDTO(code));

        assertThat(response.token()).isEqualTo("jwt");
        assertThat(response.type()).isEqualTo("Bearer");
        assertThat(response.user().email()).isEqualTo(EMAIL);
    }

    @Test
    void codigoSoValeUmaVez() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(true)));
        String code = codeStore.issue(EMAIL);
        authService.exchangeGoogleCode(new GoogleExchangeDTO(code));

        assertThatThrownBy(() -> authService.exchangeGoogleCode(new GoogleExchangeDTO(code)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("O login com o Google expirou. Tente novamente.");
    }

    @Test
    void codigoDesconhecidoEhRecusado() {
        assertThatThrownBy(() -> authService.exchangeGoogleCode(new GoogleExchangeDTO("inventado")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void contaBanidaDepoisDoGoogleNaoRecebeToken() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user(false)));
        String code = codeStore.issue(EMAIL);

        assertThatThrownBy(() -> authService.exchangeGoogleCode(new GoogleExchangeDTO(code)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage(SuspendedAccount.MESSAGE);
    }
}
