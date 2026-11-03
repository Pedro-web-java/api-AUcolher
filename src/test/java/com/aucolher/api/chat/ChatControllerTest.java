package com.aucolher.api.chat;

import com.aucolher.api.chat.dto.ChatMessageDTO;
import com.aucolher.api.common.ApiException;
import com.aucolher.api.common.security.CurrentUserProvider;
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

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Camada HTTP do chat: quem pode entrar, os códigos de sucesso do contrato e o
 * formato do erro. A Service é simulada — as regras estão em ChatServiceTest.
 */
@WebMvcTest(ChatController.class)
@Import(SecurityConfig.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService service;
    @MockBean
    private CurrentUserProvider currentUser;

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
    void everyRouteRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/chats/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/chats/8/messages")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin@email.com", roles = "ADMIN")
    void adminDoesNotAccessTheChat() throws Exception {
        mockMvc.perform(get("/api/chats/summary")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/chats/8/messages").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"oi\"}"))
                .andExpect(status().isForbidden());
        verify(service, never()).summary(anyLong());
        verify(service, never()).send(anyLong(), anyLong(), any());
    }

    @Test
    @WithMockUser(username = "ngo@email.com", roles = "NGO")
    void ngoReadsTheSummary() throws Exception {
        when(currentUser.requireUserId()).thenReturn(9L);

        mockMvc.perform(get("/api/chats/summary")).andExpect(status().isOk());

        verify(service).summary(9L);
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void listingPassesTheAfterParameter() throws Exception {
        when(currentUser.requireUserId()).thenReturn(4L);

        mockMvc.perform(get("/api/chats/8/messages").param("after", "57")).andExpect(status().isOk());

        verify(service).listMessages(4L, 8L, 57L);
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void sendingReturns201WithTheContractFields() throws Exception {
        when(currentUser.requireUserId()).thenReturn(4L);
        when(service.send(4L, 8L, "oi")).thenReturn(new ChatMessageDTO(57L, MessageType.USER, 4L, "oi", null,
                LocalDateTime.of(2026, 10, 13, 14, 3)));

        mockMvc.perform(post("/api/chats/8/messages").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"oi\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(57))
                .andExpect(jsonPath("$.type").value("USER"))
                .andExpect(jsonPath("$.senderId").value(4))
                .andExpect(jsonPath("$.text").value("oi"))
                .andExpect(jsonPath("$.systemEvent").isEmpty())
                .andExpect(jsonPath("$.createdAt").value("2026-10-13T14:03:00"));
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void markingAsReadReturns204() throws Exception {
        when(currentUser.requireUserId()).thenReturn(4L);

        mockMvc.perform(put("/api/chats/8/read").contentType(MediaType.APPLICATION_JSON).content("{\"lastMessageId\":58}"))
                .andExpect(status().isNoContent());

        verify(service).markRead(4L, 8L, 58L);
    }

    @Test
    @WithMockUser(username = "person@email.com", roles = "PERSON")
    void refusalsCarryTheReadyMessage() throws Exception {
        when(currentUser.requireUserId()).thenReturn(4L);
        when(service.send(4L, 8L, " ")).thenThrow(ApiException.badRequest("Escreva uma mensagem."));

        mockMvc.perform(post("/api/chats/8/messages").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Escreva uma mensagem."));
    }
}
