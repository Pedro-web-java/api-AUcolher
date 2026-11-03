package com.aucolher.api.payment;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * AES-256-GCM. Chave: MP_TOKEN_ENCRYPTION_KEY (Base64, 32 bytes).
 * IV aleatório de 12 bytes por valor; grava Base64(iv || ciphertext).
 */
@Component
@Converter
public class TokenCryptoConverter implements AttributeConverter<String, String> {

    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final MercadoPagoProperties props;
    private final SecureRandom random = new SecureRandom();
    private volatile SecretKeySpec key;

    public TokenCryptoConverter(MercadoPagoProperties props) {
        this.props = props;
    }

    @Override
    public String convertToDatabaseColumn(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao criptografar a credencial.", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored);
            ByteBuffer buffer = ByteBuffer.wrap(all);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException e) {
            throw new IllegalStateException("Falha ao descriptografar a credencial.", e);
        }
    }

    private SecretKeySpec key() {
        SecretKeySpec k = key;
        if (k == null) {
            String raw = props.tokenEncryptionKey();
            if (raw == null || raw.isBlank()) {
                throw new IllegalStateException("MP_TOKEN_ENCRYPTION_KEY não configurada.");
            }
            byte[] bytes = Base64.getDecoder().decode(raw.trim());
            if (bytes.length != 32) {
                throw new IllegalStateException("MP_TOKEN_ENCRYPTION_KEY deve ter 32 bytes em Base64.");
            }
            k = new SecretKeySpec(bytes, "AES");
            key = k;
        }
        return k;
    }
}
