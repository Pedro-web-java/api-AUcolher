package com.aucolher.api.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mercadopago")
public record MercadoPagoProperties(
        String clientId,
        String clientSecret,
        String redirectUri,
        String webhookSecret,
        String tokenEncryptionKey,
        boolean testToken,
        String stateSecret) {

    /** Sem estes valores a API sobe, mas a conexão responde 503. */
    public boolean oauthConfigured() {
        return notBlank(clientId) && notBlank(clientSecret) && notBlank(redirectUri)
                && notBlank(tokenEncryptionKey) && notBlank(stateSecret);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
