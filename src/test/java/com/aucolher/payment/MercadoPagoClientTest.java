package com.aucolher.payment;

import static com.aucolher.api.payment.MercadoPagoClient.OrderPaymentState.CANCELLED;
import static com.aucolher.api.payment.MercadoPagoClient.OrderPaymentState.EXPIRED;
import static com.aucolher.api.payment.MercadoPagoClient.OrderPaymentState.PAID;
import static com.aucolher.api.payment.MercadoPagoClient.OrderPaymentState.UNKNOWN;
import static com.aucolher.api.payment.MercadoPagoClient.OrderPaymentState.WAITING;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aucolher.api.payment.MercadoPagoClient;
import org.junit.jupiter.api.Test;

class MercadoPagoClientTest {

    @Test
    void mapsOrderStates() {
        assertEquals(PAID, MercadoPagoClient.mapOrderState("processed", "accredited"));
        assertEquals(WAITING, MercadoPagoClient.mapOrderState("action_required", "waiting_transfer"));
        assertEquals(EXPIRED, MercadoPagoClient.mapOrderState("expired", null));
        assertEquals(CANCELLED, MercadoPagoClient.mapOrderState("canceled", null));
        assertEquals(UNKNOWN, MercadoPagoClient.mapOrderState(null, null));
    }
}
