package com.aucolher.api.donation;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/** Assinatura do webhook do Mercado Pago (api-mercado-pago-ongs §5.2). */
class MercadoPagoWebhookValidatorTest {

    static final String SECRET = "segredo-do-webhook";

    @Test
    void acceptsTheSignatureMadeWithTheSecret() throws Exception {
        MercadoPagoWebhookValidator validator = validator(SECRET);
        String v1 = hmac("id:ord01abc;request-id:req-1;ts:1742505638683;");

        assertThat(validator.isValid("ts=1742505638683,v1=" + v1, "req-1", "ORD01ABC")).isTrue();
    }

    @Test
    void refusesATamperedSignature() throws Exception {
        MercadoPagoWebhookValidator validator = validator(SECRET);
        String v1 = hmac("id:ord01abc;request-id:req-1;ts:1742505638683;");

        assertThat(validator.isValid("ts=1742505638683,v1=" + v1, "req-1", "ORD99")).isFalse();
        assertThat(validator.isValid("ts=1742505638683", "req-1", "ORD01ABC")).isFalse();
        assertThat(validator.isValid(null, "req-1", "ORD01ABC")).isFalse();
    }

    @Test
    void refusesEverythingWithoutASecret() throws Exception {
        String v1 = hmac("id:ord01abc;request-id:req-1;ts:1742505638683;");

        assertThat(validator("").isValid("ts=1742505638683,v1=" + v1, "req-1", "ORD01ABC")).isFalse();
    }

    private static MercadoPagoWebhookValidator validator(String secret) {
        MercadoPagoWebhookValidator validator = new MercadoPagoWebhookValidator();
        ReflectionTestUtils.setField(validator, "secret", secret);
        return validator;
    }

    private static String hmac(String manifest) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
    }
}
