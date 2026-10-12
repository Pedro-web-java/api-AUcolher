package com.aucolher.api.security;

import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.AuthProvider;
import com.aucolher.api.user.entity.UserType;
import com.aucolher.api.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Executado a cada login OAuth2 (Google). Se for o primeiro acesso do
 * e-mail, cria automaticamente um registro em `users` com perfil
 * PERSON, como exigido pelo requisito 3.3.
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");

        if (email == null) {
            throw new OAuth2AuthenticationException("Não foi possível obter o e-mail da conta Google");
        }

        userRepository.findByEmail(email).orElseGet(() -> {
            User newUser = User.builder()
                    .name(name != null ? name : email)
                    .email(email)
                    .password(null)
                    .userType(UserType.PERSON)
                    .provider(AuthProvider.GOOGLE)
                    .active(true)
                    .build();
            return userRepository.save(newUser);
        });

        return oAuth2User;
    }
}
