package com.aucolher.payment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aucolher.api.payment.MercadoPagoSignatureVerifier;
import org.junit.jupiter.api.Test;

class MercadoPagoSignatureVerifierTest {

    @Test
    void validSignatureIsAccepted() {
        String v1 = MercadoPagoSignatureVerifier.computeSignature("segredo", "ORD123", "req-1", "1742505638683");
        String header = "ts=1742505638683,v1=" + v1;
        assertTrue(MercadoPagoSignatureVerifier.isValid("segredo", header, "req-1", "ORD123"));
    }

    @Test
    void wrongSecretOrIdIsRejected() {
        String v1 = MercadoPagoSignatureVerifier.computeSignature("segredo", "ORD123", "req-1", "1");
        String header = "ts=1,v1=" + v1;
        assertFalse(MercadoPagoSignatureVerifier.isValid("outro", header, "req-1", "ORD123"));
        assertFalse(MercadoPagoSignatureVerifier.isValid("segredo", header, "req-1", "ORD999"));
        assertFalse(MercadoPagoSignatureVerifier.isValid("segredo", null, "req-1", "ORD123"));
    }
}
