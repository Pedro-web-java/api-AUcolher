package com.aucolher.api.match;

import com.aucolher.api.config.SecurityConfig;
import com.aucolher.api.match.dto.MatchCardDTO;
import com.aucolher.api.match.dto.MatchCriterionDTO;
import com.aucolher.api.match.dto.MatchProfileDTO;
import com.aucolher.api.match.entity.Housing;
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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Camada HTTP do AUmatch: só pessoa física entra, e a validação do quiz
 * responde 400. A Service é simulada — as regras estão em MatchServiceTest.
 */
@WebMvcTest(MatchController.class)
@Import(SecurityConfig.class)
class MatchControllerTest {

    private static final String PERSON_EMAIL = "person@email.com";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MatchService matchService;

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
    void profileRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/match/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ngo@email.com", roles = "NGO")
    void ngoCannotUseAumatch() throws Exception {
        mockMvc.perform(get("/api/match/profile")).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/match/profile").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        verify(matchService, never()).saveProfile(any(), any());
    }

    @Test
    @WithMockUser(username = "admin@email.com", roles = "ADMIN")
    void adminCannotUseAumatch() throws Exception {
        mockMvc.perform(get("/api/match/profile")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = PERSON_EMAIL, roles = "PERSON")
    void personWithoutAnswersGetsEmptyProfile() throws Exception {
        when(matchService.getProfile(PERSON_EMAIL)).thenReturn(MatchProfileDTO.empty());

        mockMvc.perform(get("/api/match/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.complete").value(false))
                .andExpect(jsonPath("$.housing").doesNotExist());
    }

    @Test
    @WithMockUser(username = PERSON_EMAIL, roles = "PERSON")
    void personSavesAnswers() throws Exception {
        mockMvc.perform(put("/api/match/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"housing":"APARTMENT","hasChildren":true,"hasDogs":false,"hasCats":false,"preferredSize":"SMALL"}
                                """))
                .andExpect(status().isOk());

        verify(matchService).saveProfile(eq(PERSON_EMAIL), argThat(request ->
                request.housing() == Housing.APARTMENT && request.hasChildren() && !request.hasCats()));
    }

    @Test
    @WithMockUser(username = PERSON_EMAIL, roles = "PERSON")
    void partialHouseholdReturns400() throws Exception {
        mockMvc.perform(put("/api/match/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hasChildren\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.householdValid").exists());
        verify(matchService, never()).saveProfile(any(), any());
    }

    @Test
    @WithMockUser(username = PERSON_EMAIL, roles = "PERSON")
    void unknownAnswerReturns400() throws Exception {
        mockMvc.perform(put("/api/match/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"housing\":\"CASTLE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deckRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/match/deck")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ngo@email.com", roles = "NGO")
    void ngoCannotSeeTheDeck() throws Exception {
        mockMvc.perform(get("/api/match/deck")).andExpect(status().isForbidden());
        verify(matchService, never()).deck(any());
    }

    @Test
    @WithMockUser(username = PERSON_EMAIL, roles = "PERSON")
    void personGetsTheRankedDeck() throws Exception {
        when(matchService.deck(PERSON_EMAIL)).thenReturn(List.of(new MatchCardDTO(null, 85,
                List.of(new MatchCriterionDTO(MatchCriterion.ENERGY, 20, 20, true)))));

        mockMvc.perform(get("/api/match/deck"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matchScore").value(85))
                .andExpect(jsonPath("$[0].criteria[0].key").value("ENERGY"))
                .andExpect(jsonPath("$[0].criteria[0].maxPoints").value(20));
    }
}
