package com.aucolher.api.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class JwtServiceTest {

    private final JwtService service = new JwtService("segredo-de-teste-com-mais-de-32-caracteres", 60_000);

    @Test
    void issuedAtComesFromTheToken() {
        String token = service.generateToken("person@email.com");

        assertThat(service.extractIssuedAt(token).toInstant())
                .isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));
    }
}
