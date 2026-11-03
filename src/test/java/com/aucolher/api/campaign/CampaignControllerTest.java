package com.aucolher.api.campaign;

import com.aucolher.api.config.SecurityConfig;
import com.aucolher.api.security.CustomOAuth2UserService;
import com.aucolher.api.security.CustomUserDetailsService;
import com.aucolher.api.security.JwtService;
import com.aucolher.api.security.OAuth2AuthenticationFailureHandler;
import com.aucolher.api.security.OAuth2AuthenticationSuccessHandler;
import com.aucolher.api.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Camada HTTP das campanhas. A Service é simulada — as regras estão em CampaignServiceTest. */
@WebMvcTest(CampaignController.class)
@Import(SecurityConfig.class)
class CampaignControllerTest {

    static final String BODY = """
            { "title": "Cirurgia do Rex", "description": "Rex precisa de cirurgia", "category": "HEALTH",
              "isUrgent": true, "goalAmount": 8000 }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CampaignService campaignService;

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
    void listAndDetailArePublic() throws Exception {
        when(campaignService.listPublic(null, "CLOSED")).thenReturn(List.of());

        mockMvc.perform(get("/api/campaigns").param("status", "CLOSED")).andExpect(status().isOk());
        mockMvc.perform(get("/api/campaigns/4")).andExpect(status().isOk());
    }

    @Test
    void myCampaignsAndWritingNeedLogin() throws Exception {
        mockMvc.perform(get("/api/campaigns/mine")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/campaigns").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/campaigns/4/close")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/campaigns/4")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ngo@email.com", roles = "NGO")
    void creatingAnswers201() throws Exception {
        mockMvc.perform(post("/api/campaigns").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        verify(campaignService).create(any(), any());
    }

    @Test
    @WithMockUser(username = "ngo@email.com", roles = "NGO")
    void invalidBodyIsA400WithTheFieldMessage() throws Exception {
        mockMvc.perform(post("/api/campaigns").contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"title\": \"Rex\", \"description\": \"x\", \"category\": \"HEALTH\", \"goalAmount\": 10 }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A meta mínima é de R$ 50."));
    }

    @Test
    @WithMockUser(username = "ngo@email.com", roles = "NGO")
    void secondUrgentIsA409() throws Exception {
        when(campaignService.create(any(), any())).thenThrow(new ConflictException("A ONG já tem uma campanha urgente ativa"));

        mockMvc.perform(post("/api/campaigns").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "ngo@email.com", roles = "NGO")
    void deletingAnswers204() throws Exception {
        mockMvc.perform(delete("/api/campaigns/4")).andExpect(status().isNoContent());
    }
}
