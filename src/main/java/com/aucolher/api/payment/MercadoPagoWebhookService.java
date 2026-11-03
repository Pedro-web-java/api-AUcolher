package com.aucolher.api.payment;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.aucolher.api.common.ApiException;
import com.aucolher.api.common.DonationGateway;
import com.aucolher.api.common.DonationGateway.DonationRef;

@Service
public class MercadoPagoWebhookService {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookService.class);

    private final DonationGateway donations;
    private final MercadoPagoOAuthService oauth;
    private final MercadoPagoClient client;

    public MercadoPagoWebhookService(DonationGateway donations, MercadoPagoOAuthService oauth, MercadoPagoClient client) {
        this.donations = donations;
        this.oauth = oauth;
        this.client = client;
    }

    public void processOrderNotification(String orderId) {
        Optional<DonationRef> found = donations.findByMpOrderId(orderId);
        if (found.isEmpty()) {
            return; // pode ser uma order de outro sistema: responde 200 e ignora
        }
        DonationRef donation = found.get();

        String token;
        try {
            token = oauth.getValidAccessToken(donation.ngoId());
        } catch (ApiException e) {
            log.warn("Webhook: sem token utilizável para a ONG {} (doação {})", donation.ngoId(), donation.id());
            return; // a confirmação fica pelo GET /donations/{id}
        }

        MercadoPagoClient.OrderPaymentState state;
        try {
            state = client.getOrderState(token, orderId);
        } catch (MercadoPagoApiException e) {
            if (e.isAuthFailure()) {
                log.warn("Webhook: token da ONG {} recusado ao consultar a order", donation.ngoId());
                return;
            }
            // erro temporário: responde 5xx para o Mercado Pago repetir a notificação
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Não foi possível consultar a order agora.");
        }

        switch (state) {
            case PAID -> {
                if (donations.approve(donation.id())) {
                    log.info("Doação {} aprovada", donation.id());
                }
            }
            case EXPIRED -> donations.markExpired(donation.id());
            case CANCELLED -> donations.markCancelled(donation.id());
            case WAITING, UNKNOWN -> {
                // continua PENDING
            }
        }
    }
}
