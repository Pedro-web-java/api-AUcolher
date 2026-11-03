package com.aucolher.api.donation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequiredArgsConstructor
public class MercadoPagoWebhookController {

    private final MercadoPagoWebhookValidator validator;
    private final DonationService donationService;
    private final ObjectMapper mapper;

    @PostMapping("/api/webhooks/mercadopago")
    public ResponseEntity<Void> receive(
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId,
            @RequestParam(value = "data.id", required = false) String dataIdParam,
            @RequestBody(required = false) String body) {

        String dataId = dataIdParam;
        if ((dataId == null || dataId.isBlank()) && body != null && !body.isBlank()) {
            try {
                JsonNode json = mapper.readTree(body);
                dataId = json.path("data").path("id").asText(null);
            } catch (Exception e) {
                log.warn("Webhook com corpo ilegível");
            }
        }
        if (!validator.isValid(signature, requestId, dataId)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (dataId != null && !dataId.isBlank()) {
            donationService.onWebhook(dataId);   // order desconhecida = ignora; erro do MP = 500 (o MP reenvia)
        }
        return ResponseEntity.ok().build();
    }
}
