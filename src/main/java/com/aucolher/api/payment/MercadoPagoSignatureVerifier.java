package com.aucolher.api.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Valida o x-signature do webhook: HMAC-SHA256 de "id:{data.id};request-id:{x-request-id};ts:{ts};". */
public final class MercadoPagoSignatureVerifier {

    private MercadoPagoSignatureVerifier() {
    }

    public static boolean isValid(String secret, String signatureHeader, String requestId, String dataId) {
        if (isBlank(secret) || isBlank(signatureHeader) || isBlank(requestId) || isBlank(dataId)) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2) {
                if (kv[0].equals("ts")) {
                    ts = kv[1];
                } else if (kv[0].equals("v1")) {
                    v1 = kv[1];
                }
            }
        }
        if (ts == null || v1 == null) {
            return false;
        }
        String expected = computeSignature(secret, dataId, requestId, ts);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
    }

    public static String computeSignature(String secret, String dataId, String requestId, String ts) {
        String id = dataId.matches("[A-Za-z0-9]+") ? dataId.toLowerCase(Locale.ROOT) : dataId;
        String manifest = "id:" + id + ";request-id:" + requestId + ";ts:" + ts + ";";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
