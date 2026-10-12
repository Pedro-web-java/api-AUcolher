package com.aucolher.api.user.entity;

/**
 * Origem da autenticação do usuário: cadastro tradicional (LOCAL)
 * ou login social via Google (GOOGLE).
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
