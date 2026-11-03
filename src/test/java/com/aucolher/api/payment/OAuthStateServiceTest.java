package com.aucolher.api.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;

import com.aucolher.api.payment.MercadoPagoProperties;
import com.aucolher.api.payment.OAuthStateService;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class OAuthStateServiceTest {

    private final OAuthStateService service = new OAuthStateService(
            new MercadoPagoProperties("id", "secret", "http://x", "wh", "k", false, "segredo-de-teste"),
            new ObjectMapper());

    @Test
    void validStateReturnsNgoId() {
        assertEquals(42L, service.validate(service.create(42L)));
    }

    @Test
    void expiredStateIsRejected() {
        Instant old = Instant.now().minus(Duration.ofMinutes(11));
        String state = service.create(42L, old);
        assertThrows(OAuthStateService.InvalidStateException.class, () -> service.validate(state));
    }

    @Test
    void tamperedStateIsRejected() {
        String state = service.create(42L);
        String tampered = state.substring(0, state.length() - 2) + "xx";
        assertThrows(OAuthStateService.InvalidStateException.class, () -> service.validate(tampered));
    }

    @Test
    void garbageIsRejected() {
        assertThrows(OAuthStateService.InvalidStateException.class, () -> service.validate("abc"));
        assertThrows(OAuthStateService.InvalidStateException.class, () -> service.validate(null));
    }
}
