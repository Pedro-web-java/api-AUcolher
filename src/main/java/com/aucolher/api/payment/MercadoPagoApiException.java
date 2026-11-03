package com.aucolher.api.payment;

/** Falha ao falar com o Mercado Pago. httpStatus 0 = erro de rede. Nunca carrega token. */
public class MercadoPagoApiException extends RuntimeException {

    private final int httpStatus;

    public MercadoPagoApiException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /** invalid_grant (400) ou 401: o acesso foi revogado ou o refresh_token não vale mais. */
    public boolean isAuthFailure() {
        return httpStatus == 400 || httpStatus == 401;
    }
}
