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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Camada HTTP das avaliações: o resumo é público, escrever exige login e validação. */
@WebMvcTest(AdoptionReviewController.class)
@Import(SecurityConfig.class)
class AdoptionReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdoptionReviewService reviewService;

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
    void profileReviewsArePublic() throws Exception {
        mockMvc.perform(get("/api/users/2/reviews")).andExpect(status().isOk());
    }

    @Test
    void writingRequiresLogin() throws Exception {
        mockMvc.perform(put("/api/adoption-requests/50/review").contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/adoption-requests/50/review")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void savesAReview() throws Exception {
        mockMvc.perform(put("/api/adoption-requests/50/review").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"Ótima experiência\"}"))
                .andExpect(status().isOk());
        verify(reviewService).saveMine(anyLong(), anyString(), any());
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void ratingMustBeOneToFiveStars() throws Exception {
        mockMvc.perform(put("/api/adoption-requests/50/review").contentType(MediaType.APPLICATION_JSON).content("{\"rating\":6}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rating").exists());
        mockMvc.perform(put("/api/adoption-requests/50/review").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verify(reviewService, never()).saveMine(anyLong(), anyString(), any());
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void commentIsLimitedTo500Characters() throws Exception {
        String longComment = "a".repeat(501);
        mockMvc.perform(put("/api/adoption-requests/50/review").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"comment\":\"" + longComment + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.comment").exists());
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void deletesAReview() throws Exception {
        mockMvc.perform(delete("/api/adoption-requests/50/review")).andExpect(status().isNoContent());
    }
}
