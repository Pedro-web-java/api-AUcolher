package com.aucolher.api.user.entity;

/**
 * Perfis de conta do AUcolher. Só PERSON e NGO têm cadastro público;
 * ADMIN modera a plataforma e não se cadastra pelo site.
 */
public enum UserType {
    NGO,
    PERSON,
    ADMIN
}
