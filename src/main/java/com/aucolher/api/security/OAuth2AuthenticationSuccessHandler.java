package com.aucolher.api.security;

import com.aucolher.api.auth.dto.AuthResponseDTO;
import com.aucolher.api.user.UserService;
import com.aucolher.api.user.dto.UserResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Após o login Google ser concluído, gera um JWT próprio da API
 * (mesmo formato usado pelo login tradicional) e devolve em JSON,
 * mantendo a API stateless mesmo para o fluxo OAuth2.
 */
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtService jwtService;
    // O ObjectMapper do Spring já vem com o módulo de datas do Java 8 — um
    // new ObjectMapper() puro falha ao serializar o dataCriacao (LocalDateTime)
    private final ObjectMapper objectMapper;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        // O CustomOAuth2UserService já criou a conta no primeiro acesso
        UserResponseDTO user = userService.getProfile(email);

        String token = jwtService.generateToken(user.email());

        // Mesmo payload do login tradicional (POST /api/auth/login)
        AuthResponseDTO body = new AuthResponseDTO(token, user);

        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
