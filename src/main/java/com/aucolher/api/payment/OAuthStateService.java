package com.aucolher.api.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * "state" do OAuth: um JWT HS256 curto (10 min) com sub = id da ONG e purpose = "mp_oauth".
 * Sem tabela. Assinado com MP_STATE_SECRET (use o mesmo segredo do JWT do projeto).
 */
@Component
public class OAuthStateService {

    public static class InvalidStateException extends RuntimeException {
        public InvalidStateException(String message) {
            super(message);
        }
    }

    static final String PURPOSE = "mp_oauth";
    static final Duration TTL = Duration.ofMinutes(10);

    private final MercadoPagoProperties props;
    private final ObjectMapper mapper;

    public OAuthStateService(MercadoPagoProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public String create(long ngoId) {
        return create(ngoId, Instant.now());
    }

    String create(long ngoId, Instant issuedAt) {
        try {
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", String.valueOf(ngoId));
            claims.put("purpose", PURPOSE);
            claims.put("iat", issuedAt.getEpochSecond());
            claims.put("exp", issuedAt.plus(TTL).getEpochSecond());
            String header = b64("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String payload = b64(mapper.writeValueAsBytes(claims));
            return header + "." + payload + "." + sign(header + "." + payload);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o state do OAuth.", e);
        }
    }

    public long validate(String state) {
        return validate(state, Instant.now());
    }

    long validate(String state, Instant now) {
        if (state == null) {
            throw new InvalidStateException("state ausente");
        }
        String[] parts = state.split("\\.");
        if (parts.length != 3) {
            throw new InvalidStateException("state malformado");
        }
        String expected = sign(parts[0] + "." + parts[1]);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidStateException("assinatura inválida");
        }
        try {
            JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!PURPOSE.equals(claims.path("purpose").asText())) {
                throw new InvalidStateException("purpose inválido");
            }
            if (claims.path("exp").asLong(0) <= now.getEpochSecond()) {
                throw new InvalidStateException("state vencido");
            }
            return Long.parseLong(claims.path("sub").asText());
        } catch (InvalidStateException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidStateException("state ilegível");
        }
    }

    private String sign(String data) {
        String secret = props.stateSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("MP_STATE_SECRET não configurado.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return b64(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
