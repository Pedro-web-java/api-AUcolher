package com.aucolher.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

/**
 * Geração e validação dos tokens JWT usados pelos logins (tradicional e OAuth2).
 *
 * O token carrega apenas o e-mail (subject) e a validade. Papel e id não entram
 * como claims: o token é assinado, mas não é revalidado contra o banco a cada
 * uso, então um papel copiado para dentro dele ficaria desatualizado depois de
 * qualquer mudança na conta. Quem responde por isso é o CustomUserDetailsService,
 * que relê o usuário do banco em cada requisição autenticada.
 */
@Component
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** HS256 exige pelo menos 256 bits de chave. */
    private static final int MIN_SECRET_LENGTH = 32;

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret:}") String secret,
                      @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = buildKey(secret);
        this.expirationMs = expirationMs;
    }

    private static SecretKey buildKey(String secret) {
        if (secret == null || secret.isBlank()) {
            log.warn("JWT_SECRET não definido: gerando uma chave aleatória válida só para esta execução. " +
                    "Os tokens emitidos param de valer quando a API reinicia — defina JWT_SECRET no ambiente.");
            return Jwts.SIG.HS256.key().build();
        }

        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);

        if (bytes.length < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "app.jwt.secret precisa ter no mínimo " + MIN_SECRET_LENGTH + " caracteres");
        }

        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(String email) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, String email) {
        String tokenEmail = extractEmail(token);
        return tokenEmail.equals(email) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        Claims claims = extractAllClaims(token);
        return resolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
