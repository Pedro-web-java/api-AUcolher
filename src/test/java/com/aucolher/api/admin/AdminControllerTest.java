package com.aucolher.api.admin;

import com.aucolher.api.admin.dto.AdminAnimalDTO;
import com.aucolher.api.admin.dto.ListingStatus;
import com.aucolher.api.animal.dto.AnimalSummaryDTO;
import com.aucolher.api.animal.entity.*;
import com.aucolher.api.config.SecurityConfig;
import com.aucolher.api.campaign.CampaignService;
import com.aucolher.api.event.EventService;
import com.aucolher.api.report.ReportService;
import com.aucolher.api.security.CustomOAuth2UserService;
import com.aucolher.api.security.CustomUserDetailsService;
import com.aucolher.api.security.JwtService;
import com.aucolher.api.security.OAuth2AuthenticationFailureHandler;
import com.aucolher.api.security.OAuth2AuthenticationSuccessHandler;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Camada HTTP do painel do admin: só a conta ADMIN entra, e os formatos que o frontend lê. */
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;
    @MockBean
    private AdminStatsService statsService;
    @MockBean
    private ModerationService moderationService;
    @MockBean
    private EventService eventService;
    @MockBean
    private CampaignService campaignService;
    @MockBean
    private ReportService reportService;

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
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/animals/8")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ngo@email.com", roles = "NGO")
    void otherAccountsAreForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/animals")).andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/admin/users/5/status").contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/users/5").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/events/3").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/campaigns")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/campaigns/4")).andExpect(status().isForbidden());
        verifyNoInteractions(adminService, moderationService, campaignService);
    }

    @Test
    @WithMockUser(username = "admin@email.com", roles = "ADMIN")
    void adminEditsAnAccountWithTheProfileRules() throws Exception {
        mockMvc.perform(get("/api/admin/users/5")).andExpect(status().isOk());
        verify(moderationService).getUser(5L);

        mockMvc.perform(put("/api/admin/users/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"sem-arroba\",\"profile\":{\"name\":\"M\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors['profile.name']").exists());
        verify(moderationService, never()).updateUser(anyLong(), any(), any());

        mockMvc.perform(put("/api/admin/users/5").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"maria@email.com\",\"profile\":{\"name\":\"Maria\",\"team\":[],\"visitingHours\":[]}}"))
                .andExpect(status().isOk());
        verify(moderationService).updateUser(eq(5L), any(), eq("admin@email.com"));
    }

    @Test
    @WithMockUser(username = "admin@email.com", roles = "ADMIN")
    void animalEditUsesTheRegistrationValidation() throws Exception {
        mockMvc.perform(put("/api/admin/animals/8").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
        verify(moderationService, never()).updateAnimal(anyLong(), any(), any());
    }

    @Test
    @WithMockUser(username = "admin@email.com", roles = "ADMIN")
    void animalCardComesFlatWithTheListingStatus() throws Exception {
        when(adminService.listAnimals()).thenReturn(List.of(new AdminAnimalDTO(card(), ListingStatus.IN_PROGRESS)));

        mockMvc.perform(get("/api/admin/animals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(8))
                .andExpect(jsonPath("$[0].name").value("Nino"))
                .andExpect(jsonPath("$[0].owner.active").value(false))
                .andExpect(jsonPath("$[0].listingStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[0].animal").doesNotExist());
    }

    @Test
    @WithMockUser(username = "admin@email.com", roles = "ADMIN")
    void adminModerates() throws Exception {
        mockMvc.perform(patch("/api/admin/users/5/status").contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());
        verify(moderationService).setUserActive(5L, false, "admin@email.com");

        mockMvc.perform(post("/api/admin/reports/12/dismiss")).andExpect(status().isOk());
        verify(moderationService).dismiss(12L, "admin@email.com");

        mockMvc.perform(post("/api/admin/reports/12/ban")).andExpect(status().isOk());
        verify(moderationService).banByReport(12L, "admin@email.com");

        mockMvc.perform(delete("/api/admin/animals/8")).andExpect(status().isNoContent());
        verify(moderationService).removeAnimal(8L, "admin@email.com");

        mockMvc.perform(delete("/api/admin/events/3")).andExpect(status().isNoContent());
        verify(moderationService).removeEvent(3L);

        mockMvc.perform(delete("/api/admin/campaigns/4")).andExpect(status().isNoContent());
        verify(moderationService).removeCampaign(4L);
    }

    @Test
    @WithMockUser(username = "admin@email.com", roles = "ADMIN")
    void adminEditsACampaignWithTheNgoRules() throws Exception {
        mockMvc.perform(put("/api/admin/campaigns/4").contentType(MediaType.APPLICATION_JSON).content("""
                        { "title": "Ração", "description": "Ração para o inverno", "category": "FOOD", "goalAmount": 500 }
                        """))
                .andExpect(status().isOk());
        verify(moderationService).updateCampaign(eq(4L), any(), eq("admin@email.com"));

        // Mesmo corpo do PUT da ONG, com as mesmas validações
        mockMvc.perform(put("/api/admin/campaigns/4").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin@email.com", roles = "ADMIN")
    void adminSeesEveryList() throws Exception {
        mockMvc.perform(get("/api/admin/stats")).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/events")).andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/campaigns")).andExpect(status().isOk());
        verify(campaignService).listForAdmin();
        mockMvc.perform(get("/api/admin/reports")).andExpect(status().isOk());
        verify(adminService).listUsers();
        verify(eventService).listForAdmin();
        verify(reportService).listAllForAdmin();
        verify(moderationService, never()).removeAnimal(anyLong(), any());
    }

    private static AnimalSummaryDTO card() {
        User owner = User.builder().id(2L).name("Apacri").userType(UserType.NGO).city("Tubarão").state("SC").build();
        owner.setActive(false);
        Animal animal = new Animal();
        animal.setId(8L);
        animal.setName("Nino");
        animal.setSpecies(Species.DOG);
        animal.setStatus(AnimalStatus.AVAILABLE);
        animal.setOwner(owner);
        return AnimalSummaryDTO.from(animal, null);
    }
}
