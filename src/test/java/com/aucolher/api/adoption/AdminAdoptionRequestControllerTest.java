package com.aucolher.api.adoption;

import com.aucolher.api.config.SecurityConfig;
import com.aucolher.api.security.CustomOAuth2UserService;
import com.aucolher.api.security.CustomUserDetailsService;
import com.aucolher.api.security.JwtService;
import com.aucolher.api.security.OAuth2AuthenticationFailureHandler;
import com.aucolher.api.security.OAuth2AuthenticationSuccessHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A lista de pedidos do painel é só da conta ADMIN. */
@WebMvcTest(AdminAdoptionRequestController.class)
@Import(SecurityConfig.class)
class AdminAdoptionRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdoptionRequestService requestService;

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
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/admin/adoption-requests")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ngo@email.com", roles = "NGO")
    void otherAccountsAreForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/adoption-requests")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@email.com", roles = "ADMIN")
    void adminSeesEveryRequest() throws Exception {
        mockMvc.perform(get("/api/admin/adoption-requests")).andExpect(status().isOk());
    }
}
