package com.aucolher.api.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * POST /api/webhooks/mercadopago?data.id={order}&type=order  (rota pública).
 * 1) valida a assinatura; 2) localiza a doação; 3) consulta a order com o token da ONG;
 * 4) atualiza a doação de forma atômica; 5) responde 200 rápido.
 *
 * DESATIVADO (sem @RestController): a rota é atendida por donation.MercadoPagoWebhookController
 * enquanto o PIX usa o token provisório. Ao migrar para o token de cada ONG, reative este e
 * remova o outro: os dois juntos impedem a API de subir.
 */
public class MercadoPagoWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoWebhookController.class);

    private final MercadoPagoProperties props;
    private final MercadoPagoWebhookService service;

    public MercadoPagoWebhookController(MercadoPagoProperties props, MercadoPagoWebhookService service) {
        this.props = props;
        this.service = service;
    }

    @PostMapping("/api/webhooks/mercadopago")
    public ResponseEntity<Void> receive(
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(name = "type", required = false) String type,
            @RequestHeader(name = "x-signature", required = false) String signature,
            @RequestHeader(name = "x-request-id", required = false) String requestId) {

        if (props.webhookSecret() == null || props.webhookSecret().isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (!MercadoPagoSignatureVerifier.isValid(props.webhookSecret(), signature, requestId, dataId)) {
            log.warn("Webhook do Mercado Pago com assinatura inválida");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (type != null && !"order".equals(type)) {
            return ResponseEntity.ok().build();
        }
        service.processOrderNotification(dataId);
        return ResponseEntity.ok().build();
    }
}
