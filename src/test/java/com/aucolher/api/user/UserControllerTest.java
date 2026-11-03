package com.aucolher.api.user;

import com.aucolher.api.config.SecurityConfig;
import com.aucolher.api.security.CustomOAuth2UserService;
import com.aucolher.api.security.CustomUserDetailsService;
import com.aucolher.api.security.JwtService;
import com.aucolher.api.security.OAuth2AuthenticationFailureHandler;
import com.aucolher.api.security.OAuth2AuthenticationSuccessHandler;
import com.aucolher.api.shared.exception.BusinessException;
import com.aucolher.api.shared.exception.ForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Camada HTTP da exclusão de conta. A Service é simulada — as regras estão em AccountDeletionServiceTest. */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;
    @MockBean
    private AccountDeletionService accountDeletionService;

    // Dependências da configuração de segurança real, importada acima
    @MockBean
    private JwtService jwtService;
    @MockBean
    private CustomUserDetailsService customUserDetailsService;
    @MockBean
    private CustomOAuth2UserService customOAuth2UserService;
    @MockBean
    private OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    @MockBean
    private OAuth2AuthenticationFailureHandler oAuth2AuthenticationFailureHandler;

    @Test
    void deletingRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/users/me/deletion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"senha123\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(accountDeletionService);
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void deletesTheLoggedAccount() throws Exception {
        mockMvc.perform(post("/api/users/me/deletion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"senha123\"}"))
                .andExpect(status().isNoContent());
        verify(accountDeletionService).deleteAccount(eq("person@email.com"),
                argThat(body -> "senha123".equals(body.password()) && body.confirmation() == null));
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void wrongPasswordIs400WithTheContractMessage() throws Exception {
        doThrow(new BusinessException("Senha incorreta.")).when(accountDeletionService).deleteAccount(any(), any());

        mockMvc.perform(post("/api/users/me/deletion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"errada\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Senha incorreta."));
    }

    @Test
    @WithMockUser(username = "admin@email.com", roles = "ADMIN")
    void adminIs403() throws Exception {
        doThrow(new ForbiddenException("A conta de administrador não pode ser excluída."))
                .when(accountDeletionService).deleteAccount(any(), any());

        mockMvc.perform(post("/api/users/me/deletion").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"senha123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("A conta de administrador não pode ser excluída."));
    }
}
