package com.aucolher.api.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Login Google que não terminou (a pessoa cancelou na tela do Google, ou o
 * OAuth2 falhou): volta para o frontend, que mostra o aviso na tela de login.
 * Sem isto o Spring mandaria para /login?error, que não existe na API.
 */
@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationFailureHandler.class);

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        log.info("Login Google não concluído: {}", exception.getMessage());
        response.sendRedirect(frontendUrl + "/login/google?error=failed");
    }
}
