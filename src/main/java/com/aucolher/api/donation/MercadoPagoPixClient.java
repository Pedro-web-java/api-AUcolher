package com.aucolher.api.donation;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/** Orders API do Mercado Pago (PIX). Sempre chamada com o token da ONG dona da campanha. */
@Slf4j
@Component
public class MercadoPagoPixClient {

    public record PixOrder(String orderId, String qrCode, String qrCodeBase64, String ticketUrl) {
    }

    public record OrderState(boolean paid, String status, String statusDetail) {
    }

    private final RestClient http;
    private final boolean sandbox;

    public MercadoPagoPixClient(
            @Value("${app.mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
            @Value("${app.mercadopago.sandbox:false}") boolean sandbox) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.sandbox = sandbox;
    }

    public PixOrder createPixOrder(String token, Long donationId, BigDecimal amount,
                                   String donorEmail, String donorName) {
        String value = amount.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        // No sandbox, first_name "APRO" aprova o PIX sozinho e o e-mail precisa ser o de teste.
        String email = sandbox ? "test@testuser.com" : donorEmail;
        String firstName = sandbox ? "APRO" : firstName(donorName);

        Map<String, Object> body = Map.of(
                "type", "online",
                "total_amount", value,
                "external_reference", String.valueOf(donationId),
                "processing_mode", "automatic",
                "payer", Map.of("email", email, "first_name", firstName),
                "transactions", Map.of("payments", List.of(Map.of(
                        "amount", value,
                        "payment_method", Map.of("id", "pix", "type", "bank_transfer"),
                        "expiration_time", "PT30M"))));
        try {
            JsonNode res = http.post().uri("/v1/orders")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Idempotency-Key", String.valueOf(donationId))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (res == null || res.path("id").isMissingNode()) {
                throw new MercadoPagoException("Resposta vazia do Mercado Pago ao criar a order.");
            }
            JsonNode pm = res.path("transactions").path("payments").path(0).path("payment_method");
            String qr = pm.path("qr_code").asText(null);
            if (qr == null || qr.isBlank()) {
                throw new MercadoPagoException("O Mercado Pago não devolveu o código PIX.");
            }
            return new PixOrder(res.path("id").asText(), qr,
                    blankToNull(pm.path("qr_code_base64").asText(null)),
                    blankToNull(pm.path("ticket_url").asText(null)));
        } catch (RestClientResponseException e) {
            log.error("Mercado Pago recusou a order ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new MercadoPagoException("Mercado Pago recusou a criação do PIX.", e);
        } catch (RestClientException e) {
            log.error("Falha ao falar com o Mercado Pago", e);
            throw new MercadoPagoException("Falha ao falar com o Mercado Pago.", e);
        }
    }

    public OrderState getOrder(String token, String orderId) {
        try {
            JsonNode res = http.get().uri("/v1/orders/{id}", orderId)
                    .header("Authorization", "Bearer " + token)
                    .retrieve()
                    .body(JsonNode.class);
            if (res == null) {
                throw new MercadoPagoException("Resposta vazia do Mercado Pago ao consultar a order.");
            }
            String status = res.path("status").asText("");
            String detail = res.path("status_detail").asText("");
            // Pago = order "processed" (status_detail "accredited"). Confirme no sandbox com APRO.
            boolean paid = "processed".equals(status) || "accredited".equals(detail);
            return new OrderState(paid, status, detail);
        } catch (RestClientResponseException e) {
            log.error("Mercado Pago recusou a consulta ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new MercadoPagoException("Não foi possível consultar a order.", e);
        } catch (RestClientException e) {
            log.error("Falha ao consultar o Mercado Pago", e);
            throw new MercadoPagoException("Falha ao falar com o Mercado Pago.", e);
        }
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) {
            return "Doador";
        }
        return name.trim().split("\\s+")[0];
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
