package com.aucolher.api.campaign;

import com.aucolher.api.shared.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * PROVISÓRIO, até a migração 009 existir: todas as ONGs usam um único token de teste
 * (app.mercadopago.dev-access-token). Apague esta classe quando a 009 chegar e faça o
 * service dela implementar MercadoPagoAccountGateway.
 */
@Component
public class PlaceholderMercadoPagoAccountGateway implements MercadoPagoAccountGateway {

    @Value("${app.mercadopago.dev-access-token:}")
    private String devToken;

    @Override
    public boolean isConnected(Long ngoId) {
        return true;
    }

    @Override
    public String accessToken(Long ngoId) {
        if (devToken == null || devToken.isBlank()) {
            throw new BusinessException("Esta ONG não está recebendo doações no momento.");
        }
        return devToken;
    }
}
