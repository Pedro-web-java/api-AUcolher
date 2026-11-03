package com.aucolher.api.adoption.entity;

/**
 * Lado de quem escreveu a avaliação: ADOPTER avalia o dono do animal, OWNER
 * avalia quem adotou. Cada pedido concluído tem no máximo uma de cada.
 */
public enum ReviewRole {
    ADOPTER,
    OWNER
}
