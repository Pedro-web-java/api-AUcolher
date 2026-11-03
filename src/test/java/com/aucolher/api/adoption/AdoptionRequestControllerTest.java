package com.aucolher.api.adoption;

import com.aucolher.api.adoption.entity.AdoptionStatus;
import com.aucolher.api.adoption.entity.ReturnReason;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Camada HTTP dos pedidos de adoção: quem pode enviar ou mudar o status e
 * como a validação responde. A Service é simulada — as regras estão em AdoptionRequestServiceTest.
 */
@WebMvcTest(AdoptionRequestController.class)
@Import(SecurityConfig.class)
class AdoptionRequestControllerTest {

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
    void sendingRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/adoption-requests").contentType(MediaType.APPLICATION_JSON).content("{\"animalId\":10}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/adoption-requests/sent")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin@email.com", roles = "ADMIN")
    void adminCannotSendRequests() throws Exception {
        mockMvc.perform(post("/api/adoption-requests").contentType(MediaType.APPLICATION_JSON).content("{\"animalId\":10}"))
                .andExpect(status().isForbidden());
        verify(requestService, never()).create(any(), anyLong());
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void personSendsARequest() throws Exception {
        mockMvc.perform(post("/api/adoption-requests").contentType(MediaType.APPLICATION_JSON).content("{\"animalId\":10}"))
                .andExpect(status().isCreated());
        verify(requestService).create("person@email.com", 10L);
    }

    @Test
    @WithMockUser(username = "ngo@email.com", roles = "NGO")
    void ngoSendsARequest() throws Exception {
        mockMvc.perform(post("/api/adoption-requests").contentType(MediaType.APPLICATION_JSON).content("{\"animalId\":10}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void requestWithoutAnimalReturns400() throws Exception {
        mockMvc.perform(post("/api/adoption-requests").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.animalId").exists());
    }

    @Test
    @WithMockUser(username = "admin@email.com", roles = "ADMIN")
    void listsOnlyNeedLogin() throws Exception {
        mockMvc.perform(get("/api/adoption-requests/received")).andExpect(status().isOk());
    }

    @Test
    void changingStatusRequiresLogin() throws Exception {
        mockMvc.perform(patch("/api/adoption-requests/50/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ngo@email.com", roles = "NGO")
    void changesTheStatus() throws Exception {
        mockMvc.perform(patch("/api/adoption-requests/50/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk());
        verify(requestService).changeStatus(eq(50L), eq("ngo@email.com"), eq(AdoptionStatus.ACCEPTED));
    }

    @Test
    @WithMockUser(username = "ngo@email.com", roles = "NGO")
    void statusIsRequiredAndMustExist() throws Exception {
        mockMvc.perform(patch("/api/adoption-requests/50/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
        mockMvc.perform(patch("/api/adoption-requests/50/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registeringAReturnRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/adoption-requests/50/return").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ngo@email.com", roles = "NGO")
    void registersAReturn() throws Exception {
        mockMvc.perform(post("/api/adoption-requests/50/return").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"returnDate\":\"2026-10-01\",\"reason\":\"OTHER\",\"note\":\"  Mudou de casa  \"}"))
                .andExpect(status().isOk());
        verify(requestService).registerReturn(eq(50L), eq("ngo@email.com"), argThat(body ->
                body.returnDate().toString().equals("2026-10-01") && body.reason() == ReturnReason.OTHER
                        && body.note().equals("Mudou de casa")));
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void rehomeValidatesTheNewListingLikeTheRegistration() throws Exception {
        mockMvc.perform(post("/api/adoption-requests/50/rehome").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"COSTS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.animal").exists());
        mockMvc.perform(post("/api/adoption-requests/50/rehome").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"COSTS\",\"animal\":{\"name\":\"\"}}"))
                .andExpect(status().isBadRequest());
        verify(requestService, never()).rehome(anyLong(), any(), any());
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void adopterGetsTheDraftAndUndoesTheRehome() throws Exception {
        mockMvc.perform(get("/api/adoption-requests/50/rehome-draft")).andExpect(status().isOk());
        verify(requestService).getRehomeDraft(50L, "person@email.com");

        mockMvc.perform(post("/api/adoption-requests/50/rehome/undo")).andExpect(status().isOk());
        verify(requestService).undoRehome(50L, "person@email.com");
    }
}
