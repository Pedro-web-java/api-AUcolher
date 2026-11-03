package com.aucolher.api.report;

import com.aucolher.api.config.SecurityConfig;
import com.aucolher.api.report.entity.ReportReason;
import com.aucolher.api.report.entity.ReportTargetType;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Camada HTTP das denúncias. A Service é simulada — as regras estão em ReportServiceTest. */
@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

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
    void reportingRequiresLogin() throws Exception {
        mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports/mine")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void reportsAListing() throws Exception {
        mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"ANIMAL\",\"targetId\":8,\"reason\":\"SCAM\",\"description\":\"  \"}"))
                .andExpect(status().isCreated());
        verify(reportService).create(eq("person@email.com"), argThat(body -> body.targetType() == ReportTargetType.ANIMAL
                && body.targetId() == 8L && body.reason() == ReportReason.SCAM && body.description() == null));
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void duplicateReportIs409() throws Exception {
        when(reportService.create(any(), any())).thenThrow(new ConflictException("Você já denunciou este anúncio. A denúncia está em análise."));

        mockMvc.perform(post("/api/reports").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetType\":\"ANIMAL\",\"targetId\":8,\"reason\":\"SCAM\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Você já denunciou este anúncio. A denúncia está em análise."));
    }

    @Test
    @WithMockUser(username = "ngo@email.com", roles = "NGO")
    void listsMyReports() throws Exception {
        mockMvc.perform(get("/api/reports/mine")).andExpect(status().isOk());
        verify(reportService).listMine("ngo@email.com");
    }
}
