package com.aucolher.api.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.aucolher.api.common.ApiException;
import com.aucolher.api.common.UserDirectory;

/**
 * Descobre o id da conta logada. Assume que o "name" do Authentication é o e-mail
 * (padrão do UserDetails). Se o seu principal já expõe o id, troque só este método.
 */
@Component
public class CurrentUserProvider {

    private final UserDirectory users;

    public CurrentUserProvider(UserDirectory users) {
        this.users = users;
    }

    public long requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sessão inválida. Entre novamente.");
        }
        return users.findIdByEmail(auth.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sessão inválida. Entre novamente."));
    }
}
