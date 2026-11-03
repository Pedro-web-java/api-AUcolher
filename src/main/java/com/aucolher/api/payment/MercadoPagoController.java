package com.aucolher.api.payment;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aucolher.api.common.security.CurrentUserProvider;
import com.aucolher.api.payment.dto.ConnectResponseDTO;
import com.aucolher.api.payment.dto.MercadoPagoAccountDTO;

@RestController
public class MercadoPagoController {

    private final MercadoPagoOAuthService service;
    private final CurrentUserProvider currentUser;
    private final String frontendUrl;

    public MercadoPagoController(MercadoPagoOAuthService service,
                                 CurrentUserProvider currentUser,
                                 @Value("${app.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.service = service;
        this.currentUser = currentUser;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    /** Também serve para reconectar. */
    @PostMapping("/api/ngo/mercado-pago/connect")
    public ConnectResponseDTO connect() {
        return new ConnectResponseDTO(service.startConnection(currentUser.requireUserId()));
    }

    @GetMapping("/api/ngo/mercado-pago")
    public MercadoPagoAccountDTO account() {
        return service.getAccount(currentUser.requireUserId());
    }

    @DeleteMapping("/api/ngo/mercado-pago")
    public ResponseEntity<Void> disconnect() {
        service.disconnect(currentUser.requireUserId());
        return ResponseEntity.noContent().build();
    }

    /** Público: quem chama é o Mercado Pago (navegador da ONG). Sempre responde 302 para o front. */
    @GetMapping("/api/mercado-pago/oauth/callback")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error) {
        MercadoPagoOAuthService.CallbackResult result;
        try {
            result = service.handleCallback(code, state, error);
        } catch (RuntimeException e) {
            result = MercadoPagoOAuthService.CallbackResult.ERROR;
        }
        URI target = URI.create(frontendUrl + "/ong/painel/campanhas?mp=" + result.param());
        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
    }
}
