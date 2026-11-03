package com.aucolher.api.donation;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Camada HTTP das doações e do webhook. As regras estão em DonationServiceTest. */
@WebMvcTest({DonationController.class, MercadoPagoWebhookController.class})
@Import(SecurityConfig.class)
class DonationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DonationService donationService;
    @MockBean
    private MercadoPagoWebhookValidator webhookValidator;

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
    void donationRoutesNeedLogin() throws Exception {
        mockMvc.perform(post("/api/campaigns/4/donations").contentType(MediaType.APPLICATION_JSON).content("{ \"amount\": 50 }"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/donations/mine")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/donations/received")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/donations/259")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void donatingAnswers201() throws Exception {
        mockMvc.perform(post("/api/campaigns/4/donations").contentType(MediaType.APPLICATION_JSON).content("{ \"amount\": 50 }"))
                .andExpect(status().isCreated());

        verify(donationService).create(eq(4L), any(), any());
    }

    @Test
    void webhookIsPublicButNeedsTheSignature() throws Exception {
        mockMvc.perform(post("/api/webhooks/mercadopago").param("data.id", "ORD123").param("type", "order"))
                .andExpect(status().isUnauthorized());
        verify(donationService, never()).onWebhook(any());

        when(webhookValidator.isValid("ts=1,v1=abc", "req-1", "ORD123")).thenReturn(true);
        mockMvc.perform(post("/api/webhooks/mercadopago").param("data.id", "ORD123").param("type", "order")
                        .header("x-signature", "ts=1,v1=abc").header("x-request-id", "req-1"))
                .andExpect(status().isOk());
        verify(donationService).onWebhook("ORD123");
    }
}
