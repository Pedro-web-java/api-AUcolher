package com.aucolher.api.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import com.aucolher.api.payment.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

import com.aucolher.api.common.ApiException;
import com.aucolher.api.common.DonationGateway;
import com.aucolher.api.common.UserDirectory;
import com.fasterxml.jackson.databind.ObjectMapper;

class MercadoPagoOAuthServiceTest {

    private MercadoPagoProperties props;
    private MercadoPagoAccountRepository repository;
    private MercadoPagoClient client;
    private OAuthStateService stateService;
    private UserDirectory users;
    private DonationGateway donations;
    private MercadoPagoOAuthService service;

    @BeforeEach
    void setUp() {
        props = new MercadoPagoProperties("cid", "csecret", "https://api.x/cb", "wh", "key", false, "segredo");
        repository = mock(MercadoPagoAccountRepository.class);
        client = mock(MercadoPagoClient.class);
        stateService = new OAuthStateService(props, new ObjectMapper());
        users = mock(UserDirectory.class);
        donations = mock(DonationGateway.class);
        service = new MercadoPagoOAuthService(props, repository, client, stateService, users, donations,
                mock(PlatformTransactionManager.class));
    }

    private static MercadoPagoClient.TokenResponse tokens() {
        return new MercadoPagoClient.TokenResponse("acc", "ref", 15_552_000L, 999L, false);
    }

    @Test
    void happyCallbackSavesAccountAsConnected() {
        when(users.isActiveNgo(7L)).thenReturn(true);
        when(client.exchangeCode("code")).thenReturn(tokens());
        when(repository.findByMpUserId(999L)).thenReturn(Optional.empty());
        when(repository.findById(7L)).thenReturn(Optional.empty());

        var result = service.handleCallback("code", stateService.create(7L), null);

        assertEquals(MercadoPagoOAuthService.CallbackResult.CONNECTED, result);
        verify(repository).saveAndFlush(any(MercadoPagoAccount.class));
    }

    @Test
    void deniedWhenMercadoPagoSendsError() {
        assertEquals(MercadoPagoOAuthService.CallbackResult.DENIED,
                service.handleCallback(null, null, "access_denied"));
    }

    @Test
    void expiredStateIsAnError() {
        String old = stateService.create(7L, Instant.now().minus(Duration.ofMinutes(11)));
        assertEquals(MercadoPagoOAuthService.CallbackResult.ERROR, service.handleCallback("code", old, null));
        verify(client, never()).exchangeCode(anyString());
    }

    @Test
    void accountLinkedToAnotherNgoIsRefused() {
        when(users.isActiveNgo(7L)).thenReturn(true);
        when(client.exchangeCode("code")).thenReturn(tokens());
        MercadoPagoAccount other = new MercadoPagoAccount();
        other.setNgoId(8L);
        when(repository.findByMpUserId(999L)).thenReturn(Optional.of(other));

        var result = service.handleCallback("code", stateService.create(7L), null);

        assertEquals(MercadoPagoOAuthService.CallbackResult.ALREADY_LINKED, result);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void failedRefreshMarksNeedsReconnectWithoutDeletingCredentials() {
        MercadoPagoAccount account = new MercadoPagoAccount();
        account.setNgoId(7L);
        account.setMpUserId(999L);
        account.setAccessToken("acc");
        account.setRefreshToken("ref");
        account.setExpiresAt(LocalDateTime.now().plusDays(1));
        when(repository.findForUpdate(7L)).thenReturn(Optional.of(account));
        when(client.refresh("ref")).thenThrow(new MercadoPagoApiException(400, "invalid_grant"));

        boolean usable = service.refreshIfDue(7L, Duration.ofDays(7));

        assertFalse(usable);
        assertEquals(MercadoPagoAccountStatus.NEEDS_RECONNECT, account.getStatus());
        assertEquals("ref", account.getRefreshToken());
    }

    @Test
    void disconnectIsRefusedWithPendingDonation() {
        when(repository.existsById(7L)).thenReturn(true);
        when(donations.hasPendingDonations(7L)).thenReturn(true);
        assertThrows(ApiException.class, () -> service.disconnect(7L));
        verify(repository, never()).deleteById(any());
    }

    @Test
    void startConnectionWithoutClientIdIs503() {
        var empty = new MercadoPagoProperties("", "", "", "", "", false, "");
        var s = new MercadoPagoOAuthService(empty, repository, client, stateService, users, donations,
                mock(PlatformTransactionManager.class));
        ApiException e = assertThrows(ApiException.class, () -> s.startConnection(7L));
        assertEquals(503, e.getStatus().value());
    }
}
