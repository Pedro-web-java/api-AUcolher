package com.aucolher.api.security;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Códigos de troca do login Google. O fluxo OAuth2 termina numa página da
 * API, e dali até o frontend só existe um redirecionamento: em vez do token
 * na URL (histórico, logs), vai um código curto, de uso único e que expira
 * em 60 s. O frontend troca o código pelo token em POST /api/auth/google/exchange.
 *
 * Fica em memória: com uma instância da API basta, e reiniciar a API no meio
 * do fluxo só faz a pessoa clicar de novo.
 */
@Component
public class GoogleLoginCodeStore {

    private static final Duration TTL = Duration.ofSeconds(60);
    private static final int CODE_BYTES = 32;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> codes = new ConcurrentHashMap<>();

    private record Entry(String email, Instant expiresAt) {
    }

    /** Gera um código para o e-mail que acabou de entrar pelo Google. */
    public String issue(String email) {
        Instant now = Instant.now();
        // Limpa os vencidos aqui mesmo: não precisa de agendador
        codes.values().removeIf(entry -> entry.expiresAt().isBefore(now));

        byte[] bytes = new byte[CODE_BYTES];
        random.nextBytes(bytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        codes.put(code, new Entry(email, now.plus(TTL)));
        return code;
    }

    /** Usa o código: some do mapa na primeira tentativa, válido ou não. */
    public Optional<String> consume(String code) {
        Entry entry = codes.remove(code);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.email());
    }
}
