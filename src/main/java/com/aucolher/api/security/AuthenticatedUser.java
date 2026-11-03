package com.aucolher.api.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Date;

/** O usuário relido do banco a cada requisição, com o momento da última troca de senha. */
public class AuthenticatedUser extends User {

    private final LocalDateTime passwordChangedAt;

    public AuthenticatedUser(String email, String password, boolean enabled,
                             Collection<? extends GrantedAuthority> authorities,
                             LocalDateTime passwordChangedAt) {
        super(email, password, enabled, true, true, true, authorities);
        this.passwordChangedAt = passwordChangedAt;
    }

    /**
     * Token emitido antes da troca de senha deixa de valer. Compara em segundos, porque o
     * iat do JWT não tem milissegundos: o login logo depois da troca precisa passar.
     */
    public boolean acceptsTokenIssuedAt(Date issuedAt) {
        return passwordChangedAt == null
                || issuedAt.getTime() / 1000 >= passwordChangedAt.atZone(ZoneId.systemDefault()).toEpochSecond();
    }
}
