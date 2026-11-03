package com.aucolher.auth.password;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import com.aucolher.api.password.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.aucolher.api.password.PasswordUserStore.UserRow;
import com.aucolher.api.common.ApiException;

class PasswordResetServiceTest {

    private PasswordUserStore users;
    private PasswordResetTokenRepository tokens;
    private PasswordEncoder encoder;
    private MailService mail;
    private PasswordResetService service;

    @BeforeEach
    void setUp() {
        users = mock(PasswordUserStore.class);
        tokens = mock(PasswordResetTokenRepository.class);
        encoder = mock(PasswordEncoder.class);
        mail = mock(MailService.class);
        service = new PasswordResetService(users, tokens, encoder, mail, "http://localhost:5173", true);
    }

    @Test
    void unknownEmailRespondsQuietlyWithoutSending() {
        when(users.findByEmail("x@x.com")).thenReturn(Optional.empty());
        service.forgot("x@x.com");
        verify(tokens, never()).save(any());
        verify(mail, never()).sendPasswordReset(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void malformedEmailIs400() {
        ApiException e = assertThrows(ApiException.class, () -> service.forgot("nao-e-email"));
        assertEquals("Informe um e-mail válido.", e.getMessage());
    }

    @Test
    void inactiveAccountGetsNothing() {
        when(users.findByEmail("a@a.com")).thenReturn(Optional.of(new UserRow(1, "Ana", "a@a.com", "hash", false)));
        service.forgot("a@a.com");
        verify(mail, never()).sendPasswordReset(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void googleOnlyAccountGetsTheLinkWithTheGoogleLine() {
        when(users.findByEmail("g@g.com")).thenReturn(Optional.of(new UserRow(2, "Gui", "g@g.com", null, true)));
        when(tokens.findTopByUserIdOrderByCreatedAtDesc(2L)).thenReturn(Optional.empty());

        service.forgot("g@g.com");

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(mail).sendPasswordReset(eq("Gui"), eq("g@g.com"), link.capture(), eq(true));
        assertTrue(link.getValue().startsWith("http://localhost:5173/redefinir-senha?token="));
        assertEquals(43, link.getValue().substring(link.getValue().indexOf("token=") + 6).length());
    }

    @Test
    void onlyOneEmailPerMinute() {
        when(users.findByEmail("a@a.com")).thenReturn(Optional.of(new UserRow(1, "Ana", "a@a.com", "hash", true)));
        PasswordResetToken recent = new PasswordResetToken(1L, "h", LocalDateTime.now().minusSeconds(20),
                LocalDateTime.now().plusMinutes(10));
        when(tokens.findTopByUserIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(recent));

        service.forgot("a@a.com");

        verify(tokens, never()).save(any());
        verify(mail, never()).sendPasswordReset(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void expiredOrUsedTokenIs400() {
        when(tokens.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(anyString(), any())).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class, () -> service.reset("qualquer", "senhaNova2"));
        assertEquals("Este link é inválido ou expirou. Peça um novo.", e.getMessage());
    }

    @Test
    void shortPasswordIs400() {
        PasswordResetToken t = new PasswordResetToken(1L, "h", LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));
        when(tokens.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(anyString(), any())).thenReturn(Optional.of(t));
        ApiException e = assertThrows(ApiException.class, () -> service.reset("tok", "12345"));
        assertEquals("A nova senha deve ter no mínimo 6 caracteres.", e.getMessage());
    }

    @Test
    void resetStoresHashAndInvalidatesSessions() {
        PasswordResetToken t = new PasswordResetToken(1L, "h", LocalDateTime.now(), LocalDateTime.now().plusMinutes(5));
        when(tokens.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(anyString(), any())).thenReturn(Optional.of(t));
        when(users.findById(1L)).thenReturn(Optional.of(new UserRow(1, "Ana", "a@a.com", "velho", true)));
        when(encoder.encode("senhaNova2")).thenReturn("hash-novo");
        when(users.updatePassword(anyLong(), anyString(), any())).thenReturn(true);

        service.reset("tok", "senhaNova2");

        ArgumentCaptor<LocalDateTime> changedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(users).updatePassword(eq(1L), eq("hash-novo"), changedAt.capture());
        assertEquals(0, changedAt.getValue().getNano()); // truncado para segundos (o iat do JWT não tem milissegundos)
        verify(tokens).invalidateOpen(eq(1L), any());
        verify(mail).sendPasswordChanged(eq("Ana"), eq("a@a.com"), any());
    }
}
