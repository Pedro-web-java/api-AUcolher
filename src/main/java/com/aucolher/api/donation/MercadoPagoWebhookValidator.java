package com.aucolher.api.donation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Valida o header x-signature ("ts=...,v1=...") do webhook: HMAC-SHA256 do manifest
 * "id:{data.id};request-id:{x-request-id};ts:{ts};" com a chave secreta do app.
 */
@Component
public class MercadoPagoWebhookValidator {

    @Value("${app.mercadopago.webhook-secret:}")
    private String secret;

    public boolean isValid(String xSignature, String xRequestId, String dataId) {
        if (secret == null || secret.isBlank() || xSignature == null) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String part : xSignature.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                if (kv[0].equals("ts")) ts = kv[1];
                if (kv[0].equals("v1")) v1 = kv[1];
            }
        }
        if (ts == null || v1 == null) {
            return false;
        }
        StringBuilder manifest = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) manifest.append("id:").append(dataId.toLowerCase()).append(';');
        if (xRequestId != null && !xRequestId.isBlank()) manifest.append("request-id:").append(xRequestId).append(';');
        manifest.append("ts:").append(ts).append(';');
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(manifest.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return MessageDigest.isEqual(hex.toString().getBytes(StandardCharsets.UTF_8),
                    v1.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }
}
