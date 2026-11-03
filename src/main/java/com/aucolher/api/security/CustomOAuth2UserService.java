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
    private final GooglePhotoDownloader googlePhoto;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");

        if (email == null) {
            throw new OAuth2AuthenticationException("Não foi possível obter o e-mail da conta Google");
        }

        syncAccount(email, name, picture);
        return oAuth2User;
    }

    /**
     * Primeiro acesso: cria a conta com uma cópia da foto do Google. Conta que
     * já existe mantém a foto dela; só recebe a do Google quando ainda guarda o
     * link do Google (contas criadas antes da cópia) ou quando é de pessoa e
     * está sem foto.
     */
    void syncAccount(String email, String name, String picture) {
        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null) {
            userRepository.save(User.builder()
                    .name(name != null ? name : email)
                    .email(email)
                    .password(null)
                    .userType(UserType.PERSON)
                    .provider(AuthProvider.GOOGLE)
                    .active(true)
                    .photoUrl(copyOrLink(picture))
                    .build());
        } else if (GooglePhotoDownloader.isGoogleHosted(user.getPhotoUrl())) {
            // Se o download falhar, o link fica e a troca é tentada no próximo login
            String copy = googlePhoto.download(picture);
            if (copy != null) {
                user.setPhotoUrl(copy);
                userRepository.save(user);
            }
        } else if (user.getPhotoUrl() == null && user.getUserType() == UserType.PERSON) {
            user.setPhotoUrl(copyOrLink(picture));
            userRepository.save(user);
        }
    }

    /** Sem a cópia, vale o link do Google, como antes: melhor que ficar sem foto. */
    private String copyOrLink(String picture) {
        String copy = googlePhoto.download(picture);
        return copy != null ? copy : picture;
    }
}
