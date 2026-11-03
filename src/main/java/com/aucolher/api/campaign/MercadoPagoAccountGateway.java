package com.aucolher.api.campaign;

/** Ponte para a conta Mercado Pago da ONG (migração 009). */
public interface MercadoPagoAccountGateway {

    /** true se a conta da ONG está CONNECTED. */
    boolean isConnected(Long ngoId);

    /** Access token da ONG (já descriptografado). Nunca sai do backend. */
    String accessToken(Long ngoId);
}
