package com.aucolher.api.password;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.aucolher.api.password.PasswordUserStore.UserRow;
import com.aucolher.api.common.ApiException;

/** Recuperar e alterar senha (regra de negócio §2.1). */
@Service
public class PasswordResetService {

    static final String INVALID_EMAIL = "Informe um e-mail válido.";
    static final String SHORT_PASSWORD = "A nova senha deve ter no mínimo 6 caracteres.";
    static final String INVALID_LINK = "Este link é inválido ou expirou. Peça um novo.";

    private static final int MIN_PASSWORD = 6;
    private static final int TOKEN_BYTES = 32;
    private static final int TTL_MINUTES = 30;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final PasswordUserStore users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final MailService mail;
    private final String frontendUrl;
    private final boolean changedNotice;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(PasswordUserStore users,
                                PasswordResetTokenRepository tokens,
                                PasswordEncoder encoder,
                                MailService mail,
                                @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl,
                                @Value("${app.mail.password-changed-notice:true}") boolean changedNotice) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.mail = mail;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
        this.changedNotice = changedNotice;
    }

    /**
     * Resposta sempre neutra. Só envia se a conta existe e está ativa, e se o último link
     * dela tem mais de 1 minuto.
     */
    @Transactional
    public void forgot(String email) {
        String normalized = email == null ? "" : email.trim();
        if (normalized.length() > 150 || !EMAIL.matcher(normalized).matches()) {
            throw ApiException.badRequest(INVALID_EMAIL);
        }
        Optional<UserRow> found = users.findByEmail(normalized);
        if (found.isEmpty() || !found.get().active()) {
            return;
        }
        UserRow user = found.get();
        LocalDateTime now = LocalDateTime.now();

        Optional<PasswordResetToken> last = tokens.findTopByUserIdOrderByCreatedAtDesc(user.id());
        if (last.isPresent() && last.get().getCreatedAt().isAfter(now.minusMinutes(1))) {
            return;
        }

        tokens.invalidateOpen(user.id(), now);
        String raw = generateToken();
        tokens.save(new PasswordResetToken(user.id(), sha256Hex(raw), now, now.plusMinutes(TTL_MINUTES)));

        String link = frontendUrl + "/redefinir-senha?token=" + raw;
        boolean googleOnly = user.password() == null;
        afterCommit(() -> mail.sendPasswordReset(user.name(), user.email(), link, googleOnly));
    }

    /** A página de redefinir confere o link ao abrir. */
    @Transactional(readOnly = true)
    public void checkToken(String token) {
        findValid(token);
    }

    /** Redefine, invalida os links abertos e derruba as sessões (password_changed_at). */
    @Transactional
    public void reset(String token, String newPassword) {
        PasswordResetToken valid = findValid(token);
        if (newPassword == null || newPassword.length() < MIN_PASSWORD) {
            throw ApiException.badRequest(SHORT_PASSWORD);
        }
        Optional<UserRow> found = users.findById(valid.getUserId());
        if (found.isEmpty() || !found.get().active()) {
            throw ApiException.badRequest(INVALID_LINK);
        }
        UserRow user = found.get();

        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        if (!users.updatePassword(user.id(), encoder.encode(newPassword), now)) {
            throw ApiException.badRequest(INVALID_LINK);
        }
        tokens.invalidateOpen(user.id(), now);

        if (changedNotice) {
            afterCommit(() -> mail.sendPasswordChanged(user.name(), user.email(), now));
        }
    }

    /** Tokens usados ou vencidos há mais de 7 dias (opcional, o volume é pequeno). */
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void cleanUp() {
        tokens.deleteOlderThan(LocalDateTime.now().minusDays(7));
    }

    private PasswordResetToken findValid(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest(INVALID_LINK);
        }
        return tokens.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(sha256Hex(raw.trim()), LocalDateTime.now())
                .orElseThrow(() -> ApiException.badRequest(INVALID_LINK));
    }

    /** 32 bytes aleatórios em Base64 URL-safe, sem padding (43 caracteres). */
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** O e-mail sai fora da transação: só depois do commit. */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}
