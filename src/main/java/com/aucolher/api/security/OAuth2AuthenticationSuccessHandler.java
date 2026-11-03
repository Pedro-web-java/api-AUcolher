package com.aucolher.api.security;

import com.aucolher.api.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Após o login Google, volta para o frontend em /login/google. O fluxo OAuth2
 * termina numa página da API, então o token não vai direto: vai um código de
 * uso único (GoogleLoginCodeStore), que o frontend troca pelo mesmo payload
 * do login tradicional em POST /api/auth/google/exchange.
 */
@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final GoogleLoginCodeStore googleLoginCodeStore;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        // Conta banida pela moderação não recebe código, como no login tradicional.
        // O CustomOAuth2UserService já criou a conta no primeiro acesso
        boolean suspended = userRepository.findByEmail(email).map(found -> !Boolean.TRUE.equals(found.getActive())).orElse(false);
        if (suspended) {
            response.sendRedirect(frontendUrl + "/login/google?error=suspended");
            return;
        }

        response.sendRedirect(frontendUrl + "/login/google?code=" + googleLoginCodeStore.issue(email));
    }
}
