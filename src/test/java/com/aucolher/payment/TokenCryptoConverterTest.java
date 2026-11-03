package com.aucolher.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;

import com.aucolher.api.payment.MercadoPagoProperties;
import com.aucolher.api.payment.TokenCryptoConverter;
import org.junit.jupiter.api.Test;

class TokenCryptoConverterTest {

    private static MercadoPagoProperties props(String key) {
        return new MercadoPagoProperties("id", "secret", "http://x", "wh", key, false, "state");
    }

    private static String newKey() {
        return Base64.getEncoder().encodeToString(new byte[32]);
    }

    @Test
    void valueRoundTrips() {
        TokenCryptoConverter c = new TokenCryptoConverter(props(newKey()));
        String stored = c.convertToDatabaseColumn("APP_USR-123");
        assertEquals("APP_USR-123", c.convertToEntityAttribute(stored));
    }

    @Test
    void ciphertextChangesEveryTime() {
        TokenCryptoConverter c = new TokenCryptoConverter(props(newKey()));
        assertNotEquals(c.convertToDatabaseColumn("abc"), c.convertToDatabaseColumn("abc"));
    }

    @Test
    void nullStaysNull() {
        TokenCryptoConverter c = new TokenCryptoConverter(props(newKey()));
        assertEquals(null, c.convertToDatabaseColumn(null));
        assertEquals(null, c.convertToEntityAttribute(null));
    }

    @Test
    void missingKeyFailsOnlyWhenUsed() {
        TokenCryptoConverter c = new TokenCryptoConverter(props(""));
        assertThrows(IllegalStateException.class, () -> c.convertToDatabaseColumn("x"));
    }
}
