package com.aucolher.api.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Chamadas ao Mercado Pago. Nunca loga token, code nem client_secret.
 * Os caminhos dos campos da Orders API e os nomes dos status devem ser conferidos no sandbox
 * (seção 6 do contrato).
 */
@Component
public class MercadoPagoClient {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClient.class);

    public enum OrderPaymentState { PAID, WAITING, EXPIRED, CANCELLED, UNKNOWN }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken,
            @JsonProperty("expires_in") Long expiresIn,
            @JsonProperty("user_id") Long userId,
            @JsonProperty("live_mode") Boolean liveMode) {
    }

    public record PixOrder(String orderId, String qrCode, String qrCodeBase64, String ticketUrl) {
    }

    private final RestClient rest;
    private final MercadoPagoProperties props;

    public MercadoPagoClient(MercadoPagoProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        this.rest = RestClient.builder().baseUrl("https://api.mercadopago.com").requestFactory(factory).build();
    }

    /** Troca o code pelos tokens (redirect_uri idêntica à do início da conexão). */
    public TokenResponse exchangeCode(String code) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("client_id", props.clientId());
        body.put("client_secret", props.clientSecret());
        body.put("grant_type", "authorization_code");
        body.put("code", code);
        body.put("redirect_uri", props.redirectUri());
        body.put("test_token", props.testToken());
        return requestToken(body);
    }

    /** O refresh_token muda a cada renovação: quem chama tem de salvar o novo. */
    public TokenResponse refresh(String refreshToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("grant_type", "refresh_token");
        body.put("client_id", props.clientId());
        body.put("client_secret", props.clientSecret());
        body.put("refresh_token", refreshToken);
        return requestToken(body);
    }

    private TokenResponse requestToken(Map<String, Object> body) {
        try {
            TokenResponse response = rest.post().uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(TokenResponse.class);
            if (response == null || response.accessToken() == null) {
                throw new MercadoPagoApiException(502, "Resposta de token vazia.");
            }
            return response;
        } catch (RestClientResponseException e) {
            log.warn("Mercado Pago /oauth/token respondeu {}", e.getStatusCode().value());
            throw new MercadoPagoApiException(e.getStatusCode().value(), "Mercado Pago recusou a requisição de token.");
        } catch (RestClientException e) {
            log.warn("Falha de rede em /oauth/token: {}", e.getClass().getSimpleName());
            throw new MercadoPagoApiException(0, "Falha ao falar com o Mercado Pago.");
        }
    }

    /** Cria a order PIX com o token da ONG. Sem marketplace_fee: a plataforma não cobra comissão. */
    public PixOrder createPixOrder(String accessToken, String idempotencyKey, String externalReference,
                                   BigDecimal amount, String payerEmail) {
        String value = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        Map<String, Object> payment = Map.of(
                "amount", value,
                "payment_method", Map.of("id", "pix", "type", "bank_transfer"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "online");
        body.put("processing_mode", "automatic");
        body.put("total_amount", value);
        body.put("external_reference", externalReference);
        body.put("payer", Map.of("email", payerEmail));
        body.put("transactions", Map.of("payments", List.of(payment)));
        try {
            JsonNode root = rest.post().uri("/v1/orders")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header("X-Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null || text(root.path("id")) == null) {
                throw new MercadoPagoApiException(502, "Resposta da order vazia.");
            }
            JsonNode method = root.path("transactions").path("payments").path(0).path("payment_method");
            return new PixOrder(
                    text(root.path("id")),
                    text(method.path("qr_code")),
                    text(method.path("qr_code_base64")),
                    text(method.path("ticket_url")));
        } catch (RestClientResponseException e) {
            log.warn("Mercado Pago POST /v1/orders respondeu {}", e.getStatusCode().value());
            throw new MercadoPagoApiException(e.getStatusCode().value(), "Mercado Pago recusou a criação da order.");
        } catch (RestClientException e) {
            log.warn("Falha de rede em POST /v1/orders: {}", e.getClass().getSimpleName());
            throw new MercadoPagoApiException(0, "Falha ao falar com o Mercado Pago.");
        }
    }

    public OrderPaymentState getOrderState(String accessToken, String orderId) {
        try {
            JsonNode root = rest.get().uri("/v1/orders/{id}", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            if (root == null) {
                return OrderPaymentState.UNKNOWN;
            }
            String status = text(root.path("status"));
            String detail = text(root.path("status_detail"));
            if (status == null) {
                JsonNode payment = root.path("transactions").path("payments").path(0);
                status = text(payment.path("status"));
                detail = text(payment.path("status_detail"));
            }
            return mapOrderState(status, detail);
        } catch (RestClientResponseException e) {
            log.warn("Mercado Pago GET /v1/orders respondeu {}", e.getStatusCode().value());
            throw new MercadoPagoApiException(e.getStatusCode().value(), "Mercado Pago recusou a consulta da order.");
        } catch (RestClientException e) {
            log.warn("Falha de rede em GET /v1/orders: {}", e.getClass().getSimpleName());
            throw new MercadoPagoApiException(0, "Falha ao falar com o Mercado Pago.");
        }
    }

    /** processed/accredited = paga; action_required/waiting_transfer = aguardando; expired; canceled. */
    public static OrderPaymentState mapOrderState(String status, String statusDetail) {
        String s = status == null ? "" : status.toLowerCase(Locale.ROOT);
        String d = statusDetail == null ? "" : statusDetail.toLowerCase(Locale.ROOT);
        if (s.equals("processed") || d.equals("accredited")) {
            return OrderPaymentState.PAID;
        }
        if (s.equals("expired") || d.equals("expired")) {
            return OrderPaymentState.EXPIRED;
        }
        if (s.equals("canceled") || s.equals("cancelled") || d.equals("canceled") || d.equals("cancelled")) {
            return OrderPaymentState.CANCELLED;
        }
        if (s.equals("action_required") || s.equals("created") || d.equals("waiting_transfer")
                || d.equals("waiting_payment")) {
            return OrderPaymentState.WAITING;
        }
        return OrderPaymentState.UNKNOWN;
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }
}
